package com.mira.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Drives inlayHint through the real
 * {@link LspServer} -> {@link DocumentService} wiring (didOpen -> request),
 * matching HoverCompletionSmokeTest's rationale: catches wiring bugs a direct
 * provider call can't see.
 */
public class InlayHintSmokeTest {

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
    void inlayHintsThroughRealServer() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        String uri = "file:///smoke.mira";
        String source = "var x : 5;\n";
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        InlayHintParams params = new InlayHintParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setRange(new Range(new Position(0, 0), new Position(0, 99)));
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(params).join();

        assertEquals(1, hints.size());
        assertEquals(": Number", hints.get(0).getLabel().getLeft());
    }
}
