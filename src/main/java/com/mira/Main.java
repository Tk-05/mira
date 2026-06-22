package com.mira;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.build.BuildDispatcher;
import com.mira.compiler.CompileRunner;
import com.mira.debugger.Debugger;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lib.LibIndex;
import com.mira.lsp.AstFormatter;
import com.mira.lsp.Launcher;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.repl.Repl;
import com.mira.resolver.ModuleChecker;
import com.mira.resolver.StaticCheck;
import com.mira.runtime.AstPrinter;
import com.mira.runtime.HotReloader;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.testing.TestRunner;
import com.mira.utils.FileLoader;
import com.mira.warning.WarningCollector;

public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {

            if (args[0].equals("--lsp")) {
                try {
                    Launcher.launch();
                } catch (Exception e) {
                    System.err.println("LSP server error: " + e.getMessage());
                }
                return;
            }

            if (args[0].equals("--dap")) {
                try {
                    com.mira.dap.DapLauncher.launch();
                } catch (Exception e) {
                    System.err.println("DAP server error: " + e.getMessage());
                }
                return;
            }

            if (args[0].equals("--fmt")) {
                if (args.length < 2) {
                    System.err.println("Usage: mira --fmt <file.mira>");
                    System.exit(1);
                    return;
                }
                try {
                    java.nio.file.Path fmtPath = Paths.get(args[1]).toAbsolutePath().normalize();
                    String fmtSource = FileLoader.readFileFromPath(fmtPath.toString());
                    String fmtResult = AstFormatter.format(fmtSource);
                    java.nio.file.Files.writeString(fmtPath, fmtResult);
                    System.out.println("Formatted: " + fmtPath);
                } catch (Exception e) {
                    System.err.println("Format error: " + e.getMessage());
                    System.exit(1);
                }
                return;
            }

            if (BuildDispatcher.isSubcommand(args[0])) {
                BuildDispatcher.dispatch(args);
                return;
            }

            if (args[0].equals("-h") || args[0].equals("-help")) {
                System.out.println(Help.getHelp());
                System.exit(1);
            } else if (args[0].startsWith("-")) {
                System.err.println(DiagnosticFormatter.formatError("no input file specified"));
                System.err.println("Usage: mira <file.mira> [flags]  |  Use -h for help.");
                System.exit(1);
            } else {
                Flags.inputPath.set(Paths.get((args[0])).toAbsolutePath().normalize());
            }

            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "-t" ->
                        Flags.dumpTokens = true;
                    case "-e" ->
                        Flags.exitBeforeInterpreter = true;
                    case "-m" ->
                        Flags.mainFunction = true;
                    case "-li" ->
                        Flags.libInfo = true;
                    case "-liFull" -> {
                        Flags.libInfo = true;
                        Flags.libInfoFull = true;
                    }
                    case "-args" -> {
                        Flags.args = args[i + 1].split(",");
                        i++;
                    }
                    case "-debug" ->
                        Flags.debug = true;
                    case "-watch" ->
                        Flags.hotReload = true;
                    case "-crash" ->
                        Flags.crashDump = true;
                    case "-crashFull" -> {
                        Flags.crashDump = true;
                        Flags.crashDumpFull = true;
                    }
                    case "-test" ->
                        Flags.testMode = true;
                    case "-ast" ->
                        Flags.printAsts = true;
                    case "-compile" ->
                        Flags.compile = true;
                    case "-compile-run" -> {
                        Flags.compile = true;
                        Flags.compileAndRun = true;
                    }
                    case "-b" ->
                        Flags.dumpByteCode = true;
                    case "-o" -> {
                        Flags.outputDir = Paths.get(args[i + 1]);
                        i++;
                    }
                    case "-package" ->
                        Flags.packageJar = true;
                    case "-nsc" ->
                        Flags.skipStaticCheck = true;
                    case "-no-warn" ->
                        Flags.suppressWarnings = true;
                    default -> {
                        System.err.println(DiagnosticFormatter.formatError("'" + args[i] + "' is not a known flag"));
                        System.err.println("Use -h for help.");
                        System.exit(1);
                    }
                }
            }

            if (Flags.debug) {
                Debugger.run();
                return;
            }

            if (Flags.hotReload) {
                new HotReloader(Flags.inputPath.get()).run();
                return;
            }

            runFile();

        } else {
            Repl.run();
        }
    }

    private static void runFile() {
        runFile(new AtomicBoolean(false));
    }

    public static void runFile(AtomicBoolean stopping) {
        long start = System.currentTimeMillis();

        String readFile;
        try {
            readFile = FileLoader.readFileFromPath(Flags.inputPath.get().toString());
        } catch (IOException e) {
            if (!stopping.get()) {
                System.err.println(DiagnosticFormatter.formatFileError(Flags.inputPath.get(), e));
            }
            return;
        }

        Flags.fileName = Flags.inputPath.get().getFileName().toString();
        Flags.sourceLines = readFile.split("\n", -1);

        Interpreter interpreter = new Interpreter();
        try {
            Tokenizer tokenizer = new Tokenizer();
            List<Token> tokens = tokenizer.tokenize(readFile, false);

            if (Flags.dumpTokens) {
                tokens.forEach(token -> System.out.println(token.getLexeme() + "-" + token.getTokenType() + "-" + token.getLine() + ";" + token.getColumn()));
            }

            Parser parser = new Parser();
            List<Node> asts = parser.parseTokens(tokens);

            if (Flags.printAsts) {
                System.out.println(new AstPrinter().print(asts));
            }

            if (!Flags.skipStaticCheck) {
                new StaticCheck(Set.of(), Flags.inputPath.get()).check(asts);
                WarningCollector.flush();
                ModuleChecker.check(asts, new LinkedHashSet<>());
            }

            if (Flags.libInfo) {
                LibIndex.printImportInfo(asts);
            }

            if (Flags.exitBeforeInterpreter) {
                return;
            }

            if (Flags.testMode) {
                TestRunner.runPrePass(asts, Flags.args);
                return;
            }

            if (Flags.packageJar && !Flags.compile) {
                System.err.println("Warning: -package has no effect without -compile");
            }

            if (Flags.compile) {
                new CompileRunner().run(asts);
                return;
            }

            if (Flags.mainFunction) {
                Object exitValue = interpreter.run(asts, Flags.args, true);
                WarningCollector.flush();
                System.out.println("Program exited with value: " + exitValue + " in " + (System.currentTimeMillis() - start) + " ms");
            } else {
                try {
                    interpreter.run(asts, Flags.args, true);
                } catch (ReturnSignal returnSignal) {
                    System.out.println("Program exited with value: " + returnSignal.getValue() + " in " + (System.currentTimeMillis() - start) + " ms");
                } finally {
                    WarningCollector.flush();
                }
            }

            if (Flags.testMode && !Flags.testsDone) {
                TestRunner.printSummary(System.out);
                boolean failed = TestRunner.hasFailures();
                TestRunner.reset();
                if (failed) {
                    System.exit(1);
                }
            }

        } catch (MultipleParserErrors mpe) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            mpe.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
        } catch (MultipleStaticCheckErrors mre) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            mre.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
        } catch (Exception e) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            System.err.println(DiagnosticFormatter.format(e));
            if (Flags.crashDump) {
                if (Flags.compile && !com.mira.compiler.Runtime.getCallStack().isEmpty()) {
                    com.mira.compiler.Runtime.dumpCallStack(e, System.err);
                } else {
                    interpreter.dumpState(e, System.err);
                }
            }
        }
    }
}
