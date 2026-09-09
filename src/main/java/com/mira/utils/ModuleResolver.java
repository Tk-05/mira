package com.mira.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mira.cli.Flags;
import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.resolver.StaticCheck;

public final class ModuleResolver {

    private ModuleResolver() {
    }

    public static List<Path> findAllMiraFiles(Path root) {
        List<Path> result = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".mira")).forEach(result::add);
        } catch (IOException ignored) {
        }
        return result;
    }

    public static Path resolveModulePath(String rawModule, Path parentPath) {
        String raw = rawModule.replace("\"", "");
        if (!raw.endsWith(".mira")) {
            raw += ".mira";
        }
        Path candidate = Paths.get(raw);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        Path resolved = parentPath.getParent().resolve(candidate).normalize();
        if (!Files.exists(resolved) && !Flags.dependencyRoots.isEmpty()) {
            for (Path depRoot : Flags.dependencyRoots) {
                Path depCandidate = depRoot.resolve(candidate).normalize();
                if (Files.exists(depCandidate)) {
                    return depCandidate;
                }
            }
        }
        return resolved;
    }

    public static String findAliasForModule(List<Node> callerAst, Path callerPath, Path targetPath) {
        for (Node node : callerAst) {
            if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                continue;
            }
            if (resolveModulePath(imp.getModule(), callerPath).equals(targetPath)) {
                return imp.getNamespace();
            }
        }
        return null;
    }

    public static void collectExternalCalls(List<Node> callerAst, Path callerPath, Path targetPath, Set<String> out) {
        String alias = findAliasForModule(callerAst, callerPath, targetPath);
        if (alias != null) {
            out.addAll(StaticCheck.collectNamespaceCalls(callerAst, alias));
        }
        Set<String> directNames = findDirectBoundNames(callerAst, callerPath, targetPath);
        if (!directNames.isEmpty()) {
            collectDirectCalls(callerAst, directNames, out);
        }
    }

    /**
     * Names bound directly (no namespace prefix) into the caller's scope by a
     * selective, non-aliased module import of {@code targetPath} — e.g.
     * {@code import module "a.mira" {greet};} binds {@code greet} bare, unlike an
     * aliased import which only exposes {@code alias.greet}.
     */
    public static Set<String> findDirectBoundNames(List<Node> callerAst, Path callerPath, Path targetPath) {
        for (Node node : callerAst) {
            if (!(node instanceof ImportExpression imp) || imp.getKind() != ImportKind.MODULE) {
                continue;
            }
            if (imp.getNamespace() == null && imp.isSelective()
                    && resolveModulePath(imp.getModule(), callerPath).equals(targetPath)) {
                return new HashSet<>(imp.getSelectedFunctions());
            }
        }
        return Set.of();
    }

    private static void collectDirectCalls(List<Node> ast, Set<String> names, Set<String> out) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node == null) {
                continue;
            }
            if (node instanceof CallExpression ce && ce.getCallee() instanceof DumbExpression d
                    && names.contains(d.getValue())) {
                out.add(d.getValue());
            }
            AstWalker.children(node, queue);
        }
    }
}
