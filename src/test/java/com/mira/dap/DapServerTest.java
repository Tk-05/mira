package com.mira.dap;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DapServerTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private static class LatchClient implements IDebugProtocolClient {

        final CountDownLatch terminatedLatch = new CountDownLatch(1);
        final StringBuilder stderr = new StringBuilder();
        final StringBuilder stdout = new StringBuilder();

        @Override
        public void output(OutputEventArguments args) {
            if ("stderr".equals(args.getCategory())) {
                stderr.append(args.getOutput());
            } else if ("stdout".equals(args.getCategory())) {
                stdout.append(args.getOutput());
            }
        }

        @Override
        public void terminated(TerminatedEventArguments args) {
            terminatedLatch.countDown();
        }
    }

    private static DapServer launchAndRun(Path programFile, LatchClient client) throws Exception {
        DapServer server = new DapServer();
        server.connect(client);
        server.initialize(new InitializeRequestArguments()).join();
        server.launch(Map.of("program", programFile.toString())).join();
        server.configurationDone(new ConfigurationDoneArguments()).join();
        server.threads().join();
        assertTrue(client.terminatedLatch.await(10, TimeUnit.SECONDS), "debug session should terminate");
        return server;
    }

    @Test
    void debuggingScriptWithoutMainFunctionDoesNotFail(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("script.mira");
        Files.writeString(file, "println(\"hello\");\n");

        LatchClient client = new LatchClient();
        launchAndRun(file, client);

        assertTrue(client.stderr.isEmpty(), "expected no error output, got: " + client.stderr);
        assertTrue(client.stdout.toString().contains("hello"), "expected script output, got: " + client.stdout);
    }

    @Test
    void debuggingProgramWithMainFunctionStillCallsIt(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("program.mira");
        Files.writeString(file, """
                fn main() {
                    println("from main");
                }
                """);

        LatchClient client = new LatchClient();
        launchAndRun(file, client);

        assertTrue(client.stderr.isEmpty(), "expected no error output, got: " + client.stderr);
        assertTrue(client.stdout.toString().contains("from main"), "expected main() to run, got: " + client.stdout);
    }
}
