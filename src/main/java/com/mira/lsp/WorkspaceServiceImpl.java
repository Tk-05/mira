package com.mira.lsp;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceIndex workspaceIndex;
    private final DocumentService docService;
    private volatile Path workspaceRoot;

    public WorkspaceServiceImpl(WorkspaceIndex workspaceIndex, DocumentService docService) {
        this.workspaceIndex = workspaceIndex;
        this.docService = docService;
    }

    public void setWorkspaceRoot(Path root) {
        this.workspaceRoot = root;
        docService.setWorkspaceRoot(root);
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(
            WorkspaceSymbolParams params) {
        List<SymbolInformation> result = WorkspaceSymbolProvider.provide(params.getQuery(), workspaceRoot,
                workspaceIndex, docService.getOpenDocuments());
        return CompletableFuture.completedFuture(Either.forLeft(result));
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        for (FileEvent event : params.getChanges()) {
            Path path = uriToPath(event.getUri());
            if (path != null) {
                workspaceIndex.invalidate(path);
            }
            if (event.getType() == FileChangeType.Created || event.getType() == FileChangeType.Deleted) {
                workspaceIndex.invalidateFileList();
            }
        }
    }

    private static Path uriToPath(String uri) {
        try {
            return Paths.get(new URI(uri));
        } catch (Exception e) {
            return null;
        }
    }
}
