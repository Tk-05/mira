package com.mira.repl;

import java.nio.file.Paths;

import com.mira.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lib.internal.Internal;
import com.mira.parser.Parser;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.warning.WarningCollector;

public class Repl {

    private static final String PROMPT = ">>> ";
    private static final String PROMPT_CONTINUE = "... ";

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Interpreter interpreter = new Interpreter();

    public static void run() {
        Flags.inputPath.set(Paths.get(".").toAbsolutePath().normalize());
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

                String normalized = input.strip();
                if (!normalized.endsWith(";") && !normalized.endsWith("}")) {
                    normalized += ';';
                }

                Flags.fileName = "<console>";
                Flags.sourceLines = normalized.split("\n", -1);

                Object result = interpreter.runWithoutLoadingNewContext(
                        parser.parseTokens(tokenizer.tokenize(normalized, true)));

                WarningCollector.flush();

                if (result != null) {
                    System.out.println(result);
                    System.out.flush();
                }
            } catch (Exception e) {//Leave exception generic
                WarningCollector.flush();
                System.err.println(DiagnosticFormatter.format(e));
                System.err.flush();
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
                case '{' ->
                    braces++;
                case '}' ->
                    braces--;
                case '(' ->
                    parens++;
                case ')' ->
                    parens--;
            }
        }

        return braces > 0 || parens > 0;
    }

    private static void printBanner() {
        System.out.println("Mira REPL");
        System.out.println("Type 'exit' or 'quit' to quit, 'clear' to clear the screen.");
        System.out.println();
        System.out.flush();
    }
}
