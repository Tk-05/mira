package com.mira.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.cli.Flags;
import com.mira.compiler.CompileRunner;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.lexer.MultipleLexerErrors;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.format.AstWalker;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lib.LibIndex;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.resolver.ModuleChecker;
import com.mira.resolver.StaticCheck;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.testing.CoverageTracker;
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

        Flags.fileName.set(Flags.inputPath.get().getFileName().toString());
        Flags.sourceLines.set(readFile.split("\n", -1));

        Interpreter interpreter = new Interpreter();
        if (Flags.profile) {
            interpreter.setProfilingEnabled(true);
        }
        try {
            Tokenizer tokenizer = new Tokenizer();
            long tokenizeStart = System.nanoTime();
            List<Token> tokens = tokenizer.tokenize(readFile, false);
            long tokenizeNanos = System.nanoTime() - tokenizeStart;

            if (Flags.dumpTokens) {
                tokens.forEach(token -> System.out.println(token.getLexeme() + "-" + token.getTokenType() + "-"
                        + token.getLine() + ";" + token.getColumn()));
            }

            Parser parser = new Parser();
            long parseStart = System.nanoTime();
            List<Node> asts = parser.parseTokens(tokens);
            long parseNanos = System.nanoTime() - parseStart;

            if (Flags.printAsts) {
                System.out.println(new AstPrinter().print(asts));
            }

            long comptimeStart = System.nanoTime();
            Map<String, Object> comptimeConsts = (!Flags.checkOnly && !Flags.testMode)
                    ? new ComptimeExecutor().execute(asts)
                    : null;
            long comptimeNanos = System.nanoTime() - comptimeStart;

            long entryCheckMs = 0;
            int warningCount = 0;
            Map<Path, Long> moduleCheckTimingsMs = Map.of();
            Map<Path, ModuleChecker.ParsedModule> checkedModules = Map.of();
            long moduleDiscoveryWallMs = 0;
            long moduleCheckWallMs = 0;
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
                int entryWarningCount = WarningCollector.getWarnings().size();
                WarningCollector.flush();
                ModuleChecker.ModuleCheckResult moduleResult = ModuleChecker.check(asts, new LinkedHashSet<>());
                moduleCheckTimingsMs = moduleResult.checkTimingsMs();
                checkedModules = moduleResult.modules();
                warningCount = entryWarningCount + moduleResult.warningCount();
                moduleDiscoveryWallMs = moduleResult.discoveryWallMs();
                moduleCheckWallMs = moduleResult.checkWallMs();
                if (mainErrors || moduleResult.hadErrors()) {
                    return false;
                }
            }

            if (Flags.libInfo) {
                LibIndex.printImportInfo(asts);
            }

            if (Flags.checkOnly) {
                if (Flags.stats) {
                    printStats(readFile, tokens, asts, tokenizeNanos, parseNanos, comptimeNanos, entryCheckMs,
                            moduleCheckTimingsMs, checkedModules, warningCount, moduleDiscoveryWallMs,
                            moduleCheckWallMs, -1);
                }
                return true;
            }

            if (Flags.testMode) {
                if (Flags.coverage) {
                    CoverageTracker.reset();
                    CoverageTracker.setEnabled(true);
                }
                boolean testsFailed = TestRunner.runPrePassCollecting(asts, Flags.args);
                Flags.testsDone = true;
                if (Flags.stats) {
                    printStats(readFile, tokens, asts, tokenizeNanos, parseNanos, comptimeNanos, entryCheckMs,
                            moduleCheckTimingsMs, checkedModules, warningCount, moduleDiscoveryWallMs,
                            moduleCheckWallMs, -1,
                            new TestTotals(TestRunner.getLastPassed(), TestRunner.getLastFailed()));
                }
                if (Flags.coverage) {
                    printCoverage(asts, Flags.inputPath.get());
                    CoverageTracker.setEnabled(false);
                }
                if (testsFailed) {
                    System.exit(1);
                }
                return true;
            }

            if (Flags.stats && !Flags.compile) {
                printStats(readFile, tokens, asts, tokenizeNanos, parseNanos, comptimeNanos, entryCheckMs,
                        moduleCheckTimingsMs, checkedModules, warningCount, moduleDiscoveryWallMs, moduleCheckWallMs,
                        -1);
            }

            if (Flags.packageJar && !Flags.compile) {
                System.err.println("Warning: --package has no effect without --compile");
            }

            if (Flags.profile && Flags.compile && !Flags.compileAndRun) {
                System.err.println("Warning: --profile has no effect with --compile alone; add --run to see a report.");
            }

            if (Flags.compile) {
                long finalEntryCheckMs = entryCheckMs;
                int finalWarningCount = warningCount;
                Map<Path, Long> finalModuleCheckTimingsMs = moduleCheckTimingsMs;
                Map<Path, ModuleChecker.ParsedModule> finalCheckedModules = checkedModules;
                long finalModuleDiscoveryWallMs = moduleDiscoveryWallMs;
                long finalModuleCheckWallMs = moduleCheckWallMs;
                // afterCompile fires once bytecode generation is done but before --compile
                // --run
                // executes the result in memory (which happens inline, inside that same call) -
                // without this, --stats output would print after the compiled program already
                // ran
                new CompileRunner().run(asts, comptimeConsts, compileMs -> {
                    if (Flags.stats) {
                        printStats(readFile, tokens, asts, tokenizeNanos, parseNanos, comptimeNanos, finalEntryCheckMs,
                                finalModuleCheckTimingsMs, finalCheckedModules, finalWarningCount,
                                finalModuleDiscoveryWallMs, finalModuleCheckWallMs, compileMs);
                    }
                });
                return true;
            }

            interpreter.presetComptimeConsts(comptimeConsts);

            if (Flags.mainFunction) {
                Object exitValue = interpreter.run(asts, Flags.args, true);
                WarningCollector.flush();
                System.out.println("Program exited with value: " + exitValue + " in "
                        + (System.currentTimeMillis() - start) + " ms");
            } else {
                try {
                    interpreter.run(asts, Flags.args, true);
                } catch (ReturnSignal returnSignal) {
                    System.out.println("Program exited with value: " + returnSignal.getValue() + " in "
                            + (System.currentTimeMillis() - start) + " ms");
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

        } catch (MultipleLexerErrors mle) {
            WarningCollector.clear();
            if (stopping.get() || Thread.currentThread().isInterrupted()) {
                return false;
            }
            mle.getErrors().forEach(e -> System.err.println(DiagnosticFormatter.format(e)));
            return false;
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

    private record FileStats(String label, long bytes, int lines, int tokens, int topLevelNodes, int totalNodes,
            int functions, int variables, int imports, int enums, int typedVars, int totalVarsDeep, int typedParams,
            int totalParams, int typedReturns, int totalFunctionsDeep, int typeAliases) {

    }

    private record TestTotals(long passed, long failed) {

    }

    private static void printStats(String source, List<Token> tokens, List<Node> asts, long tokenizeNanos,
            long parseNanos, long comptimeNanos, long entryCheckMs, Map<Path, Long> moduleCheckTimingsMs,
            Map<Path, ModuleChecker.ParsedModule> checkedModules, int warningCount, long moduleDiscoveryWallMs,
            long moduleCheckWallMs, long compileMs) {
        printStats(source, tokens, asts, tokenizeNanos, parseNanos, comptimeNanos, entryCheckMs, moduleCheckTimingsMs,
                checkedModules, warningCount, moduleDiscoveryWallMs, moduleCheckWallMs, compileMs, null);
    }

    /**
     * Prints stats for every file that makes up the program - the entry file plus
     * every module it imports, transitively - not just the entry file alone, since
     * a program's real size/shape is usually spread across its imported modules.
     * compileMs is the bytecode-generation time when this run was a --compile run
     * (measured by CompileRunner and passed back in, since compilation finishes
     * after this method would otherwise have already printed); -1 means not
     * applicable (an interpreted run). moduleDiscoveryWallMs/moduleCheckWallMs are
     * the real wall-clock time module discovery/checking took (both run modules in
     * parallel - see ModuleChecker) - shown separately from the per-file
     * tokenize/parse/check sums below, which are a sum of concurrently-overlapping
     * durations and so no longer represent elapsed time on their own.
     */
    private static void printStats(String source, List<Token> tokens, List<Node> asts, long tokenizeNanos,
            long parseNanos, long comptimeNanos, long entryCheckMs, Map<Path, Long> moduleCheckTimingsMs,
            Map<Path, ModuleChecker.ParsedModule> checkedModules, int warningCount, long moduleDiscoveryWallMs,
            long moduleCheckWallMs, long compileMs, TestTotals testTotals) {
        Path entryPath = Flags.inputPath.get();
        Collection<ModuleChecker.ParsedModule> modules = checkedModules.values();
        List<FileStats> files = new ArrayList<>();
        files.add(computeFileStats(entryPath, source, asts, tokens.size()));
        for (ModuleChecker.ParsedModule module : modules) {
            files.add(computeFileStats(module.path(), module.source(), module.ast(), module.tokenCount()));
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
        int totalTypedVars = 0;
        int totalVarsDeep = 0;
        int totalTypedParams = 0;
        int totalParams = 0;
        int totalTypedReturns = 0;
        int totalFunctionsDeep = 0;
        int totalTypeAliases = 0;
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
            totalTypedVars += f.typedVars();
            totalVarsDeep += f.totalVarsDeep();
            totalTypedParams += f.typedParams();
            totalParams += f.totalParams();
            totalTypedReturns += f.typedReturns();
            totalFunctionsDeep += f.totalFunctionsDeep();
            totalTypeAliases += f.typeAliases();
        }

        System.out.println();
        System.out.println("=== MIRA STATS ===");
        System.out.println("Files: " + files.size());
        for (FileStats f : files) {
            System.out.printf("  %-30s %8d bytes  %6d lines  %6d tokens  %6d AST nodes%n", f.label(), f.bytes(),
                    f.lines(), f.tokens(), f.totalNodes());
        }
        System.out.println();
        System.out.println("Totals:");
        System.out.println("Bytes: " + totalBytes);
        System.out.println("Lines: " + totalLines);
        System.out.println("Tokens: " + totalTokens);
        System.out.println("AST nodes: " + totalTopLevel + " top-level, " + totalNodes + " total");
        System.out.println("Functions: " + totalFunctions);
        System.out.println("Variables: " + totalVariables + " top-level, " + totalVarsDeep + " total");
        System.out.println("Imports: " + totalImports);
        System.out.println("Enums: " + totalEnums);
        System.out.println("Warnings: " + warningCount);
        if (testTotals != null) {
            System.out.println("Tests: " + testTotals.passed() + " passed, " + testTotals.failed() + " failed");
        }
        System.out.println("--- Type Checker ---");
        System.out.println("Strict mode: " + (Flags.strictTypes ? "on (--strict-types)" : "off"));
        System.out.println("Typed variables: " + totalTypedVars + " / " + totalVarsDeep
                + percentSuffix(totalTypedVars, totalVarsDeep));
        System.out.println("Typed parameters: " + totalTypedParams + " / " + totalParams
                + percentSuffix(totalTypedParams, totalParams));
        System.out.println("Typed return types: " + totalTypedReturns + " / " + totalFunctionsDeep
                + percentSuffix(totalTypedReturns, totalFunctionsDeep));
        System.out.println("Type aliases: " + totalTypeAliases);
        System.out.println("--- Timing ---");
        System.out.println("Tokenize (entry file): " + formatMs(tokenizeNanos));
        System.out.println("Parse (entry file): " + formatMs(parseNanos));
        System.out.println("Comptime (entry file): " + formatMs(comptimeNanos));
        System.out.println("Static check (entry file): " + entryCheckMs + " ms");
        if (compileMs >= 0) {
            System.out.println("Bytecode generation (all files): " + compileMs + " ms");
        }
        double totalTokenizeMs = tokenizeNanos / 1_000_000.0;
        double totalParseMs = parseNanos / 1_000_000.0;
        long totalCheckMs = entryCheckMs;
        for (ModuleChecker.ParsedModule module : modules) {
            long moduleCheckMs = moduleCheckTimingsMs.getOrDefault(module.path(), 0L);
            String label = module.path().getFileName().toString();
            System.out.println();
            System.out.println("Tokenize (" + label + "): " + formatMs(module.tokenizeNanos()));
            System.out.println("Parse (" + label + "): " + formatMs(module.parseNanos()));
            System.out.println("Static check (" + label + "): " + moduleCheckMs + " ms");
            totalTokenizeMs += module.tokenizeNanos() / 1_000_000.0;
            totalParseMs += module.parseNanos() / 1_000_000.0;
            totalCheckMs += moduleCheckMs;
        }
        if (!modules.isEmpty() || compileMs >= 0) {
            System.out.println();
            String compileSuffix = compileMs >= 0 ? ", bytecode generation " + compileMs + " ms" : "";
            System.out.printf(java.util.Locale.US,
                    "Total (all files, CPU time summed): tokenize %.3f ms, parse %.3f ms, static check %d ms%s%n",
                    totalTokenizeMs, totalParseMs, totalCheckMs, compileSuffix);
            if (!modules.isEmpty()) {
                System.out.println("Wall-clock (modules run in parallel): discovery " + moduleDiscoveryWallMs
                        + " ms, static check " + moduleCheckWallMs + " ms");
            }
        }
        System.out.println("=== END STATS ===");
        System.out.println();
    }

    private static String percentSuffix(int part, int whole) {
        return whole == 0 ? "" : String.format(" (%.0f%%)", 100.0 * part / whole);
    }

    private static String formatMs(long nanos) {
        return String.format(java.util.Locale.US, "%.3f ms", nanos / 1_000_000.0);
    }

    private static void printCoverage(List<Node> asts, Path entryPath) {
        List<CoverageTracker.FileEntry> files = new ArrayList<>();
        files.add(new CoverageTracker.FileEntry(CoverageTracker.moduleNameOf(asts), entryPath.getFileName().toString(),
                asts));
        for (ModuleChecker.ParsedModule module : ModuleChecker.collectAllModules(asts, entryPath).values()) {
            files.add(new CoverageTracker.FileEntry(CoverageTracker.moduleNameOf(module.ast()),
                    module.path().getFileName().toString(), module.ast()));
        }
        CoverageTracker.printReport(System.out, files);
    }

    private static FileStats computeFileStats(Path path, String source, List<Node> ast, int tokenCount) {
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
        DeepStats deep = computeDeepStats(ast);
        return new FileStats(path.getFileName().toString(), bytes, lines, tokenCount, ast.size(), deep.totalNodes(),
                functions, variables, imports, enums, deep.typedVars(), deep.totalVars(), deep.typedParams(),
                deep.totalParams(), deep.typedReturns(), deep.totalFunctions(), deep.typeAliases());
    }

    private record DeepStats(int totalNodes, int typedVars, int totalVars, int typedParams, int totalParams,
            int typedReturns, int totalFunctions, int typeAliases) {

    }

    private static DeepStats computeDeepStats(List<Node> ast) {
        int totalNodes = 0;
        int typedVars = 0;
        int totalVars = 0;
        int typedParams = 0;
        int totalParams = 0;
        int typedReturns = 0;
        int totalFunctions = 0;
        int typeAliases = 0;
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            totalNodes++;
            if (n instanceof Statement.VarDecl vd) {
                totalVars++;
                if (vd.getType() != null) {
                    typedVars++;
                }
            } else if (n instanceof Statement.FuncDecl fd) {
                totalFunctions++;
                if (fd.getReturnType() != null) {
                    typedReturns++;
                }
                for (Parameter p : fd.getParameters()) {
                    totalParams++;
                    if (p.type() != null) {
                        typedParams++;
                    }
                }
            } else if (n instanceof Statement.TypeAliasDecl) {
                typeAliases++;
            }
            AstWalker.children(n, queue);
        }
        return new DeepStats(totalNodes, typedVars, totalVars, typedParams, totalParams, typedReturns, totalFunctions,
                typeAliases);
    }
}
