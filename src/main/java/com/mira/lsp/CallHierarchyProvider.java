package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.utils.ModuleResolver;

public class CallHierarchyProvider {

    private record TargetRef(FuncDecl decl, String uri, String content) {

    }

    private record Hit(FuncDecl caller, String uri, String content, Range range) {

    }

    private record Frame(Node node, FuncDecl enclosing) {

    }

    public static List<CallHierarchyItem> prepare(List<Node> ast, String content, Position pos, String uri,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        Location defLoc = DefinitionProvider.provide(ast, content, uri, pos);
        if (defLoc == null) {
            return List.of();
        }
        List<Node> targetAst = ast;
        String targetContent = content;
        if (!defLoc.getUri().equals(uri)) {
            Path targetPath = DocumentService.uriToPath(defLoc.getUri());
            if (targetPath == null || workspaceIndex == null) {
                return List.of();
            }
            targetAst = workspaceIndex.getAst(targetPath, openDocumentsByUri);
            targetContent = workspaceIndex.getSource(targetPath, openDocumentsByUri);
        }
        int declLine = defLoc.getRange().getStart().getLine() + 1;
        FuncDecl target = findFuncDeclAtLine(targetAst, declLine);
        if (target == null) {
            return List.of();
        }
        return List.of(toItem(target, defLoc.getUri(), targetContent));
    }

