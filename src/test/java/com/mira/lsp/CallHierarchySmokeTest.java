package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CallHierarchySmokeTest {

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
    void callHierarchyThroughRealServer(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("smoke.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
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

        CallHierarchyPrepareParams prepareParams = new CallHierarchyPrepareParams(
                new TextDocumentIdentifier(uri), new Position(0, 4));
        List<CallHierarchyItem> items = server.getTextDocumentService()
                .prepareCallHierarchy(prepareParams).join();
        assertEquals(1, items.size());
        assertEquals("helper", items.get(0).getName());

        List<CallHierarchyIncomingCall> incoming = server.getTextDocumentService()
                .callHierarchyIncomingCalls(new CallHierarchyIncomingCallsParams(items.get(0))).join();
        assertEquals(1, incoming.size());
        assertEquals("main", incoming.get(0).getFrom().getName());

        CallHierarchyPrepareParams mainPrepare = new CallHierarchyPrepareParams(
                new TextDocumentIdentifier(uri), new Position(3, 4));
        List<CallHierarchyItem> mainItems = server.getTextDocumentService()
                .prepareCallHierarchy(mainPrepare).join();
        List<CallHierarchyOutgoingCall> outgoing = server.getTextDocumentService()
                .callHierarchyOutgoingCalls(new CallHierarchyOutgoingCallsParams(mainItems.get(0))).join();
        assertEquals(1, outgoing.size());
        assertEquals("helper", outgoing.get(0).getTo().getName());
    }
}
