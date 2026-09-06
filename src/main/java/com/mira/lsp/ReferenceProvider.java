package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.mira.format.AstWalker;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.utils.ModuleResolver;

public class ReferenceProvider {

    public static List<Location> provide(List<Node> ast, String content, Position pos, String uri,
            Path docPath, WorkspaceIndex workspaceIndex, Path workspaceRoot,
            Map<String, String> openDocumentsByUri, boolean includeDeclaration) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return List.of();
        }
        String name = word;

        if (HoverProvider.isFieldAccess(content, pos)) {
            return textScanFieldReferences(content, uri, name);
        }

        List<Location> result = new ArrayList<>();
        collectInFile(ast, name, uri, content, includeDeclaration, result);

        if (workspaceRoot != null && docPath != null && isTopLevelSymbol(ast, name)) {
            collectCrossFile(name, docPath, workspaceIndex, workspaceRoot, openDocumentsByUri, result);
        }
        return result;
    }

    private static boolean isTopLevelSymbol(List<Node> ast, String name) {
        for (Node n : ast) {
            if (n instanceof FuncDecl f && f.getName().equals(name)) {
                return true;
            }
            if (n instanceof VarDecl v && v.getName().equals(name)) {
                return true;
            }
            if (n instanceof EnumDecl ed && ed.getIdentifier().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void collectInFile(List<Node> nodes, String name, String uri, String content,
            boolean includeDeclaration, List<Location> out) {
        Deque<Node> queue = new ArrayDeque<>(nodes);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (includeDeclaration) {
                if (n instanceof FuncDecl f) {
                    if (f.getName().equals(name)) {
                        out.add(new Location(uri, LspPositions.nameRange(content, f.line, f.nameColumn, name)));
                    }
                    addParamDecls(f.getParameters(), f.line, name, uri, content, out);
                } else if (n instanceof VarDecl v && v.getName().equals(name)) {
                    out.add(new Location(uri, LspPositions.nameRange(content, v.line, v.nameColumn, name)));
                } else if (n instanceof EnumDecl ed && ed.getIdentifier().equals(name)) {
                    out.add(new Location(uri, LspPositions.nameRange(content, ed.line, 0, name)));
                } else if (n instanceof LambdaExpression le) {
                    addParamDecls(le.getParameters(), le.line, name, uri, content, out);
                }
            }
            if (n instanceof UnaryExpression e && "$".equals(e.getOperation().getLexeme())
                    && e.getRight() instanceof DumbExpression d && name.equals(d.getValue())) {
                out.add(new Location(uri, LspPositions.nameRange(content, d.getLine(), d.getColumn(), name)));
            } else if (n instanceof CallExpression e && e.getCallee() instanceof DumbExpression d
                    && name.equals(d.getValue())) {
                out.add(new Location(uri, LspPositions.nameRange(content, d.getLine(), d.getColumn(), name)));
            }
            AstWalker.children(n, queue);
        }
    }

    private static void addParamDecls(List<Parameter> params, int declLine, String name, String uri,
            String content, List<Location> out) {
        for (Parameter p : params) {
            if (p.name().equals(name)) {
                out.add(new Location(uri, LspPositions.nameRange(content, declLine, p.column(), name)));
            }
        }
    }

    private static void collectCrossFile(String name, Path docPath, WorkspaceIndex workspaceIndex,
            Path workspaceRoot, Map<String, String> openDocumentsByUri, List<Location> out) {
        for (Path other : workspaceIndex.allMiraFiles(workspaceRoot)) {
            if (other.equals(docPath)) {
                continue;
            }
            List<Node> otherAst = workspaceIndex.getAst(other, openDocumentsByUri);
            String otherUri = other.toUri().toString();
            String alias = ModuleResolver.findAliasForModule(otherAst, other, docPath);
            if (alias != null) {
                collectQualifiedCalls(otherAst, alias, name, otherUri, out);
            }
            Set<String> directNames = ModuleResolver.findDirectBoundNames(otherAst, other, docPath);
            if (directNames.contains(name)) {
                collectDirectCalls(otherAst, name, otherUri, out);
            }
        }
    }

    private static void collectDirectCalls(List<Node> ast, String name, String uri, List<Location> out) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof CallExpression e && e.getCallee() instanceof DumbExpression d
                    && name.equals(d.getValue())) {
                int line = Math.max(d.getLine() - 1, 0);
                int col = Math.max(d.getColumn() - 1, 0);
                out.add(new Location(uri, new Range(new Position(line, col), new Position(line, col + name.length()))));
            }
            AstWalker.children(n, queue);
        }
    }

    private static void collectQualifiedCalls(List<Node> ast, String alias, String name, String uri,
            List<Location> out) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof NamespaceCallExpression nce
                    && alias.equals(nce.getAlias()) && name.equals(nce.getFunctionName())) {
                int line = Math.max(nce.getLine() - 1, 0);
                int col = Math.max(nce.getColumn() - 1, 0);
                out.add(new Location(uri, new Range(new Position(line, col), new Position(line, col + name.length()))));
            }
            AstWalker.children(n, queue);
        }
    }

    /**
     * Finds {@code .name} occurrences via the real token stream rather than a
     * raw text scan, so matches inside string literals or comments (which
     * produce no tokens, or a single opaque STRING_LITERAL token) are never
     * mistaken for a genuine field reference.
     */
    private static List<Location> textScanFieldReferences(String content, String uri, String name) {
        List<Location> out = new ArrayList<>();
        List<Token> tokens;
        try {
            tokens = new Tokenizer().tokenize(content, false);
        } catch (Exception e) {
            return out;
        }
        List<Token> significant = tokens.stream()
                .filter(t -> !(t.getTokenType() == TokenType.EXPRESSION && t.getLexeme().isBlank()))
                .toList();
        for (int i = 1; i < significant.size(); i++) {
            Token prev = significant.get(i - 1);
            Token curr = significant.get(i);
            boolean prevIsDot = ".".equals(prev.getLexeme()) && prev.getTokenType() != TokenType.STRING_LITERAL;
            boolean currIsName = name.equals(curr.getLexeme()) && curr.getTokenType() != TokenType.STRING_LITERAL;
            if (prevIsDot && currIsName) {
                int line = Math.max(curr.getLine() - 1, 0);
                int col = Math.max(curr.getColumn() - 1, 0);
                out.add(new Location(uri, new Range(new Position(line, col), new Position(line, col + name.length()))));
            }
        }
        return out;
    }
}
