package com.mira.resolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.utils.FileLoader;
import com.mira.utils.ModuleResolver;
import com.mira.warning.WarningCollector;

public final class ModuleChecker {

    public record ParsedModule(Path path, List<Node> ast, String source) {

    }

    private ModuleChecker() {
    }

    public static void check(List<Node> rootAst, Set<Path> visited) {
        Map<Path, ParsedModule> allModules = new LinkedHashMap<>();
        collectAllModules(rootAst, Flags.inputPath.get(), allModules, new LinkedHashSet<>(visited));

        Path rootPath = Flags.inputPath.get();
        String savedFileName = Flags.fileName;
        String[] savedSourceLines = Flags.sourceLines;

        for (ParsedModule module : allModules.values()) {
            Set<String> externalCalls = new LinkedHashSet<>();
            ModuleResolver.collectExternalCalls(rootAst, rootPath, module.path(), externalCalls);
            for (ParsedModule caller : allModules.values()) {
                if (!caller.path().equals(module.path())) {
                    ModuleResolver.collectExternalCalls(caller.ast(), caller.path(), module.path(), externalCalls);
                }
            }
            try {
                Flags.inputPath.set(module.path());
                Flags.fileName = module.path().getFileName().toString();
                Flags.sourceLines = module.source().split("\n", -1);
                new StaticCheck(externalCalls).check(module.ast());
                WarningCollector.flush();
            } catch (MultipleStaticCheckErrors mse) {
                WarningCollector.flush();
                mse.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
            } catch (Exception ignored) {
            } finally {
                Flags.inputPath.set(rootPath);
                Flags.fileName = savedFileName;
                Flags.sourceLines = savedSourceLines;
            }
        }
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
                List<Node> moduleAst = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
                out.put(modulePath, new ParsedModule(modulePath, moduleAst, source));
                collectAllModules(moduleAst, modulePath, out, visited);
            } catch (Exception ignored) {
            }
        }
    }
}
