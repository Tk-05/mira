package com.mira.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;

public class Launcher {

    public static void launch() throws Exception {
        LspServer server = new LspServer();
        var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
