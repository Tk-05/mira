package com.mira.debugger;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import com.mira.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.utils.FileLoader;

public class Debugger {

    private static final String PROMPT = "dbg> ";

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Scanner scanner = new Scanner(System.in);

    private static final HashSet<Integer> breakpoints = new HashSet<>();
    private static boolean stepMode = false;

    public static void run() {
        printBanner();

        String source = loadSource();
        if (source == null) {
            return;
        }

        List<Token> tokens;
        List<Node> asts;
        try {
            tokens = tokenizer.tokenize(source, false);
            asts = parser.parseTokens(tokens);
        } catch (Exception e) {
            System.err.println(DiagnosticFormatter.format(e));
            return;
        }

        System.out.println("File loaded. Set breakpoints, then type 'run'.");
        System.out.println();

        while (true) {
            System.out.print(PROMPT);
            if (!scanner.hasNextLine()) {
                return;
            }
            String[] input = scanner.nextLine().trim().split(" ");

            switch (input[0]) {
                case "exit" -> {
                    return;
                }
                case "break" ->
                    addBreakpoint(input);
                case "help" ->
                    printHelp();
                case "run" -> {
                    execute(asts);
                    return;
                }
                case "list" ->
                    listBreakpoints();
                default ->
                    System.err.println("Unknown command: '" + input[0] + "'. Type 'help' for help.");
            }
        }
    }

    private static String loadSource() {
        try {
            String source = FileLoader.readFileFromPath(Flags.inputPath.get().toString());
            Flags.fileName = Flags.inputPath.get().getFileName().toString();
            Flags.sourceLines = source.split("\n", -1);
            return source;
        } catch (IOException e) {
            System.err.println(DiagnosticFormatter.format(e));
            return null;
        }
    }

    private static void execute(List<Node> asts) {
        Interpreter interpreter = new Interpreter();
        interpreter.setDebugHook((stmt, env) -> {
            if (stmt.line > 0 && (stepMode || breakpoints.contains(stmt.line))) {
                stepMode = false;
                pause(stmt.line, env);
            }
        });

        System.out.println("Starting execution...");
        System.out.println();
        try {
            interpreter.run(asts, Flags.args, false);
        } catch (Exception e) {
            System.err.println(DiagnosticFormatter.format(e));
        }
        System.out.println();
        System.out.println("Execution finished.");
    }

    private static void pause(int line, Environment env) {
        System.out.println();
        System.out.printf("  Paused at line %d: %s%n", line, sourceLine(line));

        while (true) {
            System.out.print(PROMPT);
            if (!scanner.hasNextLine()) {
                return;
            }
            String[] parts = scanner.nextLine().trim().split(" ", 2);
            switch (parts[0]) {
                case "s", "step" -> {
                    stepMode = true;
                    return;
                }
                case "c", "continue" -> {
                    return;
                }
                case "break" ->
                    addBreakpoint(parts[0].equals("break") && parts.length > 1
                            ? new String[]{"break", parts[1]}
                            : new String[]{"break"});
                case "print", "view" ->
                    printVar(parts, env);
                case "list", "dump" ->
                    dumpEnv(env);
                case "exit" ->
                    System.exit(0);
                case "help" ->
                    printPauseHelp();
                default ->
                    System.err.println("Unknown command: '" + parts[0] + "'");
            }
        }
    }

    private static String sourceLine(int line) {
        if (Flags.sourceLines != null && line >= 1 && line <= Flags.sourceLines.length) {
            return Flags.sourceLines[line - 1].stripLeading();
        }
        return "<unknown>";
    }

    private static void printVar(String[] parts, Environment env) {
        if (parts.length < 2 || parts[1].isBlank()) {
            System.err.println("Usage: print <variable>");
            return;
        }
        String name = parts[1].trim();
        try {
            Object value = env.getOrNull(name);
            if (value == null) {
                System.out.println(name + " = null");
            } else {
                System.out.println(name + " = " + value);
            }
        } catch (Exception e) {
            System.err.println("Variable '" + name + "' not found.");
        }
    }

    private static void dumpEnv(Environment env) {
        System.out.println("  Variables in current scope:");
        for (String key : env.keySet()) {
            Object value = env.getOrNull(key);
            System.out.printf("    %-20s = %s%n", key, value);
        }
        if (env.keySet().isEmpty()) {
            System.out.println("    (empty)");
        }
    }

    private static void listBreakpoints() {
        if (breakpoints.isEmpty()) {
            System.out.println("No breakpoints set.");
        } else {
            System.out.println("Breakpoints: " + breakpoints);
        }
    }

    private static void addBreakpoint(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            System.err.println("Usage: break <line> [line2 ...]");
            return;
        }
        try {
            for (int i = 1; i < args.length; i++) {
                for (String part : args[i].split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        breakpoints.add(Integer.valueOf(trimmed));
                        System.out.println("Breakpoint set at line " + trimmed);
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid line number.");
        }
    }

    private static void printBanner() {
        System.out.println("Mira Debugger");
        System.out.println("File: " + Flags.inputPath.get().getFileName());
        System.out.println();
    }

    private static void printHelp() {
        System.out.println("""
            Before execution:
              break <n> [n2 ...]   Set breakpoint(s)
              list                 List breakpoints
              run                  Start execution
              help                 Show this help
              exit                 Quit
            """);
    }

    private static void printPauseHelp() {
        System.out.println("""
            While paused:
              s / step             Execute next statement
              c / continue         Continue to next breakpoint
              break <n>            Add a breakpoint
              print <var>          Print variable value
              dump / list          Dump all variables in scope
              exit                 Terminate program
            """);
    }
}
