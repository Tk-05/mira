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
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;

public class DapServerTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
        // configurationDone() sets this global static flag whenever the debugged
        // program has its own main() (see DapServer.java); left set, it leaks into
        // whatever test runs next in the same fork and makes it call a nonexistent
        // "main" - unrelated tests then fail with an unexplained ThrowSignal/
        // UndefinedReferenceError. Not new: any test touching Flags.mainFunction
        // needs to reset it, this class just didn't yet.
        Flags.mainFunction = false;
    }

    private static class LatchClient implements IDebugProtocolClient {

        final CountDownLatch terminatedLatch = new CountDownLatch(1);
        final CountDownLatch stoppedLatch = new CountDownLatch(1);
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
        public void stopped(StoppedEventArguments args) {
            stoppedLatch.countDown();
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

    /**
     * Regression test for the resolver/slot-based-Environment architecture (see
     * Resolver.java): a breakpoint inside a block nested in a for-loop nested in a
     * function exercises a slot-mode Environment several scopes deep, and the DAP
     * "Locals" view (DapServer#variables, backed by Environment#getLocalValues())
     * must still report exactly that innermost scope's own bindings by name and
     * value - proving the slot-array storage introduced in Phase 3 didn't change
     * what the debugger sees.
     */
    @Test
    void variablesInspectionWorksForSlotModeNestedBlockScope(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("nested.mira");
        Files.writeString(file, """
                fn main() {
                    var outer : 1;
                    for (var i : 0; i < 3; i++) {
                        {
                            var inner : i * 10;
                            println(inner);
                        }
                    }
                }

                main();
                """);

        LatchClient client = new LatchClient();
        DapServer server = new DapServer();
        server.connect(client);
        server.initialize(new InitializeRequestArguments()).join();
        server.launch(Map.of("program", file.toString())).join();

        Source source = new Source();
        source.setPath(file.toString());
        SourceBreakpoint bp = new SourceBreakpoint();
        bp.setLine(6);
        SetBreakpointsArguments bpArgs = new SetBreakpointsArguments();
        bpArgs.setSource(source);
        bpArgs.setBreakpoints(new SourceBreakpoint[]{bp});
        server.setBreakpoints(bpArgs).join();

        server.configurationDone(new ConfigurationDoneArguments()).join();
        server.threads().join();
        assertTrue(client.stoppedLatch.await(10, TimeUnit.SECONDS), "should hit the breakpoint");

        StackTraceResponse trace = server.stackTrace(new StackTraceArguments()).join();
        assertTrue(trace.getStackFrames().length >= 1, "expected at least one stack frame while paused");

        var variables = server.variables(new VariablesArguments()).join().getVariables();
        assertEquals(1, variables.length, "the innermost block scope should report only its own local (`inner`), "
                + "not the enclosing loop's `i` or the function's `outer`");
        Variable inner = variables[0];
        assertEquals("inner", inner.getName());
        assertEquals("0", inner.getValue(), "first iteration: i == 0, so inner == i * 10 == 0");

        // Clear the breakpoint before resuming so the loop's remaining iterations
        // run to completion instead of pausing again (and to avoid ever calling
        // disconnect()/terminate(), which System.exit()s the whole JVM).
        SetBreakpointsArguments clearArgs = new SetBreakpointsArguments();
        clearArgs.setSource(source);
        clearArgs.setBreakpoints(new SourceBreakpoint[0]);
        server.setBreakpoints(clearArgs).join();

        server.continue_(new org.eclipse.lsp4j.debug.ContinueArguments()).join();
        assertTrue(client.terminatedLatch.await(10, TimeUnit.SECONDS), "debug session should terminate");
        assertTrue(client.stderr.isEmpty(), "expected no error output, got: " + client.stderr);
    }
}
