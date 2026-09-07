package com.mira.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Drives documentHighlight/foldingRange through the real {@link LspServer} ->
 * {@link DocumentService} wiring (didOpen -> request), matching
 * HoverCompletionSmokeTest's rationale: catches wiring bugs a direct provider
 * call can't see.
 */
public class HighlightFoldingSmokeTest {

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
    void highlightsAndFoldsThroughRealServer() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        String uri = "file:///smoke.mira";
        String source = """
                fn add(a, b) {
                    var sum : a + b;
                    return sum;
                }
                """;
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        DocumentHighlightParams hp = new DocumentHighlightParams();
        hp.setTextDocument(new TextDocumentIdentifier(uri));
        hp.setPosition(new Position(1, 9)); // "sum" in "var sum : a + b;"
        List<? extends DocumentHighlight> highlights = server.getTextDocumentService()
                .documentHighlight(hp).join();
        assertEquals(2, highlights.size());

        FoldingRangeRequestParams fp = new FoldingRangeRequestParams();
        fp.setTextDocument(new TextDocumentIdentifier(uri));
        List<FoldingRange> ranges = server.getTextDocumentService().foldingRange(fp).join();
        assertEquals(1, ranges.size());
        assertTrue(ranges.get(0).getStartLine() == 0 && ranges.get(0).getEndLine() == 3);
    }
}
