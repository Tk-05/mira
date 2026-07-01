package com.mira.lsp;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

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
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

public class DefinitionProvider {

    public static Location provide(List<Node> ast, String content, String docUri, Position pos) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }
        String stripped = word.startsWith("$") ? word.substring(1) : word;

        if (HoverProvider.isFieldAccess(content, pos)) {
            String objectName = objectBefore(content, pos);
            Path docPath = uriToPath(docUri);
            return findFieldDefinition(ast, stripped, objectName, docUri, content, docPath);
        }

        Location loc = findInNodes(ast, stripped, docUri, content);
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
            if (alias == null) {
                continue;
            }

            String raw = imp.getModule().replace("\"", "");
            if (!raw.endsWith(".mira")) {
                raw += ".mira";
            }
            Path modPath = docPath.getParent().resolve(raw).normalize();

            if (alias.equals(stripped)) {
                if (Files.exists(modPath)) {
                    Range r = new Range(new Position(0, 0), new Position(0, 0));
                    return new Location(modPath.toUri().toString(), r);
                }
                continue;
            }
            Location found = searchInModule(modPath, stripped, null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // --- Field access (dot notation) ---
    private static String objectBefore(String content, Position pos) {
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
            String uri, String content, Path docPath) {

        if (objectName != null) {
            // 1. Resolve local type
            Node type = resolveObjectType(ast, objectName);
            if (type != null) {
                Location loc = searchFieldInType(type, fieldName, uri, content);
                if (loc != null) {
                    return loc;
                }
            }
            // 2. Module alias lookup
            if (docPath != null) {
                for (Node n : ast) {
                    if (n instanceof ImportExpression imp
                            && imp.getKind() == ImportExpression.ImportKind.MODULE
                            && objectName.equals(imp.getNamespace())) {
                        String raw = imp.getModule().replace("\"", "");
                        if (!raw.endsWith(".mira")) {
                            raw += ".mira";
                        }
                        Path modPath = docPath.getParent().resolve(raw).normalize();
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
        // 3. Fallback: scan all objects/structs in file
        return findFieldInAllNodes(ast, fieldName, uri, content);
    }

    // --- Type resolution ---
    private static Node resolveObjectType(List<Node> ast, String objectName) {
        for (Node n : ast) {
            Node t = resolveTypeInNode(n, objectName, ast);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Node resolveTypeInNode(Node n, String objectName, List<Node> ast) {
        if (n instanceof VarDecl vd && vd.getName().equals(objectName)) {
            Node init = vd.getInitializer();
            if (init instanceof ObjectExpression || init instanceof StructExpression) {
                return init;
            }
            if (init instanceof StructInitExpression si) {
                String templateName = extractName(si.getTarget());
                if (templateName != null) {
                    Node template = resolveObjectType(ast, templateName);
                    if (template != null) {
                        return template;
                    }
                }
            }
            return null;
        }
        if (n instanceof EnumDecl ed && ed.getIdentifier().equals(objectName)) {
            return ed;
        }
        if (n instanceof FuncDecl f) {
            for (Node b : f.getBody()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
        }
        if (n instanceof If s) {
            for (Node b : s.getThenBody()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
            if (s.getElseBody() != null) {
                for (Node b : s.getElseBody()) {
                    Node t = resolveTypeInNode(b, objectName, ast);
                    if (t != null) {
                        return t;
                    }
                }
            }
        }
        if (n instanceof For s) {
            for (Node b : s.getVarDecls()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
            for (Node b : s.getBody()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
        }
        if (n instanceof Foreach s) {
            Node t = resolveTypeInNode(s.getIterator(), objectName, ast);
            if (t != null) {
                return t;
            }
            for (Node b : s.getBody()) {
                t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
        }
        if (n instanceof While s) {
            for (Node b : s.getBody()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
        }
        if (n instanceof Block s) {
            for (Node b : s.getBody()) {
                Node t = resolveTypeInNode(b, objectName, ast);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
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

    // --- Field search in a concrete type ---
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

    // --- Fallback: scan all ObjectExpression + StructExpression in file ---
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

    // --- Variable/Function definition search ---
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
        if (n instanceof For stmt) {
            Location l = findInNodes(stmt.getVarDecls(), name, uri, content);
            if (l != null) {
                return l;
            }
            return findInNodes(stmt.getBody(), name, uri, content);
        }
        if (n instanceof Foreach stmt) {
            VarDecl iter = stmt.getIterator();
            if (iter.getName().equals(name)) {
                return locationForDecl(uri, content, iter.line, iter.nameColumn, iter.getName());
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

    // --- Module search ---
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

    // --- Location helpers ---
    private static Location locationForDecl(String uri, String content, int line, int nameColumn, String name) {
        int lspLine = Math.max(line - 1, 0);
        int col;
        if (nameColumn > 0) {
            col = nameColumn - 1;
        } else {
            col = 0;
            String[] lines = content.split("\n", -1);
            if (lspLine < lines.length) {
                int idx = lines[lspLine].indexOf(name);
                if (idx >= 0) {
                    col = idx;
                }
            }
        }
        return new Location(uri, new Range(
                new Position(lspLine, col),
                new Position(lspLine, col + name.length())));
    }

    private static Location locationFor(String uri, String content, int line, String name) {
        int lspLine = Math.max(line - 1, 0);
        String[] lines = content.split("\n", -1);
        int col = 0;
        if (lspLine < lines.length) {
            int idx = lines[lspLine].indexOf(name);
            if (idx >= 0) {
                col = idx;
            }
        }
        Range range = new Range(
                new Position(lspLine, col),
                new Position(lspLine, col + name.length()));
        return new Location(uri, range);
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
