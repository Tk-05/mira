package com.mira.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class LspServer implements LanguageServer, LanguageClientAware {

    private LanguageClient client;
    private final DocumentService docService = new DocumentService(this);

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(TextDocumentSyncKind.Full);
        caps.setCompletionProvider(new CompletionOptions());
        caps.setDocumentFormattingProvider(true);
        return CompletableFuture.<InitializeResult>completedFuture(new InitializeResult(caps));
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return docService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new NoOpWorkspace();
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.<Object>completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    public void publishDiagnostics(String uri, List<Diagnostic> diags) {
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diags));
        }
    }
}
