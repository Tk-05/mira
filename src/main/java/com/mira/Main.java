package com.mira;

import java.io.IOException;
import java.util.List;

import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.Interpreter;
import com.mira.utils.FileLoader;

public class Main { 
    public static void main(String[] args) {
        String readFile = "";
        try {
            readFile = FileLoader.readFileFromClassPath("demo/HelloWorld.mira");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Tokenizer tokenizer = new Tokenizer();
        List<Token> tokens = tokenizer.tokenize(readFile);
        tokens.forEach(token -> System.out.println(token.getLexeme() + "-" + token.getTokenType() + "-" + token.getLine() + ";" + token.getColumn()));
        
        Parser parser = new Parser();
        List<Node> asts = parser.parseTokens(tokens);
        Interpreter interpreter = new Interpreter();
        interpreter.run(asts);
    }
}