package com.mira.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class DocumentServiceDebounceTest {

    private static class RecordingClient implements LanguageClient {

        final AtomicInteger publishCount = new AtomicInteger();
        final List<List<Diagnostic>> published = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            publishCount.incrementAndGet();
            published.add(diagnostics.getDiagnostics());
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

    private static void change(LspServer server, String uri, int version, String newText) {
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, version), List.of(new TextDocumentContentChangeEvent(newText))));
    }

    @Test
    void rapidChangesCollapseIntoOneReanalysis() throws InterruptedException {
        LspServer server = new LspServer();
        RecordingClient client = new RecordingClient();
        server.connect(client);

        String uri = "file:///debounce.mira";
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, "var x : 1;\n");
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));
        Thread.sleep(200);
        int afterOpen = client.publishCount.get();

        // 10 edits fired 20ms apart (well under the debounce window) must each
        // cancel the previous pending run, not each trigger their own.
        for (int i = 0; i < 10; i++) {
            change(server, uri, i + 2, "var x : " + i + ";\n");
            Thread.sleep(20);
        }
        Thread.sleep(600); // past the 300ms debounce window from the last edit

        assertEquals(afterOpen + 1, client.publishCount.get(),
                "10 rapid edits should collapse into exactly one reanalysis, not one per keystroke");
    }

    @Test
    void changeEventuallyPublishesUpdatedDiagnostics() throws InterruptedException {
        LspServer server = new LspServer();
        RecordingClient client = new RecordingClient();
        server.connect(client);

        String uri = "file:///debounce2.mira";
        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1,
                "module main;\nfn main() {\n    return 0;\n}\n");
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));
        Thread.sleep(200);
        client.published.clear();

        change(server, uri, 2, "module main;\nfn main() {\n    var unused : 1;\n    return 0;\n}\n");
        Thread.sleep(600);

        assertFalse(client.published.isEmpty(), "debounced diagnostics should still eventually publish");
        List<Diagnostic> last = client.published.get(client.published.size() - 1);
        assertTrue(last.stream().anyMatch(d -> d.getMessage().contains("unused")),
                "expected an unused-variable diagnostic after the debounce settles, got: " + last);
    }
}
