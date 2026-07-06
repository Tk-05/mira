package com.mira.lsp;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class LspServer implements LanguageServer, LanguageClientAware {

    private LanguageClient client;
    private final WorkspaceIndex workspaceIndex = new WorkspaceIndex();
    private final DocumentService docService = new DocumentService(this, workspaceIndex);
    private final WorkspaceServiceImpl workspaceService = new WorkspaceServiceImpl(workspaceIndex, docService);

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceService.setWorkspaceRoot(resolveWorkspaceRoot(params));

        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(TextDocumentSyncKind.Full);
        caps.setCompletionProvider(new CompletionOptions());
        caps.setDocumentFormattingProvider(true);
        caps.setHoverProvider(true);
        caps.setDefinitionProvider(true);
        caps.setDocumentSymbolProvider(true);
        caps.setReferencesProvider(true);
        RenameOptions renameOpts = new RenameOptions();
        renameOpts.setPrepareProvider(true);
        caps.setRenameProvider(renameOpts);
        caps.setWorkspaceSymbolProvider(true);
        SignatureHelpOptions sigOpts = new SignatureHelpOptions();
        sigOpts.setTriggerCharacters(List.of("(", ","));
        caps.setSignatureHelpProvider(sigOpts);
        caps.setCodeActionProvider(new CodeActionOptions(List.of(CodeActionKind.QuickFix)));
        SemanticTokensWithRegistrationOptions semTokenOpts = new SemanticTokensWithRegistrationOptions();
        semTokenOpts.setLegend(new SemanticTokensLegend(
                SemanticTokenProvider.TOKEN_TYPES,
                SemanticTokenProvider.TOKEN_MODIFIERS));
        semTokenOpts.setFull(true);
        caps.setSemanticTokensProvider(semTokenOpts);
        return CompletableFuture.<InitializeResult>completedFuture(new InitializeResult(caps));
    }

    private static Path resolveWorkspaceRoot(InitializeParams params) {
        List<WorkspaceFolder> folders = params.getWorkspaceFolders();
        if (folders != null && !folders.isEmpty()) {
            Path p = uriToPath(folders.get(0).getUri());
            if (p != null) {
                return p;
            }
        }
        String rootUri = params.getRootUri();
        if (rootUri != null) {
            Path p = uriToPath(rootUri);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static Path uriToPath(String uri) {
        try {
            return Paths.get(new URI(uri));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return docService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
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
