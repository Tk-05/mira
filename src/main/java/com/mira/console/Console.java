package com.mira.console;

import java.util.Scanner;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.interpreter.Interpreter;

public class Console {

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();
    private static Interpreter interpreter;

    private static final Scanner scanner = new Scanner(System.in);

    public static void run() {
        try {
            while (true) {
                interpreter = new Interpreter();
                System.out.print(">>");

                String input = getInput();
                if (!input.endsWith(";") && !input.equals("exit")) {
                    input += ';';
                }

                switch (input) {
                    case "exit" -> {
                        return;
                    }
                }

                Object result = interpreter.run(parser.parseTokens(tokenizer.tokenize(input, true)));

                if (result != null) {
                    System.out.println(result);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            run();
        }
    }

    private static String getInput() {
        return scanner.nextLine();
    }
}
