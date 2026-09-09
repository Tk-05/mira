package com.mira.repl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lib.internal.Internal;
import com.mira.parser.Parser;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.Function;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.Namespace;
import com.mira.runtime.values.NullValue;
import com.mira.warning.WarningCollector;

public class Repl {

    private static final String PROMPT = ">>> ";
    private static final String PROMPT_CONTINUE = "... ";
    private static final int MAX_HISTORY = 100;

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Interpreter interpreter = new Interpreter();

    private static final List<String> history = new ArrayList<>();
    private static Set<String> baseline = new HashSet<>();

    public static void run() {
        Flags.inputPath.set(Paths.get(".").toAbsolutePath().normalize());
        baseline = new HashSet<>(interpreter.getGlobalEnvironment().keySet());
        printBanner();

        while (true) {
            try {
                String input = readInput();

                if (input == null) {
                    System.out.println("Goodbye!");
                    System.out.flush();
                    break;
                }

                if (input.isBlank()) {
                    continue;
                }

                dispatch(input.strip());

            } catch (Exception e) {
                WarningCollector.flush();
                printError(e);
            }
        }
    }

    private static void dispatch(String input) {
        if (input.startsWith(":")) {
            runCommand(input.substring(1).strip());
        } else {
            runMira(input);
        }
    }

    private static void runCommand(String cmd) {
        if (cmd.equals("help") || cmd.equals("h") || cmd.equals("?")) {
            cmdHelp();
        } else if (cmd.equals("reset") || cmd.equals("r")) {
            cmdReset();
        } else if (cmd.equals("vars")) {
            cmdVars();
        } else if (cmd.equals("fns")) {
            cmdFns();
        } else if (cmd.startsWith("load ")) {
            cmdLoad(cmd.substring(5).strip());
        } else if (cmd.equals("history")) {
            cmdHistory();
        } else {
            System.err.println("Unknown command: :" + cmd + "  (type :help for a list)");
            System.err.flush();
        }
    }

