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
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.statement.Statement;

public class DefinitionProvider {

    public static Location provide(List<Node> ast, String content, String docUri, Position pos) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }
        String stripped = word.startsWith("$") ? word.substring(1) : word;

        for (Node n : ast) {
            if (n instanceof Statement.FuncDecl f && f.getName().equals(stripped)) {
                return locationFor(docUri, content, f.line, f.getName());
            }
            if (n instanceof Statement.VarDecl v && v.getName().equals(stripped)) {
                return locationFor(docUri, content, v.line, v.getName());
            }
        }

        Path docPath = uriToPath(docUri);
        if (docPath == null) {
            return null;
        }

        for (Node n : ast) {
            if (!(n instanceof Expression.ImportExpression imp)) {
                continue;
            }
            if (imp.getKind() != Expression.ImportExpression.ImportKind.MODULE) {
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
            Location loc = searchInModule(modPath, stripped, alias, word);
            if (loc != null) {
                return loc;
            }
        }

        return null;
    }

    private static Location searchInModule(Path modPath, String name, String alias, String originalWord) {
        if (!Files.exists(modPath)) {
            return null;
        }
        try {
            String src = Files.readString(modPath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            for (Node n : modAst) {
                if (n instanceof Statement.FuncDecl f && f.getName().equals(name)) {
                    return locationFor(modPath.toUri().toString(), src, f.line, f.getName());
                }
                if (n instanceof Statement.VarDecl v && v.getName().equals(name)) {
                    return locationFor(modPath.toUri().toString(), src, v.line, v.getName());
                }
            }
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
