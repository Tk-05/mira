package com.mira.resolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.utils.FileLoader;
import com.mira.utils.ModuleResolver;
import com.mira.warning.WarningCollector;

public final class ModuleChecker {

    /**
     * tokenizeNanos/parseNanos are captured at the one real, load-bearing
     * tokenize+parse for this module (not a throwaway remeasurement for display
     * purposes) - by the time any consumer re-tokenizes/re-parses the same
     * source again later in the run, the JIT has already warmed up on this
     * exact code path and the numbers stop being representative.
     */
    public record ParsedModule(Path path, List<Node> ast, String source,
            int tokenCount, long tokenizeNanos, long parseNanos) {

    }

    /**
     * hadErrors/checkTimingsMs come from the actual per-module static-check
     * pass; modules is the same ParsedModule set that pass already parsed,
     * exposed so callers (e.g. --stats) can read real tokenize/parse timing and
     * size info off it instead of re-parsing every module a second time.
     * warningCount is tallied module-by-module as each one is flushed - this
     * loop already calls WarningCollector.flush() per module (so warnings print
     * as each module is checked, not batched to the end), which drains the
     * collector; a caller reading WarningCollector.getWarnings().size() only
     * after this method returns would always see 0.
     */
    public record ModuleCheckResult(boolean hadErrors, Map<Path, Long> checkTimingsMs,
            Map<Path, ParsedModule> modules, int warningCount) {

    }

    private ModuleChecker() {
    }

    /**
     * The entry file plus every module it imports, transitively (deduped, no
     * stdlib/native imports).
     */
    public static Map<Path, ParsedModule> collectAllModules(List<Node> rootAst, Path rootPath) {
        Map<Path, ParsedModule> allModules = new LinkedHashMap<>();
        collectAllModules(rootAst, rootPath, allModules, new LinkedHashSet<>());
        return allModules;
    }

    public static ModuleCheckResult check(List<Node> rootAst, Set<Path> visited) {
        Map<Path, ParsedModule> allModules = new LinkedHashMap<>();
        collectAllModules(rootAst, Flags.inputPath.get(), allModules, new LinkedHashSet<>(visited));

        Path rootPath = Flags.inputPath.get();
        String savedFileName = Flags.fileName;
        String[] savedSourceLines = Flags.sourceLines;

        boolean hadErrors = false;
        int warningCount = 0;
        List<String> pendingErrors = new ArrayList<>();
        Map<Path, Long> timingsMs = new LinkedHashMap<>();
        for (ParsedModule module : allModules.values()) {
            Set<String> externalCalls = new LinkedHashSet<>();
            ModuleResolver.collectExternalCalls(rootAst, rootPath, module.path(), externalCalls);
            for (ParsedModule caller : allModules.values()) {
                if (!caller.path().equals(module.path())) {
                    ModuleResolver.collectExternalCalls(caller.ast(), caller.path(), module.path(), externalCalls);
                }
            }
            long moduleStart = System.nanoTime();
            try {
                Flags.inputPath.set(module.path());
                Flags.fileName = module.path().getFileName().toString();
                Flags.sourceLines = module.source().split("\n", -1);
                new StaticCheck(externalCalls).check(module.ast());
                warningCount += WarningCollector.getWarnings().size();
                WarningCollector.flush();
            } catch (MultipleStaticCheckErrors mse) {
                warningCount += WarningCollector.getWarnings().size();
                WarningCollector.flush();
                mse.getErrors().stream()
                        .map(DiagnosticFormatter::format)
                        .forEach(pendingErrors::add);
                hadErrors = true;
            } catch (Exception ignored) {
            } finally {
                timingsMs.put(module.path(), (System.nanoTime() - moduleStart) / 1_000_000);
                Flags.inputPath.set(rootPath);
                Flags.fileName = savedFileName;
                Flags.sourceLines = savedSourceLines;
            }
        }
        pendingErrors.forEach(msg -> System.err.println(msg));
        return new ModuleCheckResult(hadErrors, timingsMs, allModules, warningCount);
    }

    public static Map<Path, List<Path>> collectDependencyGraph(List<Node> rootAst, Path rootPath) {
        Map<Path, List<Path>> graph = new LinkedHashMap<>();
        collectDeps(rootAst, rootPath, graph, new LinkedHashSet<>());
        return graph;
    }

    private static void collectDeps(List<Node> ast, Path parentPath,
            Map<Path, List<Path>> graph, Set<Path> visited) {
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

    private static void collectAllModules(List<Node> ast, Path parentPath,
            Map<Path, ParsedModule> out, Set<Path> visited) {
        for (Node node : ast) {
            if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                continue;
            }
            Path modulePath = ModuleResolver.resolveModulePath(imp.getModule(), parentPath);
            if (!visited.add(modulePath)) {
                continue;
            }
            try {
                String source = FileLoader.readFileFromPath(modulePath.toString());
                long tokenizeStart = System.nanoTime();
                List<Token> moduleTokens = new Tokenizer().tokenize(source, false);
                long tokenizeNanos = System.nanoTime() - tokenizeStart;
                long parseStart = System.nanoTime();
                List<Node> moduleAst = new Parser().parseTokens(moduleTokens);
                long parseNanos = System.nanoTime() - parseStart;
                out.put(modulePath, new ParsedModule(modulePath, moduleAst, source,
                        moduleTokens.size(), tokenizeNanos, parseNanos));
                collectAllModules(moduleAst, modulePath, out, visited);
            } catch (Exception ignored) {
            }
        }
    }
}
