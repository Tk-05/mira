package com.mira.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class SelectionRangeSmokeTest {

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
    void selectionRangeThroughRealServer() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        String uri = "file:///smoke.mira";
        String source = """
                fn main() {
                    var x : 1;
                }
                """;
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        SelectionRangeParams params = new SelectionRangeParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPositions(List.of(new Position(1, 8)));

        List<SelectionRange> result = server.getTextDocumentService().selectionRange(params).join();
        assertEquals(1, result.size());
        SelectionRange sr = result.get(0);
        assertNotNull(sr.getRange());
        assertNotNull(sr.getParent());
    }
}
