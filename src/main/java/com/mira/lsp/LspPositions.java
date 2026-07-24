package com.mira.lsp;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class LspPositions {

    private LspPositions() {
    }

    public static Range nameRange(String content, int line, int nameColumn, String name) {
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
        return new Range(new Position(lspLine, col), new Position(lspLine, col + name.length()));
    }

    public static Range fullRange(String content, int line, int endLine, Range mustContain) {
        int lspLine = Math.max(line - 1, 0);
        int lspEndLine = Math.max(endLine - 1, lspLine);
        int startLine = lspLine;
        if (mustContain != null) {
            startLine = Math.min(startLine, mustContain.getStart().getLine());
            lspEndLine = Math.max(lspEndLine, mustContain.getEnd().getLine());
        }
        String[] lines = content.split("\n", -1);
        int endCol = 0;
        if (lspEndLine < lines.length) {
            endCol = lines[lspEndLine].length();
        }
        if (mustContain != null && lspEndLine == mustContain.getEnd().getLine()) {
            endCol = Math.max(endCol, mustContain.getEnd().getCharacter());
        }
        return new Range(new Position(startLine, 0), new Position(lspEndLine, endCol));
    }
}
