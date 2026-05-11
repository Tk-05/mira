package com.mira;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.compiler.CompileRunner;
import com.mira.debugger.Debugger;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.runtime.RuntimeError.ModuleNameMismatchError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.linter.Linter;
import com.mira.lsp.Launcher;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.repl.Repl;
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

            if (args[0].equals("-h") || args[0].equals("-help")) {
                System.out.println(Help.getHelp());
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
                    case "-args" -> {
                        Flags.args = args[i + 1].split(",");
                        i++;
                    }
                    case "-debug" ->
                        Flags.debug = true;
                    case "-lint" ->
                        Flags.lint = true;
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
                    default ->
                        throw new RuntimeException(args[i] + " is not a known flag");
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
                System.err.println(DiagnosticFormatter.format(e));
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

            if (!asts.isEmpty() && asts.getFirst() instanceof ModuleDecl moduleDecl) {
                String expectedName = Flags.fileName.replace(".mira", "");
                if (!moduleDecl.getModuleName().equals(expectedName)) {
                    throw new ModuleNameMismatchError(Flags.fileName, expectedName, moduleDecl.getModuleName());
                }
            }

            if (Flags.printAsts) {
                System.out.println(new AstPrinter().print(asts));
            }

            if (Flags.lint) {
                new Linter().lint(asts);
                WarningCollector.flush();
            }

            if (Flags.exitBeforeInterpreter) {
                return;
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

            if (Flags.testMode) {
                TestRunner.printSummary(System.out);
                boolean failed = TestRunner.hasFailures();
                TestRunner.reset();
                if (failed) {
                    System.exit(1);
                }
            }

        } catch (Exception e) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            System.err.println(DiagnosticFormatter.format(e));
            if (Flags.crashDump) {
                interpreter.dumpState(e, System.err);
            }
        }
    }

}
