package com.mira.lsp;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import com.mira.parser.nodes.Node;

/**
 * All occurrences of the symbol under the cursor, scoped to the current file
 * only. Built directly on {@link ReferenceProvider}'s own logic - passing
 * {@code workspaceRoot=null} short-circuits its cross-file search (see
 * {@code ReferenceProvider.provide}'s guard on {@code workspaceRoot != null}),
 * leaving exactly the same-file matches this needs - so declaration-finding and
 * field-vs-plain-identifier detection stay in exactly one place rather than
 * being reimplemented here.
 */
public class DocumentHighlightProvider {

    public static List<DocumentHighlight> provide(List<Node> ast, String content, Position pos, String uri) {
        List<Location> locations = ReferenceProvider.provide(ast, content, pos, uri,
                null, null, null, Map.of(), true);
        return locations.stream()
                .map(loc -> new DocumentHighlight(loc.getRange(), DocumentHighlightKind.Text))
                .toList();
    }
}
