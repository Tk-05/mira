package com.mira.runtime;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.cli.Flags;
import com.mira.compiler.CompileRunner;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lib.LibIndex;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.resolver.ModuleChecker;
import com.mira.resolver.StaticCheck;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.testing.TestRunner;
import com.mira.utils.FileLoader;
import com.mira.warning.WarningCollector;

public class FileRunner {

    public static boolean runFile(AtomicBoolean stopping) {
        long start = System.currentTimeMillis();

        String readFile;
        try {
            readFile = FileLoader.readFileFromPath(Flags.inputPath.get().toString());
        } catch (IOException e) {
            if (!stopping.get()) {
                System.err.println(DiagnosticFormatter.formatFileError(Flags.inputPath.get(), e));
            }
            return false;
        }

        Flags.fileName = Flags.inputPath.get().getFileName().toString();
        Flags.sourceLines = readFile.split("\n", -1);

        Interpreter interpreter = new Interpreter();
        if (Flags.profile) {
            interpreter.setProfilingEnabled(true);
        }
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
                boolean mainErrors = false;
                try {
                    new StaticCheck(Set.of(), Flags.inputPath.get()).check(asts);
                } catch (MultipleStaticCheckErrors mre) {
                    WarningCollector.clear();
                    mre.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
                    mainErrors = true;
                }
                boolean moduleErrors = ModuleChecker.check(asts, new LinkedHashSet<>());
                WarningCollector.flush();
                if (mainErrors || moduleErrors) {
                    return false;
                }
            }

            if (Flags.libInfo) {
                LibIndex.printImportInfo(asts);
            }

            if (Flags.exitBeforeInterpreter) {
                return true;
            }

            if (Flags.testMode) {
                TestRunner.runPrePass(asts, Flags.args);
                return true;
            }

            if (Flags.packageJar && !Flags.compile) {
                System.err.println("Warning: -package has no effect without -compile");
            }

            if (Flags.profile && Flags.compile && !Flags.compileAndRun) {
                System.err.println("Warning: -profile has no effect with -compile alone; use -compile-run to see a report.");
            }

            if (Flags.compile) {
                new CompileRunner().run(asts);
                return true;
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

            if (Flags.profile) {
                interpreter.getProfiler().finishLineTracking();
                interpreter.getProfiler().printReport(System.out);
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
                return false;
            }
            mpe.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
            return false;
        } catch (MultipleStaticCheckErrors mre) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return false;
            }
            mre.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
            return false;
        } catch (Exception e) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return false;
            }
            System.err.println(DiagnosticFormatter.format(e));
            if (Flags.crashDump) {
                if (Flags.compile && !com.mira.compiler.support.CompiledRuntimeSupport.getCallStack().isEmpty()) {
                    com.mira.compiler.support.CompiledRuntimeSupport.dumpCallStack(e, System.err);
                } else {
                    interpreter.dumpState(e, System.err);
                }
            }
            return false;
        }
        return true;
    }
}
