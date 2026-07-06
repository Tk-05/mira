package com.mira;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.mira.error.DiagnosticFormatter;

public class Flags {

    public static final ThreadLocal<Path> inputPath = new ThreadLocal<>();
    public static boolean dumpTokens = false;
    public static boolean exitBeforeInterpreter = false;
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

    public static boolean skipStaticCheck = false;
    public static boolean suppressWarnings = false;

    public static boolean profile = false;

    public static void parse(String[] args) {
        if (args[0].equals("-h") || args[0].equals("-help")) {
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
                case "-profile" ->
                    Flags.profile = true;
                default -> {
                    System.err.println(DiagnosticFormatter.formatError("'" + args[i] + "' is not a known flag"));
                    System.err.println("Use -h for help.");
                    System.exit(1);
                }
            }
        }
    }
}
