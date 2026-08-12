package com.mira.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.cli.Flags;
import com.mira.compiler.CompileRunner;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.format.AstWalker;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lib.LibIndex;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement;
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
            long tokenizeStart = System.nanoTime();
            List<Token> tokens = tokenizer.tokenize(readFile, false);
            long tokenizeMs = (System.nanoTime() - tokenizeStart) / 1_000_000;

            if (Flags.dumpTokens) {
                tokens.forEach(token -> System.out.println(token.getLexeme() + "-" + token.getTokenType() + "-" + token.getLine() + ";" + token.getColumn()));
            }

            Parser parser = new Parser();
            long parseStart = System.nanoTime();
            List<Node> asts = parser.parseTokens(tokens);
            long parseMs = (System.nanoTime() - parseStart) / 1_000_000;

            if (Flags.printAsts) {
                System.out.println(new AstPrinter().print(asts));
            }

            long comptimeStart = System.nanoTime();
            Map<String, Object> comptimeConsts = (!Flags.checkOnly && !Flags.testMode)
                    ? new ComptimeExecutor().execute(asts)
                    : null;
            long comptimeMs = (System.nanoTime() - comptimeStart) / 1_000_000;

            long entryCheckMs = 0;
            long moduleCheckMs = 0;
            if (!Flags.noCheck) {
                long entryCheckStart = System.nanoTime();
                boolean mainErrors = false;
                try {
                    new StaticCheck(Set.of(), Flags.inputPath.get(), Map.of(), comptimeConsts).check(asts);
                } catch (MultipleStaticCheckErrors mre) {
                    WarningCollector.clear();
                    mre.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
                    mainErrors = true;
                }
                entryCheckMs = (System.nanoTime() - entryCheckStart) / 1_000_000;
                long moduleCheckStart = System.nanoTime();
                boolean moduleErrors = ModuleChecker.check(asts, new LinkedHashSet<>());
                moduleCheckMs = (System.nanoTime() - moduleCheckStart) / 1_000_000;
                WarningCollector.flush();
                if (mainErrors || moduleErrors) {
                    return false;
                }
            }

            if (Flags.libInfo) {
                LibIndex.printImportInfo(asts);
            }

            if (Flags.checkOnly) {
                if (Flags.stats) {
                    printStats(readFile, tokens, asts, tokenizeMs, parseMs, comptimeMs, entryCheckMs, moduleCheckMs);
                }
                return true;
            }

            if (Flags.testMode) {
                boolean testsFailed = TestRunner.runPrePassCollecting(asts, Flags.args);
                Flags.testsDone = true;
                if (Flags.stats) {
                    printStats(readFile, tokens, asts, tokenizeMs, parseMs, comptimeMs, entryCheckMs, moduleCheckMs,
                            new TestTotals(TestRunner.getLastPassed(), TestRunner.getLastFailed()));
                }
                if (testsFailed) {
                    System.exit(1);
                }
                return true;
            }

            if (Flags.stats) {
                printStats(readFile, tokens, asts, tokenizeMs, parseMs, comptimeMs, entryCheckMs, moduleCheckMs);
            }

            if (Flags.packageJar && !Flags.compile) {
                System.err.println("Warning: --package has no effect without --compile");
            }

            if (Flags.profile && Flags.compile && !Flags.compileAndRun) {
                System.err.println("Warning: --profile has no effect with --compile alone; add --run to see a report.");
            }

            if (Flags.compile) {
                new CompileRunner().run(asts, comptimeConsts);
                return true;
            }

            interpreter.presetComptimeConsts(comptimeConsts);

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
            if (Flags.compile && !com.mira.compiler.support.CompiledRuntimeSupport.getCallStack().isEmpty()) {
                com.mira.compiler.support.CompiledRuntimeSupport.dumpCallStack(e, System.err);
            } else {
                interpreter.dumpState(e, System.err);
            }
            return false;
        }
        return true;
    }

    private record FileStats(String label, long bytes, int lines, int tokens, int topLevelNodes,
            int totalNodes, int functions, int variables, int imports, int enums) {
    }

    private record TestTotals(long passed, long failed) {
    }

    private static void printStats(String source, List<Token> tokens, List<Node> asts,
            long tokenizeMs, long parseMs, long comptimeMs, long entryCheckMs, long moduleCheckMs) {
        printStats(source, tokens, asts, tokenizeMs, parseMs, comptimeMs, entryCheckMs, moduleCheckMs, null);
    }

    /**
     * Prints stats for every file that makes up the program - the entry file plus
     * every module it imports, transitively - not just the entry file alone, since
     * a program's real size/shape is usually spread across its imported modules.
     */
    private static void printStats(String source, List<Token> tokens, List<Node> asts,
            long tokenizeMs, long parseMs, long comptimeMs, long entryCheckMs, long moduleCheckMs,
            TestTotals testTotals) {
        Path entryPath = Flags.inputPath.get();
        List<FileStats> files = new ArrayList<>();
        files.add(computeFileStats(entryPath, source, asts, tokens));
        for (ModuleChecker.ParsedModule module : ModuleChecker.collectAllModules(asts, entryPath).values()) {
            List<Token> moduleTokens = new Tokenizer().tokenize(module.source(), false);
            files.add(computeFileStats(module.path(), module.source(), module.ast(), moduleTokens));
        }

        long totalBytes = 0;
        int totalLines = 0;
        int totalTokens = 0;
        int totalTopLevel = 0;
        int totalNodes = 0;
        int totalFunctions = 0;
        int totalVariables = 0;
        int totalImports = 0;
        int totalEnums = 0;
        for (FileStats f : files) {
            totalBytes += f.bytes();
            totalLines += f.lines();
            totalTokens += f.tokens();
            totalTopLevel += f.topLevelNodes();
            totalNodes += f.totalNodes();
            totalFunctions += f.functions();
            totalVariables += f.variables();
            totalImports += f.imports();
            totalEnums += f.enums();
        }

        System.out.println();
        System.out.println("=== MIRA STATS ===");
        System.out.println("Files: " + files.size());
        for (FileStats f : files) {
            System.out.printf("  %-30s %8d bytes  %6d lines  %6d tokens  %6d AST nodes%n",
                    f.label(), f.bytes(), f.lines(), f.tokens(), f.totalNodes());
        }
        System.out.println();
        System.out.println("Totals:");
        System.out.println("Bytes: " + totalBytes);
        System.out.println("Lines: " + totalLines);
        System.out.println("Tokens: " + totalTokens);
        System.out.println("AST nodes: " + totalTopLevel + " top-level, " + totalNodes + " total");
        System.out.println("Functions: " + totalFunctions);
        System.out.println("Variables: " + totalVariables);
        System.out.println("Imports: " + totalImports);
        System.out.println("Enums: " + totalEnums);
        if (testTotals != null) {
            System.out.println("Tests: " + testTotals.passed() + " passed, " + testTotals.failed() + " failed");
        }
        System.out.println("--- Timing ---");
        System.out.println("Tokenize (entry file): " + tokenizeMs + " ms");
        System.out.println("Parse (entry file): " + parseMs + " ms");
        System.out.println("Comptime (entry file): " + comptimeMs + " ms");
        System.out.println("Static check (entry file): " + entryCheckMs + " ms");
        System.out.println("Static check (imported modules): " + moduleCheckMs + " ms");
        System.out.println("=== END STATS ===");
        System.out.println();
    }

    private static FileStats computeFileStats(Path path, String source, List<Node> ast, List<Token> tokens) {
        int functions = 0;
        int variables = 0;
        int imports = 0;
        int enums = 0;
        for (Node n : ast) {
            if (n instanceof Statement.FuncDecl) {
                functions++;
            } else if (n instanceof Statement.VarDecl || n instanceof Statement.VarDestructure) {
                variables++;
            } else if (n instanceof ImportExpression) {
                imports++;
            } else if (n instanceof Statement.EnumDecl) {
                enums++;
            }
        }
        long bytes;
        try {
            bytes = Files.size(path);
        } catch (IOException e) {
            bytes = source.length();
        }
        int lines = source.split("\n", -1).length;
        return new FileStats(path.getFileName().toString(), bytes, lines, tokens.size(),
                ast.size(), countNodes(ast), functions, variables, imports, enums);
    }

    private static int countNodes(List<Node> ast) {
        int count = 0;
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            count++;
            AstWalker.children(n, queue);
        }
        return count;
    }
}
