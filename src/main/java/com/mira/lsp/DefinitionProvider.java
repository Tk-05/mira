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
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
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
            return findFieldDefinition(ast, stripped, docUri, content);
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
            Location found = searchInModule(modPath, stripped);
            if (found != null) {
                return found;
            }
        }
        return null;
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
                return locationFor(uri, content, f.line, f.getName());
            }
            for (Parameter p : f.getParameters()) {
                if (p.name().equals(name)) {
                    return locationFor(uri, content, f.line, p.name());
                }
            }
            return findInNodes(f.getBody(), name, uri, content);
        }
        if (n instanceof VarDecl v && v.getName().equals(name)) {
            return locationFor(uri, content, v.line, v.getName());
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
                return locationFor(uri, content, iter.line, iter.getName());
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

    private static Location findFieldDefinition(List<Node> ast, String fieldName, String uri, String content) {
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
                    return locationFor(uri, content, f.line, f.getName());
                }
            }
            for (FuncDecl m : obj.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    return locationFor(uri, content, m.line, m.getName());
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

    private static Location searchInModule(Path modPath, String name) {
        if (!Files.exists(modPath)) {
            return null;
        }
        try {
            String src = Files.readString(modPath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            return findInNodes(modAst, name, modPath.toUri().toString(), src);
        } catch (Exception ignored) {
        }
        return null;
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
