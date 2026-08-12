package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import com.mira.parser.nodes.Node;

public class RenameProvider {

    public static Range prepareRename(List<Node> ast, String content, Position pos, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }
        List<Location> refs = ReferenceProvider.provide(ast, content, pos, uri, docPath,
                workspaceIndex, workspaceRoot, openDocumentsByUri, true);
        if (refs.isEmpty()) {
            return null;
        }
        return HoverProvider.wordRangeAt(content, pos);
    }

    public static WorkspaceEdit rename(List<Node> ast, String content, Position pos, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri,
            String newName) {
        List<Location> refs = ReferenceProvider.provide(ast, content, pos, uri, docPath,
                workspaceIndex, workspaceRoot, openDocumentsByUri, true);
        if (refs.isEmpty()) {
            return null;
        }
        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (Location loc : refs) {
            changes.computeIfAbsent(loc.getUri(), k -> new ArrayList<>())
                    .add(new TextEdit(loc.getRange(), newName));
        }
        return new WorkspaceEdit(changes);
    }
}