    public static List<CallHierarchyOutgoingCall> outgoingCalls(CallHierarchyItem item, WorkspaceIndex workspaceIndex,
            Map<String, String> openDocumentsByUri) {
        Path sourcePath = DocumentService.uriToPath(item.getUri());
        if (sourcePath == null || workspaceIndex == null) {
            return List.of();
        }
        List<Node> ast = workspaceIndex.getAst(sourcePath, openDocumentsByUri);
        int declLine = item.getSelectionRange().getStart().getLine() + 1;
        FuncDecl source = findFuncDeclAtLine(ast, declLine);
        if (source == null) {
            return List.of();
        }

        Map<String, CallHierarchyItem> itemByKey = new LinkedHashMap<>();
        Map<String, List<Range>> rangesByKey = new LinkedHashMap<>();
        Deque<Node> queue = new ArrayDeque<>(source.getBody());
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof CallExpression e && e.getCallee() instanceof DumbExpression d) {
                resolveDirectTarget(ast, sourcePath, d.getValue(), workspaceIndex, openDocumentsByUri)
                        .ifPresent(t -> addOutgoingHit(itemByKey, rangesByKey, t,
                        rangeOf(d.getLine(), d.getColumn(), d.getValue().length())));
            } else if (n instanceof NamespaceCallExpression nce) {
                resolveNamespacedTarget(ast, sourcePath, nce.getAlias(), nce.getFunctionName(), workspaceIndex,
                        openDocumentsByUri)
                        .ifPresent(t -> addOutgoingHit(itemByKey, rangesByKey, t,
                        rangeOf(nce.getLine(), nce.getColumn(), nce.getFunctionName().length())));
            }
            AstWalker.children(n, queue);
        }

        List<CallHierarchyOutgoingCall> result = new ArrayList<>();
        for (String key : itemByKey.keySet()) {
            result.add(new CallHierarchyOutgoingCall(itemByKey.get(key), rangesByKey.get(key)));
        }
        return result;
    }

    public static List<CallHierarchyIncomingCall> incomingCalls(CallHierarchyItem item, WorkspaceIndex workspaceIndex,
            Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        Path targetPath = DocumentService.uriToPath(item.getUri());
        if (targetPath == null || workspaceIndex == null) {
            return List.of();
        }
        String targetName = item.getName();
        List<Hit> hits = new ArrayList<>();

        List<Node> targetFileAst = workspaceIndex.getAst(targetPath, openDocumentsByUri);
        String targetFileContent = workspaceIndex.getSource(targetPath, openDocumentsByUri);
        collectDirectCallers(targetFileAst, targetName, item.getUri(), targetFileContent, hits);

        if (workspaceRoot != null) {
            for (Path other : workspaceIndex.allMiraFiles(workspaceRoot)) {
                if (other.equals(targetPath)) {
                    continue;
                }
                List<Node> otherAst = workspaceIndex.getAst(other, openDocumentsByUri);
                String otherUri = other.toUri().toString();
                String otherContent = workspaceIndex.getSource(other, openDocumentsByUri);
                String alias = ModuleResolver.findAliasForModule(otherAst, other, targetPath);
                if (alias != null) {
                    collectQualifiedCallers(otherAst, alias, targetName, otherUri, otherContent, hits);
                }
                if (ModuleResolver.findDirectBoundNames(otherAst, other, targetPath).contains(targetName)) {
                    collectDirectCallers(otherAst, targetName, otherUri, otherContent, hits);
                }
            }
        }

        Map<String, CallHierarchyItem> itemByKey = new LinkedHashMap<>();
        Map<String, List<Range>> rangesByKey = new LinkedHashMap<>();
        for (Hit h : hits) {
            String key = h.uri() + "#" + h.caller().line;
            itemByKey.computeIfAbsent(key, k -> toItem(h.caller(), h.uri(), h.content()));
            rangesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(h.range());
        }
        List<CallHierarchyIncomingCall> result = new ArrayList<>();
        for (String key : itemByKey.keySet()) {
            result.add(new CallHierarchyIncomingCall(itemByKey.get(key), rangesByKey.get(key)));
        }
        return result;
    }

    private static void walkWithEnclosingFunc(List<Node> ast, BiConsumer<Node, FuncDecl> visitor) {
        Deque<Frame> stack = new ArrayDeque<>();
        for (Node n : ast) {
            stack.push(new Frame(n, null));
        }
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame.node() == null) {
                continue;
            }
            visitor.accept(frame.node(), frame.enclosing());
            FuncDecl next = frame.node() instanceof FuncDecl f ? f : frame.enclosing();
            Deque<Node> children = new ArrayDeque<>();
            AstWalker.children(frame.node(), children);
            for (Node c : children) {
                stack.push(new Frame(c, next));
            }
        }
    }

    private static void collectDirectCallers(List<Node> ast, String targetName, String uri, String content,
            List<Hit> out) {
        walkWithEnclosingFunc(ast, (node, enclosing) -> {
            if (enclosing != null && node instanceof CallExpression e
                    && e.getCallee() instanceof DumbExpression d && targetName.equals(d.getValue())) {
                out.add(new Hit(enclosing, uri, content, rangeOf(d.getLine(), d.getColumn(), targetName.length())));
            }
        });
    }

    private static void collectQualifiedCallers(List<Node> ast, String alias, String targetName, String uri,
            String content, List<Hit> out) {
        walkWithEnclosingFunc(ast, (node, enclosing) -> {
            if (enclosing != null && node instanceof NamespaceCallExpression nce
                    && alias.equals(nce.getAlias()) && targetName.equals(nce.getFunctionName())) {
                out.add(new Hit(enclosing, uri, content, rangeOf(nce.getLine(), nce.getColumn(), targetName.length())));
            }
        });
    }

    private static Optional<TargetRef> resolveDirectTarget(List<Node> callerAst, Path callerPath, String name,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        FuncDecl local = findFuncDeclByName(callerAst, name);
        if (local != null) {
            return Optional.of(new TargetRef(local, callerPath.toUri().toString(),
                    workspaceIndex.getSource(callerPath, openDocumentsByUri)));
        }
        for (Node n : callerAst) {
            if (n instanceof ImportExpression imp && imp.getKind() == ImportKind.MODULE
                    && imp.getNamespace() == null && imp.isSelective()
                    && imp.getSelectedFunctions() != null && imp.getSelectedFunctions().contains(name)) {
                Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), callerPath);
                FuncDecl found = findFuncDeclByName(workspaceIndex.getAst(modPath, openDocumentsByUri), name);
                if (found != null) {
                    return Optional.of(new TargetRef(found, modPath.toUri().toString(),
                            workspaceIndex.getSource(modPath, openDocumentsByUri)));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<TargetRef> resolveNamespacedTarget(List<Node> callerAst, Path callerPath, String alias,
            String name, WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        for (Node n : callerAst) {
            if (n instanceof ImportExpression imp && imp.getKind() == ImportKind.MODULE
                    && alias.equals(imp.getNamespace())) {
                Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), callerPath);
                FuncDecl found = findFuncDeclByName(workspaceIndex.getAst(modPath, openDocumentsByUri), name);
                if (found != null) {
                    return Optional.of(new TargetRef(found, modPath.toUri().toString(),
                            workspaceIndex.getSource(modPath, openDocumentsByUri)));
                }
            }
        }
        return Optional.empty();
    }

    private static void addOutgoingHit(Map<String, CallHierarchyItem> itemByKey, Map<String, List<Range>> rangesByKey,
            TargetRef target, Range callSiteRange) {
        String key = target.uri() + "#" + target.decl().line;
        itemByKey.computeIfAbsent(key, k -> toItem(target.decl(), target.uri(), target.content()));
        rangesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(callSiteRange);
    }

    private static FuncDecl findFuncDeclAtLine(List<Node> ast, int line) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof FuncDecl f && f.line == line) {
                return f;
            }
            AstWalker.children(n, queue);
        }
        return null;
    }

    private static FuncDecl findFuncDeclByName(List<Node> ast, String name) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof FuncDecl f && f.getName().equals(name)) {
                return f;
            }
            AstWalker.children(n, queue);
        }
        return null;
    }

    private static CallHierarchyItem toItem(FuncDecl f, String uri, String content) {
        Range selection = LspPositions.nameRange(content, f.line, f.nameColumn, f.getName());
        Range full = LspPositions.fullRange(content, f.line, f.endLine > 0 ? f.endLine : f.line, selection);
        return new CallHierarchyItem(f.getName(), SymbolKind.Function, uri, full, selection);
    }

    private static Range rangeOf(int line, int column, int length) {
        int lspLine = Math.max(line - 1, 0);
        int col = Math.max(column - 1, 0);
        return new Range(new Position(lspLine, col), new Position(lspLine, col + length));
    }
}
