package com.mira.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement;

/**
 * Smart-expand-selection: word under cursor -> each enclosing statement/block
 * (reusing {@link DefinitionProvider}'s own scope-chain walk, the same one it
 * uses to resolve identifiers by lexical shadowing) -> whole document.
 */
public class SelectionRangeProvider {

    public static List<SelectionRange> provide(List<Node> ast, String content, List<Position> positions) {
        List<SelectionRange> result = new ArrayList<>();
        for (Position pos : positions) {
            result.add(provideOne(ast, content, pos));
        }
        return result;
    }

    private static SelectionRange provideOne(List<Node> ast, String content, Position pos) {
        List<Range> chain = new ArrayList<>();

        Range wordRange = HoverProvider.wordRangeAt(content, pos);
        if (wordRange != null) {
            chain.add(wordRange);
        }

        int cursorLine = pos.getLine() + 1;
        List<DefinitionProvider.Scope> scopes = DefinitionProvider.buildScopeChain(ast, cursorLine);

        if (!scopes.isEmpty()) {
            Statement leaf = directStatementAt(scopes.get(0).body(), cursorLine);
            if (leaf != null && leaf.line > 0 && leaf.endLine > 0) {
                chain.add(LspPositions.fullRange(content, leaf.line, leaf.endLine, null));
            }
        }

        for (DefinitionProvider.Scope scope : scopes) {
            if (scope.owner() instanceof Statement s && s.line > 0 && s.endLine > 0) {
                chain.add(LspPositions.fullRange(content, s.line, s.endLine, null));
            }
        }

        chain.add(fullDocumentRange(content));

        return buildChain(dedupe(chain));
    }

    private static Statement directStatementAt(List<Node> body, int cursorLine) {
        for (Node n : body) {
            if (n instanceof Statement s && s.line > 0 && s.endLine > 0 && cursorLine >= s.line
                    && cursorLine <= s.endLine) {
                return s;
            }
        }
        return null;
    }

    private static Range fullDocumentRange(String content) {
        String[] lines = content.split("\n", -1);
        int lastLine = Math.max(lines.length - 1, 0);
        int lastCol = lines.length > 0 ? lines[lines.length - 1].length() : 0;
        return new Range(new Position(0, 0), new Position(lastLine, lastCol));
    }

    private static List<Range> dedupe(List<Range> ranges) {
        List<Range> out = new ArrayList<>();
        for (Range r : ranges) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(r)) {
                out.add(r);
            }
        }
        return out;
    }

    private static SelectionRange buildChain(List<Range> rangesInnerToOuter) {
        SelectionRange parent = null;
        for (int i = rangesInnerToOuter.size() - 1; i >= 0; i--) {
            parent = new SelectionRange(rangesInnerToOuter.get(i), parent);
        }
        return parent;
    }
}
