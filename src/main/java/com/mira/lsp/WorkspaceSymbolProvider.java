package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SymbolInformation;

import com.mira.parser.nodes.Node;

public class WorkspaceSymbolProvider {

    public static List<SymbolInformation> provide(String query, Path workspaceRoot,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        List<SymbolInformation> result = new ArrayList<>();
        if (workspaceRoot == null) {
            return result;
        }
        String q = query == null ? "" : query.toLowerCase();
        for (Path file : workspaceIndex.allMiraFiles(workspaceRoot)) {
            List<Node> ast = workspaceIndex.getAst(file, openDocumentsByUri);
            String content = workspaceIndex.getSource(file, openDocumentsByUri);
            String uri = file.toUri().toString();
            List<SymbolInformation> fileSymbols = new ArrayList<>();
            DocumentSymbolProvider.collectFlat(ast, uri, content, fileSymbols);
            for (SymbolInformation si : fileSymbols) {
                if (q.isEmpty() || si.getName().toLowerCase().contains(q)) {
                    result.add(si);
                }
            }
        }
        return result;
    }
}
