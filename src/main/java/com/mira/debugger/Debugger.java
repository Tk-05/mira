package com.mira.debugger;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.Interpreter.StackFrame;
import com.mira.utils.FileLoader;

public class Debugger {

    private static final String PROMPT = "dbg> ";
    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Scanner scanner = new Scanner(System.in);

    private static final HashSet<Integer> breakpoints = new HashSet<>();
    private static final Set<String> watchVars = new LinkedHashSet<>();
    private static boolean stepMode = false;
    private static List<Node> asts;
    private static Interpreter interpreter;

    public static void run() {
        printBanner();

        String source = loadSource();
        if (source == null) {
            return;
        }

        try {
            List<Token> tokens = tokenizer.tokenize(source, false);
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
            String[] input = scanner.nextLine().trim().split(" ", 2);

            switch (input[0]) {
                case "exit" -> {
                    return;
                }
                case "break" ->
                    addBreakpoint(input);
                case "watch" ->
                    addWatch(input);
                case "unwatch" ->
                    removeWatch(input);
                case "watches" ->
                    listWatches();
                case "help" ->
                    printHelp();
                case "run" -> {
                    execute();
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

    private static void execute() {
        interpreter = new Interpreter();
        interpreter.setDebugHook((line, env) -> {
            if (line > 0 && (stepMode || breakpoints.contains(line))) {
                stepMode = false;
                pauseAt(line, env);
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

    private static void pauseAt(int line, Environment env) {
        System.out.println();
        System.out.printf("  Paused at line %d: %s%n", line, sourceLine(line));

        for (String name : watchVars) {
            Object value = env.getOrNull(name);
            System.out.printf("  [watch] %s = %s%n", name, value != null ? value : "null");
        }

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
                    addBreakpoint(parts);
                case "print", "view" ->
                    printVar(parts, env);
                case "list", "dump" ->
                    dumpEnv(env);
                case "stack", "bt" ->
                    printStack();
                case "watch" ->
                    addWatch(parts);
                case "unwatch" ->
                    removeWatch(parts);
                case "watches" ->
                    listWatches();
                case "set" ->
                    setVar(parts, env);
                case "exit" ->
                    System.exit(0);
                case "help" ->
                    printPauseHelp();
                default ->
                    System.err.println("Unknown command: '" + parts[0] + "'");
            }
        }
    }

    private static void addBreakpoint(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            System.err.println("Usage: break <line> [line2 ...]  |  break fn <name>");
            return;
        }
        String rest = args[1].trim();
        if (rest.equals("fn") || rest.startsWith("fn ")) {
            String[] names = rest.substring(2).trim().split("\\s+");
            addFunctionBreakpoint(names);
            return;
        }
        try {
            for (String part : rest.split("[,\\s]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    breakpoints.add(Integer.valueOf(trimmed));
                    System.out.println("Breakpoint set at line " + trimmed);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid line number. Use 'break fn <name>' for function breakpoints.");
        }
    }

    private static void addFunctionBreakpoint(String[] names) {
        if (asts == null) {
            System.err.println("No file loaded.");
            return;
        }
        for (String name : names) {
            if (name.isBlank()) {
                continue;
            }
            boolean found = false;
            for (Node node : asts) {
                if (node instanceof FuncDecl f && f.getName().equals(name)) {
                    breakpoints.add(f.line);
                    System.out.printf("Breakpoint set at fn '%s' → line %d%n", name, f.line);
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.printf("Function '%s' not found in %s%n", name, Flags.fileName);
            }
        }
    }

    private static void listBreakpoints() {
        if (breakpoints.isEmpty()) {
            System.out.println("No breakpoints set.");
        } else {
            System.out.println("Breakpoints: " + breakpoints);
        }
    }

    private static void printStack() {
        if (interpreter == null) {
            System.out.println("Not running.");
            return;
        }
        List<StackFrame> stack = interpreter.getCallStack();
        if (stack.isEmpty()) {
            System.out.println("  #0  <top level>");
        } else {
            for (int i = 0; i < stack.size(); i++) {
                StackFrame f = stack.get(i);
                System.out.printf("  #%d  %-20s (line %d)%n", i, f.name(), f.line());
            }
        }
    }

    private static void addWatch(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            System.err.println("Usage: watch <variable>");
            return;
        }
        String name = parts[1].trim();
        watchVars.add(name);
        System.out.println("Watching '" + name + "'");
    }

    private static void removeWatch(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            System.err.println("Usage: unwatch <variable>");
            return;
        }
        String name = parts[1].trim();
        if (watchVars.remove(name)) {
            System.out.println("Removed watch for '" + name + "'");
        } else {
            System.err.println("No watch for '" + name + "'");
        }
    }

    private static void listWatches() {
        if (watchVars.isEmpty()) {
            System.out.println("No active watches.");
        } else {
            System.out.println("Watches: " + watchVars);
        }
    }

    private static void setVar(String[] parts, Environment env) {
        if (parts.length < 2 || parts[1].isBlank()) {
            System.err.println("Usage: set <variable> <value>");
            return;
        }
        String[] tokens = parts[1].trim().split("\\s+", 2);
        if (tokens.length < 2) {
            System.err.println("Usage: set <variable> <value>");
            return;
        }
        String name = tokens[0];
        Object value = parseValue(tokens[1].trim());
        env.forceDefine(name, value);
        System.out.printf("$%s set to %s%n", name, value);
    }

    private static Object parseValue(String raw) {
        if (raw.equals("true")) {
            return true;
        }
        if (raw.equals("false")) {
            return false;
        }
        if (raw.equals("null")) {
            return null;
        }
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
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
        Object value = env.getOrNull(name);
        System.out.println(name + " = " + (value != null ? value : "null"));
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

    private static void printBanner() {
        System.out.println("Mira Debugger");
        System.out.println("File: " + Flags.inputPath.get().getFileName());
        System.out.println();
    }

    private static void printHelp() {
        System.out.println("""
            Before execution:
              break <n> [n2 ...]   Set breakpoint(s) at line(s)
              break fn <name>      Set breakpoint at function start
              list                 List breakpoints
              watch <var>          Watch a variable (shown on every pause)
              unwatch <var>        Remove a watch
              watches              List all watches
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
              stack / bt           Show call stack
              break <n>            Add a breakpoint
              break fn <name>      Add a function breakpoint
              print <var>          Print variable value
              dump / list          Dump all variables in scope
              set <var> <value>    Modify a variable
              watch <var>          Watch a variable
              unwatch <var>        Remove a watch
              watches              List all watches
              exit                 Terminate program
            """);
    }
}
