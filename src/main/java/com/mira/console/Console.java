package com.mira.console;

import java.util.Scanner;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.interpreter.Interpreter;

public class Console {

    private static final String VERSION = "0.1.0";
    private static final String PROMPT = ">>> ";
    private static final String PROMPT_CONTINUE = "... ";

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Interpreter interpreter = new Interpreter();
    private static final Scanner scanner = new Scanner(System.in);

    public static void run() {
        printBanner();

        while (true) {
            try {
                String input = readInput();

                if (input == null) {
                    System.out.println("\nGoodbye!");
                    break;
                }

                if (input.isBlank()) {
                    continue;
                }

                if (input.strip().equals("exit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                String normalized = input.strip();
                if (!normalized.endsWith(";") && !normalized.endsWith("}")) {
                    normalized += ';';
                }

                Object result = interpreter.runWithoutLoadingNewContext(
                        parser.parseTokens(tokenizer.tokenize(normalized, true)));

                if (result != null) {
                    System.out.println(result);
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                System.err.println(msg != null ? msg : e.getClass().getSimpleName());
            }
        }
    }

    private static String readInput() {
        System.out.print(PROMPT);

        if (!scanner.hasNextLine()) {
            return null;
        }

        String firstLine = scanner.nextLine();

        if (firstLine.strip().equals("exit")) {
            return "exit";
        }

        StringBuilder buffer = new StringBuilder(firstLine);

        while (isIncomplete(buffer.toString())) {
            System.out.print(PROMPT_CONTINUE);
            if (!scanner.hasNextLine()) {
                break;
            }
            buffer.append('\n').append(scanner.nextLine());
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

        return braces > 0 || parens > 0;
    }

    private static void printBanner() {
        System.out.println("Mira " + VERSION + " interactive console");
        System.out.println("Type 'exit' to quit.");
        System.out.println();
    }
}
