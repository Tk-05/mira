package com.mira.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

public class LspServerTest {

    private static class RecordingClient implements LanguageClient {
        final AtomicReference<RegistrationParams> registered = new AtomicReference<>();

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            registered.set(params);
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

    private static InitializeParams paramsWithWatcherSupport(boolean dynamicRegistration) {
        DidChangeWatchedFilesCapabilities watchedFiles = new DidChangeWatchedFilesCapabilities();
        watchedFiles.setDynamicRegistration(dynamicRegistration);
        WorkspaceClientCapabilities workspace = new WorkspaceClientCapabilities();
        workspace.setDidChangeWatchedFiles(watchedFiles);
        ClientCapabilities caps = new ClientCapabilities();
        caps.setWorkspace(workspace);
        InitializeParams params = new InitializeParams();
        params.setCapabilities(caps);
        return params;
    }

    @Test
    void registersFileWatcherWhenClientSupportsDynamicRegistration() {
        LspServer server = new LspServer();
        RecordingClient client = new RecordingClient();
        server.connect(client);

        server.initialize(paramsWithWatcherSupport(true)).join();

        RegistrationParams registration = client.registered.get();
        assertEquals(1, registration.getRegistrations().size());
        Registration reg = registration.getRegistrations().get(0);
        assertEquals("workspace/didChangeWatchedFiles", reg.getMethod());
        var options = (DidChangeWatchedFilesRegistrationOptions) reg.getRegisterOptions();
        List<FileSystemWatcher> watchers = options.getWatchers();
        assertEquals(1, watchers.size());
        assertEquals("**/*.mira", watchers.get(0).getGlobPattern().getLeft());
    }

    @Test
    void doesNotRegisterFileWatcherWithoutDynamicRegistrationSupport() {
        LspServer server = new LspServer();
        RecordingClient client = new RecordingClient();
        server.connect(client);

        server.initialize(paramsWithWatcherSupport(false)).join();

        assertNull(client.registered.get());
    }
}
