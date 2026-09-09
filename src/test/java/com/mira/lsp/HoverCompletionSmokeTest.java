package com.mira.lsp;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Drives hover/completion through the real {@link LspServer} ->
 * {@link DocumentService} wiring (didOpen -> hover/completion), unlike
 * HoverProviderTest/CompletionProviderTest which call the providers directly -
 * catches wiring bugs (wrong params threaded through DocumentService,
 * server-level exceptions) that a direct provider call can't see.
 */
public class HoverCompletionSmokeTest {

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
    void smokeHoverAndCompletionThroughRealServer() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        String uri = "file:///smoke.mira";
        String source = """
                var Point : struct { var x; var y; };
                fn main() {
                    var p : Point{};
                    return p.x;
                }
                """;

        TextDocumentItem doc = new TextDocumentItem(uri, "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        // hover on "x" in "return p.x;" (line index 3, char 12)
        HoverParams hp = new HoverParams();
        hp.setTextDocument(new TextDocumentIdentifier(uri));
        hp.setPosition(new Position(3, 13));
        Hover hover = server.getTextDocumentService().hover(hp).join();
        assertNotNull(hover, "hover through the real server returned null");
        assertTrue(hover.getContents().getRight().getValue().contains("var x"));

        // completion on "x" in "return p.x;" - dot-context after "p."
        CompletionParams cp = new CompletionParams();
        cp.setTextDocument(new TextDocumentIdentifier(uri));
        cp.setPosition(new Position(3, 13));
        Either<List<CompletionItem>, ?> completion = server.getTextDocumentService().completion(cp).join();
        List<CompletionItem> items = completion.getLeft();
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("x")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("y")));
    }

    /**
     * Exact real-world case that was reported broken: hovering the alias in
     * {@code import native "...raylib.jar" as ray;} - uses the actual checked-in
     * raylib fixture jar, not a synthetic one.
     */
    @Test
    void hoversNativeImportAliasAgainstRealRaylibJar() {
        LspServer server = new LspServer();
        server.connect(new NoopClient());

        Path realDocPath = java.nio.file.Paths.get("src/main/resources/demo/Debug.mira").toAbsolutePath();
        String source = "import native \"../../../../extern/raylib/target/raylib.jar\" as ray;\n";

        TextDocumentItem doc = new TextDocumentItem(realDocPath.toUri().toString(), "mira", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));

        int rayCol = source.indexOf("as ray") + 3;
        HoverParams hp = new HoverParams();
        hp.setTextDocument(new TextDocumentIdentifier(realDocPath.toUri().toString()));
        hp.setPosition(new Position(0, rayCol));
        Hover hover = server.getTextDocumentService().hover(hp).join();

        assertNotNull(hover, "hovering the native import alias returned null");
        String text = hover.getContents().getRight().getValue();
        assertTrue(text.contains("import native"), text);
        assertTrue(text.contains("native library"), text);
    }
}
