package com.mira.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mira.error.DiagnosticFormatter;

public class Flags {

    public static final String VERSION = "0.1.0";

    public static final ThreadLocal<Path> inputPath = new ThreadLocal<>();
    public static boolean dumpTokens = false;
    public static boolean checkOnly = false;
    public static boolean mainFunction = false;
    public static boolean libInfo = false;
    public static boolean libInfoFull = false;
    public static boolean debug = false;
    public static boolean hotReload = false;
    public static boolean crashDump = false;
    public static boolean crashDumpFull = false;
    public static boolean testMode = false;
    public static boolean testsDone = false;
    public static boolean printAsts = false;
    public static String[] args = null;
    public static String fileName = null;
    public static String[] sourceLines = null;

    public static boolean compile = false;
    public static boolean compileAndRun = false;
    public static boolean dumpByteCode = false;
    public static Path outputDir = null;
    public static boolean packageJar = false;
    public static boolean slimJar = false;

    public static List<Path> dependencyRoots = new ArrayList<>();
    public static List<Path> nativeRoots = new ArrayList<>();

    public static boolean noCheck = false;
    public static boolean suppressWarnings = false;
    public static boolean noColor = false;
    public static boolean verbose = false;

    public static boolean profile = false;

    public static void parse(String[] args) {
        if (args[0].equals("-h") || args[0].equals("--help")) {
            System.out.println(Help.getHelp());
            System.exit(1);
        } else if (args[0].startsWith("-")) {
            System.err.println(DiagnosticFormatter.formatError("no input file specified"));
            System.err.println("Usage: mira <file.mira> [flags]  |  Use -h for help.");
            System.exit(1);
        } else {
            Flags.inputPath.set(Paths.get(args[0]).toAbsolutePath().normalize());
        }

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    System.out.println(Help.getHelp());
                    System.exit(0);
                }
                case "-v", "--version" -> {
                    System.out.println("mira " + Flags.VERSION);
                    System.exit(0);
                }
                case "-t", "--tokens" ->
                    Flags.dumpTokens = true;
                case "--check-only" ->
                    Flags.checkOnly = true;
                case "-m", "--main" ->
                    Flags.mainFunction = true;
                case "--imports" ->
                    Flags.libInfo = true;
                case "--debug" ->
                    Flags.debug = true;
                case "--watch" ->
                    Flags.hotReload = true;
                case "--crash-dump" ->
                    Flags.crashDump = true;
                case "--test" ->
                    Flags.testMode = true;
                case "--ast" ->
                    Flags.printAsts = true;
                case "--compile" ->
                    Flags.compile = true;
                case "--run" ->
                    Flags.compileAndRun = true;
                case "-b", "--dump-bytecode" ->
                    Flags.dumpByteCode = true;
                case "-o", "--output" -> {
                    Flags.outputDir = Paths.get(args[i + 1]);
                    i++;
                }
                case "--package" ->
                    Flags.packageJar = true;
                case "--slim" ->
                    Flags.slimJar = true;
                case "--full" ->
                    Flags.slimJar = false;
                case "--no-check" ->
                    Flags.noCheck = true;
                case "--no-warn" ->
                    Flags.suppressWarnings = true;
                case "--no-color" ->
                    Flags.noColor = true;
                case "--verbose" ->
                    Flags.verbose = true;
                case "--profile" ->
                    Flags.profile = true;
                case "--" -> {
                    Flags.args = Arrays.copyOfRange(args, i + 1, args.length);
                    i = args.length;
                }
                default -> {
                    System.err.println(DiagnosticFormatter.formatError("'" + args[i] + "' is not a known flag"));
                    System.err.println("Use -h for help.");
                    System.exit(1);
                }
            }
        }

        if (Flags.compileAndRun && !Flags.compile) {
            System.err.println(DiagnosticFormatter.formatError("--run has no effect without --compile"));
            System.exit(1);
        }
        Flags.libInfoFull = Flags.libInfo && Flags.verbose;
        Flags.crashDumpFull = Flags.crashDump && Flags.verbose;
    }
}
