package com.mira.lexer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.lexer.token.Token;

public class LexerTests {
    Tokenizer tokenizer = new Tokenizer();
    
    @Test
    void variableDeclaration() {
        String varDeclaration = "var x : 10;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ":");
        assertEquals(tokens.get(3).getLexeme(), "10");
        assertEquals(tokens.get(4).getLexeme(), ";");
    }

    @Test
    void functionDeclaration() {
        String functionDeclaration = "fn test() {print}";
        List<Token> tokens = tokenizer.tokenize(functionDeclaration);
        assertEquals(tokens.get(0).getLexeme(), "fn");
        assertEquals(tokens.get(1).getLexeme(), "test");
        assertEquals(tokens.get(2).getLexeme(), "(");
        assertEquals(tokens.get(3).getLexeme(), ")");
        assertEquals(tokens.get(4).getLexeme(), "{");
        assertEquals(tokens.get(5).getLexeme(), "print");
        assertEquals(tokens.get(6).getLexeme(), "}");
    }
}
