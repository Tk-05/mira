package com.mira.resolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.lexer.MultipleLexerErrors;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.utils.FileLoader;
import com.mira.utils.ModuleResolver;
import com.mira.warning.Warning;
import com.mira.warning.WarningCollector;

public final class ModuleChecker {

    /**
     * tokenizeNanos/parseNanos are captured at the one real, load-bearing
     * tokenize+parse for this module (not a throwaway remeasurement for display
     * purposes) - by the time any consumer re-tokenizes/re-parses the same source
     * again later in the run, the JIT has already warmed up on this exact code path
     * and the numbers stop being representative.
     */
    public record ParsedModule(Path path, List<Node> ast, String source, int tokenCount, long tokenizeNanos,
            long parseNanos) {

    }

    /**
     * hadErrors/checkTimingsMs come from the actual per-module static-check pass;
     * modules is the same ParsedModule set that pass already parsed, exposed so
     * callers (e.g. --stats) can read real tokenize/parse timing and size info off
     * it instead of re-parsing every module a second time. warningCount is tallied
     * from each module's own {@link ModuleResult} after all per-module checks (each
     * running on its own thread, see {@link #checkModule}) complete - warnings are
     * collected and formatted on the checking thread itself, then printed by the
     * caller in deterministic module order once every check is done, not
     * interleaved live as each module finishes.
     *
     * discoveryWallMs/checkWallMs are the REAL elapsed wall-clock time for the
     * whole parallel discovery/check phase respectively - unlike summing each
     * module's own timingMs (checkTimingsMs' values), which now overlap in time
     * since modules run concurrently, these two numbers are what a caller should
     * show to demonstrate the actual speedup from parallelizing.
     */
    public record ModuleCheckResult(boolean hadErrors, Map<Path, Long> checkTimingsMs, Map<Path, ParsedModule> modules,
            int warningCount, long discoveryWallMs, long checkWallMs) {

    }

    /**
     * Shared, JVM-lifetime virtual-thread executor for module discovery - mirrors
     * {@code ImportResolver.MODULE_EXECUTOR}'s pattern/lifetime. Virtual threads
     * suit this workload (I/O-bound file reads mixed with CPU-bound tokenize/parse,
     * unpredictable recursion depth from nested imports) far better than a bounded
     * platform-thread pool, which could deadlock on a deep/shared import graph.
     */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private ModuleChecker() {
    }

    /**
     * The entry file plus every module it imports, transitively (deduped, no
     * stdlib/native imports).
     */
    public static Map<Path, ParsedModule> collectAllModules(List<Node> rootAst, Path rootPath) {
        return parallelCollectAllModules(rootAst, rootPath, Set.of(), Collections.synchronizedList(new ArrayList<>()));
    }

    public static ModuleCheckResult check(List<Node> rootAst, Set<Path> visited) {
        List<String> syntaxErrors = Collections.synchronizedList(new ArrayList<>());
        Path rootPath = Flags.inputPath.get();
        long discoveryStart = System.nanoTime();
        Map<Path, ParsedModule> allModules = parallelCollectAllModules(rootAst, rootPath, visited, syntaxErrors);
        long discoveryWallMs = (System.nanoTime() - discoveryStart) / 1_000_000;
        syntaxErrors.forEach(System.err::println);

        Map<Path, Set<String>> externalCallsByTarget = ModuleResolver.collectExternalCallsByTarget(rootAst, rootPath,
                allModules);
        long checkStart = System.nanoTime();
        List<CompletableFuture<ModuleResult>> checks = new ArrayList<>();
        for (ParsedModule module : allModules.values()) {
            Set<String> externalCalls = externalCallsByTarget.getOrDefault(module.path(), Set.of());
            checks.add(CompletableFuture.supplyAsync(() -> checkModule(module, externalCalls), EXECUTOR));
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).join();
        long checkWallMs = (System.nanoTime() - checkStart) / 1_000_000;

        boolean hadErrors = !syntaxErrors.isEmpty();
        int warningCount = 0;
        List<String> pendingErrors = new ArrayList<>();
        Map<Path, Long> timingsMs = new LinkedHashMap<>();
        for (CompletableFuture<ModuleResult> future : checks) {
            ModuleResult result = future.join();
            timingsMs.put(result.path(), result.timingMs());
            warningCount += result.warningCount();
            result.warningLines().forEach(System.err::println);
            if (result.hadErrors()) {
                hadErrors = true;
                pendingErrors.addAll(result.errorLines());
            }
        }
        pendingErrors.forEach(msg -> System.err.println(msg));
        return new ModuleCheckResult(hadErrors, timingsMs, allModules, warningCount, discoveryWallMs, checkWallMs);
    }

    private record ModuleResult(Path path, boolean hadErrors, List<String> errorLines, List<String> warningLines,
            int warningCount, long timingMs) {

    }

