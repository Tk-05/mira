package com.mira.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mira.cli.Flags;
import com.mira.parser.nodes.Node;
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

    public static void collectExternalCalls(List<Node> callerAst, Path callerPath,
            Path targetPath, Set<String> out) {
        String alias = findAliasForModule(callerAst, callerPath, targetPath);
        if (alias != null) {
            out.addAll(StaticCheck.collectNamespaceCalls(callerAst, alias));
        }
    }
}
