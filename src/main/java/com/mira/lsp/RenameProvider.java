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
import com.mira.vocabulary.Vocabulary;

public class RenameProvider {

    public static final class RenameRejectedException extends RuntimeException {

        public RenameRejectedException(String message) {
            super(message);
        }
    }

    public static Range prepareRename(List<Node> ast, String content, Position pos, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        String word = HoverProvider.wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }
        List<Location> refs = ReferenceProvider.provide(ast, content, pos, uri, docPath, workspaceIndex, workspaceRoot,
                openDocumentsByUri, true);
        if (refs.isEmpty()) {
            return null;
        }
        return HoverProvider.wordRangeAt(content, pos);
    }

    public static WorkspaceEdit rename(List<Node> ast, String content, Position pos, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri, String newName) {
        String identifierError = identifierError(newName);
        if (identifierError != null) {
            throw new RenameRejectedException(identifierError);
        }
        String collision = collisionReason(ast, content, pos, uri, newName);
        if (collision != null) {
            throw new RenameRejectedException(collision);
        }
        List<Location> refs = ReferenceProvider.provide(ast, content, pos, uri, docPath, workspaceIndex, workspaceRoot,
                openDocumentsByUri, true);
        if (refs.isEmpty()) {
            throw new RenameRejectedException("Cannot find any references to rename here.");
        }
        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (Location loc : refs) {
            changes.computeIfAbsent(loc.getUri(), k -> new ArrayList<>()).add(new TextEdit(loc.getRange(), newName));
        }
        return new WorkspaceEdit(changes);
    }

    private static String collisionReason(List<Node> ast, String content, Position pos, String uri, String newName) {
        String oldName = HoverProvider.wordAt(content, pos);
        if (newName.equals(oldName)) {
            return null;
        }
        int cursorLine = pos.getLine() + 1;
        if (HoverProvider.isFieldAccess(content, pos)) {
            String objectName = DefinitionProvider.objectBefore(content, pos);
            if (objectName == null) {
                return null;
            }
            Node type = DefinitionProvider.resolveObjectType(ast, objectName, cursorLine);
            if (type != null && DefinitionProvider.typeHasMember(type, newName)) {
                return "'" + newName + "' already exists on this type.";
            }
            return null;
        }
        if (DefinitionProvider.findScoped(ast, newName, uri, content, cursorLine) != null) {
            return "'" + newName + "' is already declared in this scope.";
        }
        return null;
    }

    private static String identifierError(String name) {
        if (name == null || name.isEmpty()) {
            return "New name must not be empty.";
        }
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return "'" + name + "' is not a valid name: it must start with a letter or '_'.";
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return "'" + name + "' is not a valid name: only letters, digits, and '_' are allowed.";
            }
        }
        if (Vocabulary.stringIsKeyword(name)) {
            return "'" + name + "' is a reserved keyword and cannot be used as a name.";
        }
        return null;
    }
}
