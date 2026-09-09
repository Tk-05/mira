package com.mira.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Drives semanticTokens/full and semanticTokens/range through the real
 * {@link LspServer} -> {@link DocumentService} wiring (didOpen -> request),
 * matching HoverCompletionSmokeTest's rationale: catches wiring bugs a direct
 * provider call can't see.
 */
public class SemanticTokenSmokeTest {

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
    void semanticTokensFullAndRangeThroughRealServer() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        String uri = "file:///smoke.mira";
        String source = """
                var a : 1;
                var b : 2;
                """;
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        SemanticTokensParams fullParams = new SemanticTokensParams(new TextDocumentIdentifier(uri));
        SemanticTokens full = server.getTextDocumentService().semanticTokensFull(fullParams).join();
        assertEquals(2, full.getData().size() / 5);

        SemanticTokensRangeParams rangeParams = new SemanticTokensRangeParams();
        rangeParams.setTextDocument(new TextDocumentIdentifier(uri));
        rangeParams.setRange(new Range(new Position(0, 0), new Position(0, 99)));
        SemanticTokens ranged = server.getTextDocumentService().semanticTokensRange(rangeParams).join();
        assertEquals(1, ranged.getData().size() / 5);
    }
}
