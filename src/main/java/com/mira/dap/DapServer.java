package com.mira.dap;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointLocationsArguments;
import org.eclipse.lsp4j.debug.BreakpointLocationsResponse;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CompletionsArguments;
import org.eclipse.lsp4j.debug.CompletionsResponse;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.DataBreakpointInfoArguments;
import org.eclipse.lsp4j.debug.DataBreakpointInfoResponse;
import org.eclipse.lsp4j.debug.DisassembleArguments;
import org.eclipse.lsp4j.debug.DisassembleResponse;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.ExceptionInfoArguments;
import org.eclipse.lsp4j.debug.ExceptionInfoResponse;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.GotoArguments;
import org.eclipse.lsp4j.debug.GotoTargetsArguments;
import org.eclipse.lsp4j.debug.GotoTargetsResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.LoadedSourcesArguments;
import org.eclipse.lsp4j.debug.LoadedSourcesResponse;
import org.eclipse.lsp4j.debug.ModulesArguments;
import org.eclipse.lsp4j.debug.ModulesResponse;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.ReadMemoryArguments;
import org.eclipse.lsp4j.debug.ReadMemoryResponse;
import org.eclipse.lsp4j.debug.RestartArguments;
import org.eclipse.lsp4j.debug.RestartFrameArguments;
import org.eclipse.lsp4j.debug.ReverseContinueArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetDataBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetDataBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetExpressionArguments;
import org.eclipse.lsp4j.debug.SetExpressionResponse;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceArguments;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.SourceResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StepBackArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepInTargetsArguments;
import org.eclipse.lsp4j.debug.StepInTargetsResponse;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminateThreadsArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.WriteMemoryArguments;
import org.eclipse.lsp4j.debug.WriteMemoryResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import com.mira.cli.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.utils.FileLoader;

public class DapServer implements IDebugProtocolServer {

    private static final int THREAD_ID = 1;

    private IDebugProtocolClient client;
    private Interpreter interpreter;
    private List<Node> asts;
    private volatile Path programPath;
    private volatile boolean stepMode = false;
    private volatile Environment pausedEnv;
    private volatile int pausedLine = 0;

