package com.mira.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;

public final class DapLauncher {

    private DapLauncher() {
    }

    public static void launch() throws Exception {
        // Capture stdin before any other class (especially Internal's static
        // initializer)
        // can wrap or consume bytes from the DAP pipe.
        InputStream dapIn = System.in;

        // Redirect System.out → stderr so accidental prints don't corrupt the DAP
        // stream.
        PrintStream dapOut = System.out;
        System.setOut(System.err);

        // Replace System.in with an infinite-blocking stub so the Mira runtime's
        // stdin-reader thread (Internal.java static block) never touches the DAP pipe.
        System.setIn(new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ignored) {
                }
                return -1;
            }
        });

        DapServer server = new DapServer();
        var launcher = DSPLauncher.createServerLauncher(server, dapIn, dapOut);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
