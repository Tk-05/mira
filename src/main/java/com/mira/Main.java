package com.mira;

import java.io.IOException;
import java.util.List;

import com.mira.console.Console;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.Interpreter;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.utils.FileLoader;

public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            long start = System.currentTimeMillis();

            if (args[0].equals("-h") || args[0].equals("-help")) {
                System.out.println(Help.getHelp());
                System.exit(1);
            } else {
                Flags.inputPath = args[0];
            }

            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "-t" ->
                        Flags.dumpTokens = true;
                    case "-e" ->
                        Flags.exitBeforeInterpreter = true;
                    case "-c" ->
                        Flags.loadFromClasspath = true;
                }
            }

            String readFile = "";
            try {
                if (Flags.loadFromClasspath) {
                    readFile = FileLoader.readFileFromClassPath(Flags.inputPath);
                } else {
                    readFile = FileLoader.readFileFromPath(Flags.inputPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Tokenizer tokenizer = new Tokenizer();
            List<Token> tokens = tokenizer.tokenize(readFile);
            if (Flags.dumpTokens) {
                tokens.forEach(token -> System.out.println(token.getLexeme() + "-" + token.getTokenType() + "-" + token.getLine() + ";" + token.getColumn()));
            }

            Parser parser = new Parser();
            List<Node> asts = parser.parseTokens(tokens);
            if (Flags.exitBeforeInterpreter) {
                System.exit(1);
            }
            Interpreter interpreter = new Interpreter();

            try {
                interpreter.run(asts);
            } catch (ReturnSignal returnSignal) {
                System.out.println("Program exited with value: " + returnSignal.getValue() + " in " + (System.currentTimeMillis() - start) + " ms");
            }
        } else {
            Console.run();
        }
    }
}