    private final Map<Path, Set<Integer>> breakpointLines = new ConcurrentHashMap<>();
    private final BlockingQueue<String> resumeQueue = new LinkedBlockingQueue<>(1);
    private volatile int stepOverDepth = -1;
    private final java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);

    public void connect(IDebugProtocolClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        Capabilities caps = new Capabilities();
        caps.setSupportsConfigurationDoneRequest(true);
        caps.setSupportsTerminateRequest(true);
        return CompletableFuture.completedFuture(caps);
    }

    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        Object programObj = args.get("program");
        String program = programObj instanceof String s ? s : "";
        try {
            String source = FileLoader.readFileFromPath(program);
            programPath = Paths.get(program).toAbsolutePath().normalize();
            Flags.inputPath.set(programPath);
            Flags.fileName.set(programPath.getFileName().toString());
            Flags.sourceLines.set(source.split("\n", -1));
            asts = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        } catch (Exception e) {
            sendOutput("stderr", "Failed to load '" + program + "': " + e.getMessage() + "\n");
        }
        client.initialized();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> attach(Map<String, Object> args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        Path path = Paths.get(args.getSource().getPath()).toAbsolutePath().normalize();
        Set<Integer> lines = ConcurrentHashMap.newKeySet();
        List<Breakpoint> verified = new ArrayList<>();
        if (args.getBreakpoints() != null) {
            for (SourceBreakpoint sb : args.getBreakpoints()) {
                lines.add(sb.getLine());
                Breakpoint bp = new Breakpoint();
                bp.setVerified(true);
                bp.setLine(sb.getLine());
                verified.add(bp);
            }
        }
        breakpointLines.put(path, lines);
        SetBreakpointsResponse resp = new SetBreakpointsResponse();
        resp.setBreakpoints(verified.toArray(new Breakpoint[0]));
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        if (asts == null) {
            client.terminated(new TerminatedEventArguments());
            return CompletableFuture.completedFuture(null);
        }
        System.setOut(new PrintStream(new OutputStream() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public void write(int b) {
                buf.append((char) b);
                if (b == '\n') {
                    flush();
                }
            }

            @Override
            public void flush() {
                if (!buf.isEmpty()) {
                    sendOutput("stdout", buf.toString());
                    buf.setLength(0);
                }
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        }, true));

        Thread t = new Thread(() -> {
            try {
                startLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Flags.inputPath.set(programPath);
            interpreter = new Interpreter();
            interpreter.setDebugHook((line, env) -> {
                Set<Integer> bps = programPath != null
                        ? breakpointLines.getOrDefault(programPath, Collections.emptySet())
                        : Collections.emptySet();

                boolean stepHit = stepMode && (stepOverDepth < 0 || interpreter.getCallStack().size() <= stepOverDepth);
                boolean hit = stepHit || bps.contains(line);
                if (!hit) {
                    return;
                }

                String reason = stepMode ? "step" : "breakpoint";
                stepMode = false;
                stepOverDepth = -1;
                pausedEnv = env;
                pausedLine = line;

                StoppedEventArguments stopped = new StoppedEventArguments();
                stopped.setReason(reason);
                stopped.setThreadId(THREAD_ID);
                stopped.setAllThreadsStopped(true);
                sendOutput("console", "Paused at line " + line + " (" + reason + ")\n");
                client.stopped(stopped);

                try {
                    String action = resumeQueue.take();
                    if ("step".equals(action) || "stepin".equals(action)) {
                        stepMode = true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Flags.mainFunction = asts.stream().anyMatch(n -> n instanceof FuncDecl fd && "main".equals(fd.getName()));
            try {
                interpreter.run(asts, Flags.args, true);
                sendOutput("console", "Execution finished.\n");
            } catch (Throwable e) {
                sendOutput("stderr", e.getClass().getSimpleName() + ": " + e.getMessage() + "\n");
            } finally {
                System.out.flush();
                ExitedEventArguments exited = new ExitedEventArguments();
                exited.setExitCode(0);
                client.exited(exited);
                client.terminated(new TerminatedEventArguments());
            }
        });
        t.setDaemon(true);
        t.setName("mira-execution");
        t.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        resumeQueue.offer("continue");
        ContinueResponse resp = new ContinueResponse();
        resp.setAllThreadsContinued(true);
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<Void> next(NextArguments args) {
        stepOverDepth = interpreter != null ? interpreter.getCallStack().size() : -1;
        resumeQueue.offer("step");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stepIn(StepInArguments args) {
        stepOverDepth = -1;
        resumeQueue.offer("stepin");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stepOut(StepOutArguments args) {
        resumeQueue.offer("continue");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        org.eclipse.lsp4j.debug.Thread t = new org.eclipse.lsp4j.debug.Thread();
        t.setId(THREAD_ID);
        t.setName("main");
        ThreadsResponse resp = new ThreadsResponse();
        resp.setThreads(new org.eclipse.lsp4j.debug.Thread[]{t});
        if (startLatch.getCount() > 0) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                startLatch.countDown();
            });
        }
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        List<StackFrame> frames = new ArrayList<>();
        Source src = currentSource();
        if (interpreter != null) {
            List<Interpreter.StackFrame> stack = interpreter.getCallStack();
            for (int i = 0; i < stack.size(); i++) {
                Interpreter.StackFrame mf = stack.get(i);
                StackFrame f = new StackFrame();
                f.setId(i);
                f.setName(mf.name());
                f.setLine(i == 0 ? pausedLine : mf.line());
                f.setColumn(1);
                f.setSource(src);
                frames.add(f);
            }
        }
        if (frames.isEmpty()) {
            StackFrame f = new StackFrame();
            f.setId(0);
            f.setName("<top level>");
            f.setLine(pausedLine);
            f.setColumn(1);
            f.setSource(src);
            frames.add(f);
        }
        StackTraceResponse resp = new StackTraceResponse();
        resp.setStackFrames(frames.toArray(new StackFrame[0]));
        resp.setTotalFrames(frames.size());
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        Scope scope = new Scope();
        scope.setName("Locals");
        scope.setVariablesReference(1);
        scope.setExpensive(false);
        ScopesResponse resp = new ScopesResponse();
        resp.setScopes(new Scope[]{scope});
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        List<Variable> vars = new ArrayList<>();
        if (pausedEnv != null) {
            for (Map.Entry<String, Object> entry : pausedEnv.getLocalValues().entrySet()) {
                Variable v = new Variable();
                v.setName(entry.getKey());
                v.setValue(String.valueOf(entry.getValue()));
                v.setVariablesReference(0);
                v.setType(entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
                vars.add(v);
            }
        }
        VariablesResponse resp = new VariablesResponse();
        resp.setVariables(vars.toArray(new Variable[0]));
        return CompletableFuture.completedFuture(resp);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        resumeQueue.offer("disconnect");
        scheduleShutdown();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> terminate(TerminateArguments args) {
        resumeQueue.offer("disconnect");
        scheduleShutdown();
        return CompletableFuture.completedFuture(null);
    }

    private void scheduleShutdown() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        });
        t.setDaemon(true);
        t.start();
    }

    private Source currentSource() {
        if (programPath == null) {
            return null;
        }
        Source src = new Source();
        src.setPath(programPath.toString());
        src.setName(Flags.fileName.get());
        src.setSourceReference(0);
        return src;
    }

    private void sendOutput(String category, String output) {
        OutputEventArguments evt = new OutputEventArguments();
        evt.setCategory(category);
        evt.setOutput(output);
        client.output(evt);
    }

    @Override
    public CompletableFuture<Void> restart(RestartArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetFunctionBreakpointsResponse> setFunctionBreakpoints(
            SetFunctionBreakpointsArguments args) {
        return CompletableFuture.completedFuture(new SetFunctionBreakpointsResponse());
    }

    @Override
    public CompletableFuture<SetExceptionBreakpointsResponse> setExceptionBreakpoints(
            SetExceptionBreakpointsArguments args) {
        return CompletableFuture.completedFuture(new SetExceptionBreakpointsResponse());
    }

    @Override
    public CompletableFuture<DataBreakpointInfoResponse> dataBreakpointInfo(DataBreakpointInfoArguments args) {
        return CompletableFuture.completedFuture(new DataBreakpointInfoResponse());
    }

    @Override
    public CompletableFuture<SetDataBreakpointsResponse> setDataBreakpoints(SetDataBreakpointsArguments args) {
        return CompletableFuture.completedFuture(new SetDataBreakpointsResponse());
    }

    @Override
    public CompletableFuture<SetInstructionBreakpointsResponse> setInstructionBreakpoints(
            SetInstructionBreakpointsArguments args) {
        return CompletableFuture.completedFuture(new SetInstructionBreakpointsResponse());
    }

    @Override
    public CompletableFuture<Void> stepBack(StepBackArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reverseContinue(ReverseContinueArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> restartFrame(RestartFrameArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> goto_(GotoArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> pause(PauseArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SourceResponse> source(SourceArguments args) {
        return CompletableFuture.completedFuture(new SourceResponse());
    }

    @Override
    public CompletableFuture<LoadedSourcesResponse> loadedSources(LoadedSourcesArguments args) {
        return CompletableFuture.completedFuture(new LoadedSourcesResponse());
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return CompletableFuture.completedFuture(new EvaluateResponse());
    }

    @Override
    public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
        return CompletableFuture.completedFuture(new SetVariableResponse());
    }

    @Override
    public CompletableFuture<SetExpressionResponse> setExpression(SetExpressionArguments args) {
        return CompletableFuture.completedFuture(new SetExpressionResponse());
    }

    @Override
    public CompletableFuture<StepInTargetsResponse> stepInTargets(StepInTargetsArguments args) {
        return CompletableFuture.completedFuture(new StepInTargetsResponse());
    }

    @Override
    public CompletableFuture<GotoTargetsResponse> gotoTargets(GotoTargetsArguments args) {
        return CompletableFuture.completedFuture(new GotoTargetsResponse());
    }

    @Override
    public CompletableFuture<CompletionsResponse> completions(CompletionsArguments args) {
        return CompletableFuture.completedFuture(new CompletionsResponse());
    }

    @Override
    public CompletableFuture<ExceptionInfoResponse> exceptionInfo(ExceptionInfoArguments args) {
        return CompletableFuture.completedFuture(new ExceptionInfoResponse());
    }

    @Override
    public CompletableFuture<ReadMemoryResponse> readMemory(ReadMemoryArguments args) {
        return CompletableFuture.completedFuture(new ReadMemoryResponse());
    }

    @Override
    public CompletableFuture<WriteMemoryResponse> writeMemory(WriteMemoryArguments args) {
        return CompletableFuture.completedFuture(new WriteMemoryResponse());
    }

    @Override
    public CompletableFuture<DisassembleResponse> disassemble(DisassembleArguments args) {
        return CompletableFuture.completedFuture(new DisassembleResponse());
    }

    @Override
    public CompletableFuture<BreakpointLocationsResponse> breakpointLocations(BreakpointLocationsArguments args) {
        return CompletableFuture.completedFuture(new BreakpointLocationsResponse());
    }

    @Override
    public CompletableFuture<ModulesResponse> modules(ModulesArguments args) {
        return CompletableFuture.completedFuture(new ModulesResponse());
    }

    @Override
    public CompletableFuture<Void> terminateThreads(TerminateThreadsArguments args) {
        return CompletableFuture.completedFuture(null);
    }
}
