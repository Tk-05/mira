package com.mira.console;

import java.util.Scanner;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.Interpreter;

public class Console {

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static final Interpreter interpreter = new Interpreter();

    private static final Scanner scanner = new Scanner(System.in);

    public static void run() {
        while (true) {
            System.out.print(">>");

            String input = getInput();

            switch (input) {
                case "exit" -> {
                    return;
                }
            }

            Object result = interpreter.run(
                    parser.parseTokens(tokenizer.tokenize(input))
            );

            if (result != null) {
                System.out.println(result);
            }
        }
    }

    private static String getInput() {
        return scanner.nextLine();
    }
}
