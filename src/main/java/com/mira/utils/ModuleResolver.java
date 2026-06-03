package com.mira.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.resolver.StaticCheck;

public final class ModuleResolver {

    private ModuleResolver() {
    }

    public static Path resolveModulePath(String rawModule, Path parentPath) {
        String raw = rawModule.replace("\"", "");
        if (!raw.endsWith(".mira")) {
            raw += ".mira";
        }
        Path candidate = Paths.get(raw);
        return candidate.isAbsolute() ? candidate.normalize()
                : parentPath.getParent().resolve(candidate).normalize();
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