    private static void runMira(String input) {
        if (input.startsWith("!")) {
            input = resolveHistory(input);
            if (input == null) {
                return;
            }
        }

        addHistory(input);

        String normalized = input;
        if (!normalized.endsWith(";") && !normalized.endsWith("}")) {
            normalized += ';';
        }

        Flags.fileName = "<console>";
        Flags.sourceLines = normalized.split("\n", -1);

        Object result = interpreter
                .runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(normalized, false)));

        WarningCollector.flush();

        if (result != null) {
            System.out.println(result);
            System.out.flush();
        }
    }

    private static void cmdHelp() {
        System.out.println("REPL Commands:");
        System.out.println("  :help / :h / :?   Show this help");
        System.out.println("  :reset / :r        Clear all user-defined variables and functions");
        System.out.println("  :vars              List user-defined variables and their values");
        System.out.println("  :fns               List user-defined functions and their signatures");
        System.out.println("  :load <path>       Load and execute a .mira file in the current context");
        System.out.println("  :history           Show command history");
        System.out.println("  !<n>               Re-run history entry #n");
        System.out.println("  exit / quit        Exit the REPL");
        System.out.println("  clear              Clear the screen");
        System.out.flush();
    }

    private static void cmdReset() {
        interpreter.reset();
        baseline = new HashSet<>(interpreter.getGlobalEnvironment().keySet());
        System.out.println("Interpreter reset.");
        System.out.flush();
    }

    private static void cmdVars() {
        Environment env = interpreter.getGlobalEnvironment();
        TreeMap<String, Object> vars = new TreeMap<>();
        for (String key : env.keySet()) {
            if (baseline.contains(key)) {
                continue;
            }
            Object val = env.getOrNull(key);
            if (val instanceof Callable || val instanceof Namespace) {
                continue;
            }
            vars.put(key, val);
        }
        if (vars.isEmpty()) {
            System.out.println("(no user-defined variables)");
        } else {
            vars.forEach((name, val) -> System.out.println("  " + name + " = " + formatValue(val)));
        }
        System.out.flush();
    }

    private static void cmdFns() {
        Environment env = interpreter.getGlobalEnvironment();
        TreeMap<String, Function> fns = new TreeMap<>();
        for (String key : env.keySet()) {
            if (baseline.contains(key)) {
                continue;
            }
            Object val = env.getOrNull(key);
            if (val instanceof Function f) {
                fns.put(key, f);
            }
        }
        if (fns.isEmpty()) {
            System.out.println("(no user-defined functions)");
        } else {
            fns.forEach((name, f) -> System.out.println("  fn " + name + formatParams(f)));
        }
        System.out.flush();
    }

    private static void cmdLoad(String pathStr) {
        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            path = Flags.inputPath.get().getParent() != null ? Flags.inputPath.get().getParent().resolve(path) : path;
        }
        if (!Files.exists(path)) {
            System.err.println("File not found: " + path);
            System.err.flush();
            return;
        }
        try {
            String source = Files.readString(path);
            Path previousPath = Flags.inputPath.get();
            Flags.inputPath.set(path.toAbsolutePath().normalize());
            Flags.fileName = path.getFileName().toString();
            Flags.sourceLines = source.split("\n", -1);

            interpreter.runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(source, false)));

            WarningCollector.flush();
            Flags.inputPath.set(previousPath);
            System.out.println("Loaded: " + path.getFileName());
            System.out.flush();
        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
            System.err.flush();
        }
    }

    private static void cmdHistory() {
        if (history.isEmpty()) {
            System.out.println("(no history)");
        } else {
            for (int i = 0; i < history.size(); i++) {
                System.out.printf("  [%d] %s%n", i + 1, history.get(i));
            }
        }
        System.out.flush();
    }

    private static String resolveHistory(String input) {
        try {
            int n = Integer.parseInt(input.substring(1).strip());
            if (n < 1 || n > history.size()) {
                System.err.println("No history entry #" + n);
                System.err.flush();
                return null;
            }
            String entry = history.get(n - 1);
            System.out.println(PROMPT + entry);
            System.out.flush();
            return entry;
        } catch (NumberFormatException e) {
            return input;
        }
    }

    private static void addHistory(String input) {
        if (!input.startsWith("!")) {
            if (history.isEmpty() || !history.get(history.size() - 1).equals(input)) {
                history.add(input);
                if (history.size() > MAX_HISTORY) {
                    history.remove(0);
                }
            }
        }
    }

    private static String readInput() throws InterruptedException {
        System.out.print(PROMPT);
        System.out.flush();

        String firstLine = Internal.readLine();
        if (firstLine == null) {
            return null;
        }

        String stripped = firstLine.strip();
        if (stripped.equalsIgnoreCase("exit") || stripped.equalsIgnoreCase("quit")) {
            return null;
        }
        if (stripped.equalsIgnoreCase("clear")) {
            System.out.print("\033[2J\033[H");
            System.out.flush();
            return "";
        }

        StringBuilder buffer = new StringBuilder(firstLine);

        while (isIncomplete(buffer.toString())) {
            System.out.print(PROMPT_CONTINUE);
            System.out.flush();
            String next = Internal.readLine();
            if (next == null) {
                break;
            }
            buffer.append('\n').append(next);
        }

        return buffer.toString();
    }

    private static boolean isIncomplete(String input) {
        int braces = 0;
        int parens = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inString) {
                if (c == '\\' && i + 1 < input.length()) {
                    i++;
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"', '\'' -> {
                    inString = true;
                    stringChar = c;
                }
                case '{' -> braces++;
                case '}' -> braces--;
                case '(' -> parens++;
                case ')' -> parens--;
            }
        }

        if (braces > 0 || parens > 0) {
            return true;
        }

        String trimmed = input.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }
        return endsWithContinuation(trimmed);
    }

    private static boolean endsWithContinuation(String s) {
        String[] continuationSuffixes = {"->", "|>", "&&", "||", "??", "+", "-", "*", "/", "%", "**", "==", "!=", "<=",
                ">=", "<", ">", "&", "|", "^", ","};
        for (String suffix : continuationSuffixes) {
            if (s.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String formatValue(Object val) {
        if (val == null || val instanceof NullValue) {
            return "null";
        }
        if (val instanceof String s) {
            return "\"" + s + "\"";
        }
        if (val instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf(d.longValue());
            }
            return d.toString();
        }
        if (val instanceof Boolean b) {
            return b.toString();
        }
        if (val instanceof List<?> list) {
            return "[" + list.size() + " element" + (list.size() == 1 ? "" : "s") + "]";
        }
        if (val instanceof Namespace ns) {
            return "<module " + ns.getAlias() + ">";
        }
        if (val instanceof Environment) {
            return "<object>";
        }
        return String.valueOf(val);
    }

    private static String formatParams(Function f) {
        List<com.mira.parser.nodes.Parameter> params = f.getParameters();
        if (params.isEmpty()) {
            return "()";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).name());
            if (params.get(i).hasDefault()) {
                sb.append("?");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private static void printError(Exception e) {
        switch (e) {
            case com.mira.error.parser.MultipleParserErrors mpe ->
                mpe.getErrors().forEach(err -> System.err.println(DiagnosticFormatter.format(err)));
            case com.mira.error.resolver.MultipleStaticCheckErrors mse ->
                mse.getErrors().forEach(err -> System.err.println(DiagnosticFormatter.format(err)));
            default -> System.err.println(DiagnosticFormatter.format(e));
        }
        System.err.flush();
    }

    private static void printBanner() {
        System.out.println("Mira REPL  —  type :help for commands, exit to quit");
        System.out.println();
        System.out.flush();
    }
}