    private static ModuleResult checkModule(ParsedModule module, Set<String> externalCalls) {
        long moduleStart = System.nanoTime();
        Flags.inputPath.set(module.path());
        Flags.fileName.set(module.path().getFileName().toString());
        Flags.sourceLines.set(module.source().split("\n", -1));

        List<String> errorLines = List.of();
        boolean hadErrors = false;
        try {
            new StaticCheck(externalCalls).check(module.ast());
        } catch (MultipleStaticCheckErrors mse) {
            errorLines = mse.getErrors().stream().map(DiagnosticFormatter::format).toList();
            hadErrors = true;
        } catch (Exception ignored) {
        }

        List<Warning> rawWarnings = WarningCollector.getWarnings();
        int warningCount = rawWarnings.size();
        List<String> warningLines = Flags.suppressWarnings
                ? List.of()
                : rawWarnings.stream().map(Warning::format).toList();
        WarningCollector.clear();

        long timingMs = (System.nanoTime() - moduleStart) / 1_000_000;
        return new ModuleResult(module.path(), hadErrors, errorLines, warningLines, warningCount, timingMs);
    }

    public static Map<Path, List<Path>> collectDependencyGraph(List<Node> rootAst, Path rootPath) {
        Map<Path, List<Path>> graph = new LinkedHashMap<>();
        collectDeps(rootAst, rootPath, graph, new LinkedHashSet<>());
        return graph;
    }

    private static void collectDeps(List<Node> ast, Path parentPath, Map<Path, List<Path>> graph, Set<Path> visited) {
        List<Path> directImports = new ArrayList<>();
        for (Node node : ast) {
            if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                continue;
            }
            Path modulePath = ModuleResolver.resolveModulePath(imp.getModule(), parentPath);
            directImports.add(modulePath);
            if (!visited.add(modulePath)) {
                continue;
            }
            try {
                String source = FileLoader.readFileFromPath(modulePath.toString());
                List<Node> moduleAst = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
                collectDeps(moduleAst, modulePath, graph, visited);
            } catch (Exception ignored) {
            }
        }
        graph.put(parentPath, directImports);
    }

    /**
     * Parallel replacement for the old single-threaded recursive DFS: discovers and
     * parses the full transitive module graph concurrently on virtual threads,
     * deduped via {@code claimed}. The returned map is sorted by path string rather
     * than by discovery/insertion order, since parallel discovery completes in a
     * different order every run - callers (error/warning printing, --stats) need
     * output that stays byte-identical across runs.
     */
    private static Map<Path, ParsedModule> parallelCollectAllModules(List<Node> rootAst, Path rootPath,
            Set<Path> preVisited, List<String> syntaxErrors) {
        Set<Path> claimed = ConcurrentHashMap.newKeySet();
        claimed.addAll(preVisited);
        Map<Path, ParsedModule> out = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> roots = new ArrayList<>();
        for (Node node : rootAst) {
            if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                continue;
            }
            Path modulePath = ModuleResolver.resolveModulePath(imp.getModule(), rootPath);
            roots.add(discoverModule(modulePath, claimed, out, syntaxErrors));
        }
        CompletableFuture.allOf(roots.toArray(CompletableFuture[]::new)).join();

        Map<Path, ParsedModule> sorted = new TreeMap<>(Comparator.comparing(Path::toString));
        sorted.putAll(out);
        return sorted;
    }

    private static CompletableFuture<Void> discoverModule(Path modulePath, Set<Path> claimed,
            Map<Path, ParsedModule> out, List<String> syntaxErrors) {
        if (!claimed.add(modulePath)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> parseModule(modulePath, syntaxErrors), EXECUTOR)
                .thenCompose(parsed -> {
                    if (parsed == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    out.put(modulePath, parsed);
                    List<CompletableFuture<Void>> children = new ArrayList<>();
                    for (Node node : parsed.ast()) {
                        if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                            continue;
                        }
                        Path childPath = ModuleResolver.resolveModulePath(imp.getModule(), modulePath);
                        children.add(discoverModule(childPath, claimed, out, syntaxErrors));
                    }
                    return CompletableFuture.allOf(children.toArray(CompletableFuture[]::new));
                });
    }

    private static ParsedModule parseModule(Path modulePath, List<String> syntaxErrors) {
        String source;
        try {
            source = FileLoader.readFileFromPath(modulePath.toString());
        } catch (Exception ignored) {
            return null;
        }
        Flags.fileName.set(modulePath.getFileName().toString());
        Flags.sourceLines.set(source.split("\n", -1));
        try {
            long tokenizeStart = System.nanoTime();
            List<Token> moduleTokens = new Tokenizer().tokenize(source, false);
            long tokenizeNanos = System.nanoTime() - tokenizeStart;
            long parseStart = System.nanoTime();
            List<Node> moduleAst = new Parser().parseTokens(moduleTokens);
            long parseNanos = System.nanoTime() - parseStart;
            return new ParsedModule(modulePath, moduleAst, source, moduleTokens.size(), tokenizeNanos, parseNanos);
        } catch (MultipleLexerErrors mle) {
            mle.getErrors().stream().map(DiagnosticFormatter::format).forEach(syntaxErrors::add);
            return null;
        } catch (MultipleParserErrors mpe) {
            mpe.getErrors().stream().map(DiagnosticFormatter::format).forEach(syntaxErrors::add);
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
