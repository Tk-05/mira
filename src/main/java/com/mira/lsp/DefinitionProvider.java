package com.mira.lsp;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.mira.format.AstWalker;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.StructInitExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.utils.ModuleResolver;

public class DefinitionProvider {

    public static Location provide(List<Node> ast, String content, String docUri, Position pos) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }
        if (HoverProvider.isFieldAccess(content, pos)) {
            String objectName = objectBefore(content, pos);
            Path docPath = uriToPath(docUri);
            return findFieldDefinition(ast, word, objectName, docUri, content, docPath, pos.getLine() + 1);
        }

        Location loc = findScoped(ast, word, docUri, content, pos.getLine() + 1);
        if (loc != null) {
            return loc;
        }

        Path docPath = uriToPath(docUri);
        if (docPath == null) {
            return null;
        }
        for (Node n : ast) {
            if (!(n instanceof ImportExpression imp)) {
                continue;
            }
            if (imp.getKind() != ImportExpression.ImportKind.MODULE) {
                continue;
            }
            String alias = imp.getNamespace();

            Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), docPath);

            if (alias != null && alias.equals(word)) {
                if (Files.exists(modPath)) {
                    Range r = new Range(new Position(0, 0), new Position(0, 0));
                    return new Location(modPath.toUri().toString(), r);
                }
                continue;
            }
            Location found = searchInModule(modPath, word, null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static String objectBefore(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) {
            return null;
        }
        String line = lines[pos.getLine()];
        int col = Math.min(pos.getCharacter(), line.length());
        int dotIdx = line.lastIndexOf('.', col - 1);
        if (dotIdx <= 0) {
            return null;
        }
        int end = dotIdx;
        // Null-safe field access (`p?.field`) - the receiver name sits before
        // the '?', not directly before the '.'.
        if (end > 0 && line.charAt(end - 1) == '?') {
            end--;
        }
        int start = end - 1;
        while (start >= 0 && (Character.isLetterOrDigit(line.charAt(start)) || line.charAt(start) == '_')) {
            start--;
        }
        start++;
        if (start >= end) {
            return null;
        }
        String id = line.substring(start, end);
        return id.isEmpty() ? null : id;
    }

    private static Location findFieldDefinition(
            List<Node> ast, String fieldName, String objectName,
            String uri, String content, Path docPath, int cursorLine) {

        if (objectName != null) {
            Node type = resolveObjectType(ast, objectName, cursorLine);
            if (type != null) {
                // objectName resolved to a specific, known declaration in scope -
                // trust that resolution rather than falling through to a blind
                // whole-file field search, which could land on an unrelated
                // same-named field on a completely different (shadowed) object.
                return searchFieldInType(type, fieldName, uri, content);
            }
            // objectName didn't resolve to any local declaration - it might be
            // a module namespace alias instead.
            if (docPath != null) {
                for (Node n : ast) {
                    if (n instanceof ImportExpression imp
                            && imp.getKind() == ImportExpression.ImportKind.MODULE
                            && objectName.equals(imp.getNamespace())) {
                        Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), docPath);
                        Location loc = searchInModule(modPath, fieldName, null);
                        if (loc == null) {
                            loc = searchInModuleForField(modPath, fieldName);
                        }
                        if (loc != null) {
                            return loc;
                        }
                    }
                }
            }
        }
        return findFieldInAllNodes(ast, fieldName, uri, content);
    }

    /**
     * Resolves what {@code objectName} refers to at {@code cursorLine},
     * respecting shadowing: a declaration local to the innermost enclosing
     * function wins over a same-named declaration anywhere else in the file
     * (module scope, or another, unrelated function). Without this, two
     * unrelated objects that happen to share a variable name would be
     * indistinguishable to callers.
     */
    static Node resolveObjectType(List<Node> ast, String objectName, int cursorLine) {
        FuncDecl enclosing = findEnclosingFunction(ast, cursorLine);
        if (enclosing != null) {
            VarDecl nearest = nearestVarDecl(enclosing.getBody(), objectName, cursorLine);
            if (nearest != null) {
                return typeOfVarDecl(nearest, ast, cursorLine);
            }
        }
        for (Node n : ast) {
            if (n instanceof VarDecl vd && vd.getName().equals(objectName)) {
                return typeOfVarDecl(vd, ast, cursorLine);
            }
            if (n instanceof EnumDecl ed && ed.getIdentifier().equals(objectName)) {
                return ed;
            }
        }
        return null;
    }

    private static Node typeOfVarDecl(VarDecl vd, List<Node> ast, int cursorLine) {
        Node init = vd.getInitializer();
        if (init instanceof ObjectExpression || init instanceof StructExpression) {
            return init;
        }
        if (init instanceof StructInitExpression si) {
            String templateName = extractName(si.getTarget());
            if (templateName != null) {
                return resolveObjectType(ast, templateName, cursorLine);
            }
        }
        return null;
    }

    /**
     * Innermost function (by smallest line range) whose body contains
     * {@code cursorLine}.
     */
    private static FuncDecl findEnclosingFunction(List<Node> ast, int cursorLine) {
        FuncDecl best = null;
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof FuncDecl f && f.line > 0 && f.endLine > 0
                    && f.line <= cursorLine && cursorLine <= f.endLine
                    && (best == null || (f.endLine - f.line) < (best.endLine - best.line))) {
                best = f;
            }
            AstWalker.children(n, queue);
        }
        return best;
    }

    /**
     * Among every {@code var objectName} reachable within {@code body}
     * (including nested blocks/functions), picks the one declared closest to
     * (and at or before) {@code cursorLine} - the nearest enclosing
     * declaration, matching normal lexical shadowing instead of
     * file-declaration-order.
     */
    private static VarDecl nearestVarDecl(List<Node> body, String objectName, int cursorLine) {
        VarDecl bestBefore = null;
        VarDecl bestAfter = null;
        Deque<Node> queue = new ArrayDeque<>(body);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof VarDecl vd && vd.getName().equals(objectName) && vd.line > 0) {
                if (vd.line <= cursorLine && (bestBefore == null || vd.line > bestBefore.line)) {
                    bestBefore = vd;
                } else if (vd.line > cursorLine && (bestAfter == null || vd.line < bestAfter.line)) {
                    bestAfter = vd;
                }
            }
            AstWalker.children(n, queue);
        }
        return bestBefore != null ? bestBefore : bestAfter;
    }

    private static String extractName(Node expr) {
        if (expr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d) {
            return d.getValue();
        }
        if (expr instanceof DumbExpression d) {
            return d.getValue();
        }
        return null;
    }

    /**
     * Whether {@code type} (a struct/object literal or enum) already declares a
     * member named {@code name} - field, method, or enum value. Used by
     * {@link RenameProvider} to refuse a field rename that would collide with
     * an existing sibling member.
     */
    static boolean typeHasMember(Node type, String name) {
        if (findMethodInType(type, name) != null) {
            return true;
        }
        if (type instanceof ObjectExpression obj) {
            return obj.getVarDecls().stream().anyMatch(f -> f.getName().equals(name));
        }
        if (type instanceof StructExpression st) {
            return st.getVarDecls().stream().anyMatch(f -> f.getName().equals(name));
        }
        if (type instanceof EnumDecl ed) {
            return ed.getValues().containsKey(name);
        }
        return false;
    }

    static FuncDecl findMethodInType(Node type, String methodName) {
        if (type instanceof ObjectExpression obj) {
            for (FuncDecl m : obj.getMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        }
        if (type instanceof StructExpression st) {
            for (FuncDecl m : st.getMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Location searchFieldInType(Node type, String fieldName, String uri, String content) {
        if (type instanceof ObjectExpression obj) {
            for (VarDecl f : obj.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, f.line, f.nameColumn, fieldName);
                }
            }
            for (FuncDecl m : obj.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, m.line, m.nameColumn, fieldName);
                }
            }
        }
        if (type instanceof StructExpression st) {
            for (VarDecl f : st.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, f.line, f.nameColumn, fieldName);
                }
            }
            for (FuncDecl m : st.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, m.line, m.nameColumn, fieldName);
                }
            }
        }
        if (type instanceof EnumDecl ed && ed.getValues().containsKey(fieldName)) {
            return locationFor(uri, content, ed.line, fieldName);
        }
        return null;
    }

    private static Location findFieldInAllNodes(List<Node> ast, String fieldName, String uri, String content) {
        for (Node n : ast) {
            Location loc = findFieldInNode(n, fieldName, uri, content);
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private static Location findFieldInNode(Node n, String fieldName, String uri, String content) {
        if (n instanceof ObjectExpression obj) {
            for (VarDecl f : obj.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, f.line, f.nameColumn, fieldName);
                }
            }
            for (FuncDecl m : obj.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, m.line, m.nameColumn, fieldName);
                }
            }
        }
        if (n instanceof StructExpression st) {
            for (VarDecl f : st.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, f.line, f.nameColumn, fieldName);
                }
            }
            for (FuncDecl m : st.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    return locationForDecl(uri, content, m.line, m.nameColumn, fieldName);
                }
            }
        }
        if (n instanceof VarDecl vd && vd.getInitializer() != null) {
            return findFieldInNode(vd.getInitializer(), fieldName, uri, content);
        }
        if (n instanceof FuncDecl f) {
            for (Node bodyNode : f.getBody()) {
                Location l = findFieldInNode(bodyNode, fieldName, uri, content);
                if (l != null) {
                    return l;
                }
            }
        }
        return null;
    }

    /**
     * A lexical scope: the container statement that introduces it (null for
     * top-level) and its body.
     */
    private record Scope(Node owner, List<Node> body) {

    }

    /**
     * Resolves a plain (non-field) identifier reference at {@code cursorLine}
     * by walking outward through the chain of lexical scopes actually enclosing
     * the cursor - innermost first - so an inner declaration correctly shadows
     * an unrelated same-named declaration elsewhere in the file (e.g. in a
     * sibling branch, or at the top level), instead of returning whichever
     * declaration happens to appear first in AST traversal order regardless of
     * scope.
     */
    static Location findScoped(List<Node> ast, String name, String uri, String content, int cursorLine) {
        for (Scope scope : buildScopeChain(ast, cursorLine)) {
            Location found = findInScopeLevel(scope, name, uri, content, cursorLine);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Builds the full chain of scopes enclosing {@code cursorLine}, innermost
     * first. Built up front (rather than descending and searching in the same
     * pass) so that failing to find {@code name} in the innermost scope falls
     * back to searching each enclosing scope in turn, instead of stopping as
     * soon as there is nothing deeper left to descend into.
     */
    private static List<Scope> buildScopeChain(List<Node> ast, int cursorLine) {
        List<Scope> chain = new ArrayList<>();
        Scope current = new Scope(null, ast);
        chain.add(current);
        Scope child;
        while ((child = enclosingChild(current, cursorLine)) != null) {
            chain.add(0, child);
            current = child;
        }
        return chain;
    }

    /**
     * Descends into whichever direct child of {@code scope} actually contains
     * {@code cursorLine}, if any.
     */
    private static Scope enclosingChild(Scope scope, int cursorLine) {
        for (Node n : scope.body()) {
            if (!(n instanceof Statement s) || s.line <= 0 || s.endLine <= 0
                    || cursorLine < s.line || cursorLine > s.endLine) {
                continue;
            }
            List<Node> child = childBodyAt(n, cursorLine);
            if (child != null) {
                return new Scope(n, child);
            }
        }
        return null;
    }

    private static List<Node> childBodyAt(Node n, int cursorLine) {
        if (n instanceof FuncDecl f) {
            return f.getBody();
        }
        if (n instanceof If stmt) {
            List<Node> branch = branchContaining(stmt.getThenBody(), cursorLine);
            return branch != null ? branch : branchContaining(stmt.getElseBody(), cursorLine);
        }
        if (n instanceof Loop stmt) {
            return stmt.getBody();
        }
        if (n instanceof While stmt) {
            return stmt.getBody();
        }
        if (n instanceof Block stmt) {
            return stmt.getBody();
        }
        if (n instanceof Switch stmt) {
            for (Switch.SwitchCase sc : stmt.getCases()) {
                List<Node> branch = branchContaining(sc.getBody(), cursorLine);
                if (branch != null) {
                    return branch;
                }
            }
            return branchContaining(stmt.getDefaultBody(), cursorLine);
        }
        if (n instanceof TryCatch stmt) {
            List<Node> branch = branchContaining(stmt.getTryBody(), cursorLine);
            if (branch != null) {
                return branch;
            }
            for (TryCatch.CatchClause cc : stmt.getCatchClauses()) {
                branch = branchContaining(cc.getBody(), cursorLine);
                if (branch != null) {
                    return branch;
                }
            }
            return branchContaining(stmt.getFinallyBody(), cursorLine);
        }
        if (n instanceof Lock stmt) {
            return stmt.getBody();
        }
        if (n instanceof ComptimeBlock stmt) {
            return stmt.getBody();
        }
        return null;
    }

    /**
     * Whether {@code cursorLine} falls within the line span actually covered by
     * this specific body's statements.
     */
    private static List<Node> branchContaining(List<Node> body, int cursorLine) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (Node n : body) {
            if (n instanceof Statement s && s.line > 0) {
                min = Math.min(min, s.line);
                max = Math.max(max, s.endLine > 0 ? s.endLine : s.line);
            }
        }
        if (min == Integer.MAX_VALUE || max < 0) {
            return null;
        }
        return (cursorLine >= min && cursorLine <= max) ? body : null;
    }

    private static Location findInScopeLevel(Scope scope, String name, String uri, String content, int cursorLine) {
        if (scope.owner() instanceof FuncDecl f) {
            for (Parameter p : f.getParameters()) {
                if (p.name().equals(name)) {
                    return locationFor(uri, content, f.line, p.name());
                }
            }
        }
        if (scope.owner() instanceof Loop loop) {
            if (loop.isForeach()) {
                VarDecl iter = loop.getIterator();
                if (iter.getName().equals(name)) {
                    return locationForDecl(uri, content, iter.line, iter.nameColumn, iter.getName());
                }
            } else {
                Location l = findDirectInBody(loop.getVarDecls(), name, uri, content, cursorLine);
                if (l != null) {
                    return l;
                }
            }
        }
        return findDirectInBody(scope.body(), name, uri, content, cursorLine);
    }

    /**
     * Searches only the direct statements of {@code body} (not nested blocks)
     * for a declaration of {@code name}, preferring the one closest to (and at
     * or before) {@code cursorLine} - the nearest enclosing declaration -
     * falling back to the nearest one after it if none precede.
     */
    private static Location findDirectInBody(List<Node> body, String name, String uri, String content,
            int cursorLine) {
        Location before = null;
        int beforeLine = -1;
        Location after = null;
        int afterLine = Integer.MAX_VALUE;
        for (Node n : body) {
            Location candidate = null;
            int declLine = -1;
            if (n instanceof VarDecl v && v.getName().equals(name)) {
                candidate = locationForDecl(uri, content, v.line, v.nameColumn, v.getName());
                declLine = v.line;
            } else if (n instanceof VarDestructure vd) {
                List<String> names = vd.getNames();
                for (int i = 0; i < names.size(); i++) {
                    if (names.get(i).equals(name)) {
                        candidate = locationForDecl(uri, content, vd.line, vd.getNameColumns().get(i), name);
                        declLine = vd.line;
                        break;
                    }
                }
            } else if (n instanceof FuncDecl f && f.getName().equals(name)) {
                candidate = locationForDecl(uri, content, f.line, f.nameColumn, f.getName());
                declLine = f.line;
            } else if (n instanceof EnumDecl ed && ed.getIdentifier().equals(name)) {
                candidate = locationFor(uri, content, ed.line, ed.getIdentifier());
                declLine = ed.line;
            }
            if (candidate == null || declLine <= 0) {
                continue;
            }
            if (declLine <= cursorLine && declLine > beforeLine) {
                before = candidate;
                beforeLine = declLine;
            } else if (declLine > cursorLine && declLine < afterLine) {
                after = candidate;
                afterLine = declLine;
            }
        }
        return before != null ? before : after;
    }

    private static Location findInNodes(List<Node> nodes, String name, String uri, String content) {
        for (Node n : nodes) {
            Location loc = findInNode(n, name, uri, content);
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private static Location findInNode(Node n, String name, String uri, String content) {
        if (n instanceof FuncDecl f) {
            if (f.getName().equals(name)) {
                return locationForDecl(uri, content, f.line, f.nameColumn, f.getName());
            }
            for (Parameter p : f.getParameters()) {
                if (p.name().equals(name)) {
                    return locationFor(uri, content, f.line, p.name());
                }
            }
            return findInNodes(f.getBody(), name, uri, content);
        }
        if (n instanceof VarDecl v && v.getName().equals(name)) {
            return locationForDecl(uri, content, v.line, v.nameColumn, v.getName());
        }
        if (n instanceof VarDestructure vd) {
            List<String> names = vd.getNames();
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(name)) {
                    return locationForDecl(uri, content, vd.line, vd.getNameColumns().get(i), name);
                }
            }
        }
        if (n instanceof EnumDecl ed && ed.getIdentifier().equals(name)) {
            return locationFor(uri, content, ed.line, ed.getIdentifier());
        }
        if (n instanceof If stmt) {
            Location l = findInNodes(stmt.getThenBody(), name, uri, content);
            if (l != null) {
                return l;
            }
            if (stmt.getElseBody() != null) {
                return findInNodes(stmt.getElseBody(), name, uri, content);
            }
        }
        if (n instanceof Loop stmt) {
            if (stmt.isForeach()) {
                VarDecl iter = stmt.getIterator();
                if (iter.getName().equals(name)) {
                    return locationForDecl(uri, content, iter.line, iter.nameColumn, iter.getName());
                }
            } else {
                Location l = findInNodes(stmt.getVarDecls(), name, uri, content);
                if (l != null) {
                    return l;
                }
            }
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        if (n instanceof While stmt) {
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        if (n instanceof Block stmt) {
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        if (n instanceof Switch stmt) {
            for (Switch.SwitchCase sc : stmt.getCases()) {
                Location l = findInNodes(sc.getBody(), name, uri, content);
                if (l != null) {
                    return l;
                }
            }
            if (stmt.getDefaultBody() != null) {
                return findInNodes(stmt.getDefaultBody(), name, uri, content);
            }
        }
        if (n instanceof TryCatch stmt) {
            Location l = findInNodes(stmt.getTryBody(), name, uri, content);
            if (l != null) {
                return l;
            }
            for (TryCatch.CatchClause cc : stmt.getCatchClauses()) {
                l = findInNodes(cc.getBody(), name, uri, content);
                if (l != null) {
                    return l;
                }
            }
            if (stmt.getFinallyBody() != null) {
                return findInNodes(stmt.getFinallyBody(), name, uri, content);
            }
        }
        if (n instanceof Lock stmt) {
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        if (n instanceof ComptimeBlock stmt) {
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        return null;
    }

    private static Location searchInModule(Path modPath, String name, String fieldName) {
        if (!Files.exists(modPath)) {
            return null;
        }
        try {
            String src = Files.readString(modPath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            String modUri = modPath.toUri().toString();
            return findInNodes(modAst, name, modUri, src);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Location searchInModuleForField(Path modPath, String fieldName) {
        if (!Files.exists(modPath)) {
            return null;
        }
        try {
            String src = Files.readString(modPath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            String modUri = modPath.toUri().toString();
            return findFieldInAllNodes(modAst, fieldName, modUri, src);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Location locationForDecl(String uri, String content, int line, int nameColumn, String name) {
        return new Location(uri, LspPositions.nameRange(content, line, nameColumn, name));
    }

    private static Location locationFor(String uri, String content, int line, String name) {
        return new Location(uri, LspPositions.nameRange(content, line, 0, name));
    }

    private static Path uriToPath(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return Paths.get(new URI(uri));
        } catch (Exception e) {
            return null;
        }
    }
}
