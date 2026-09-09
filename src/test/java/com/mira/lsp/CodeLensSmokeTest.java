package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CodeLensSmokeTest {

    private static class NoopClient implements LanguageClient {

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }

    @Test
    void codeLensThroughRealServer(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("smoke.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
                test("sample", fn () {
                    assert(true);
                });
                """;
        Files.writeString(file, source);

        LspServer server = new LspServer();
        server.connect(new NoopClient());
        InitializeParams initParams = new InitializeParams();
        initParams.setWorkspaceFolders(List.of(new WorkspaceFolder(tempDir.toUri().toString())));
        server.initialize(initParams).join();

        String uri = file.toUri().toString();
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        CodeLensParams params = new CodeLensParams(new TextDocumentIdentifier(uri));
        List<? extends CodeLens> lenses = server.getTextDocumentService().codeLens(params).join();

        assertEquals(3, lenses.size());
        assertTrue(lenses.stream().anyMatch(l -> "1 reference".equals(l.getCommand().getTitle())));
        assertTrue(lenses.stream().anyMatch(l -> "0 references".equals(l.getCommand().getTitle())));
        assertTrue(lenses.stream().anyMatch(l -> "▶ Run Tests".equals(l.getCommand().getTitle())));
    }
}
