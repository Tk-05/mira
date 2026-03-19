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
    void emptyVariableDeclaration() {
        String varDeclaration = "var x;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ";");
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

    @Test
    void functionCall() {
        String functionCall = "test()";
        List<Token> tokens = tokenizer.tokenize(functionCall);
        assertEquals(tokens.get(0).getLexeme(), "test");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), ")");
    }

    @Test
    void explicitString() {
        String explicitString = "\"Hello World\"";
        List<Token> tokens = tokenizer.tokenize(explicitString);
        assertEquals(tokens.get(0).getLexeme(), "Hello World");
    }

    @Test
    void simpleExpression() {
        String simpleExpression = "((1+2)+3)";
        List<Token> tokens = tokenizer.tokenize(simpleExpression);
        assertEquals(tokens.get(0).getLexeme(), "(");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), "1");
        assertEquals(tokens.get(3).getLexeme(), "+");
        assertEquals(tokens.get(4).getLexeme(), "2");
        assertEquals(tokens.get(5).getLexeme(), ")");
        assertEquals(tokens.get(6).getLexeme(), "+");
        assertEquals(tokens.get(7).getLexeme(), "3");
        assertEquals(tokens.get(8).getLexeme(), ")");
    }

    @Test
    void complexExpression() {
        String complexExpression = "((($val1 + $val3) + val()) + 1)";
        List<Token> tokens = tokenizer.tokenize(complexExpression);
        assertEquals(tokens.get(0).getLexeme(), "(");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), "(");
        assertEquals(tokens.get(3).getLexeme(), "$");
        assertEquals(tokens.get(4).getLexeme(), "val1");
        assertEquals(tokens.get(5).getLexeme(), "+");
        assertEquals(tokens.get(6).getLexeme(), "$");
        assertEquals(tokens.get(7).getLexeme(), "val3");
        assertEquals(tokens.get(8).getLexeme(), ")");
        assertEquals(tokens.get(9).getLexeme(), "+");
        assertEquals(tokens.get(10).getLexeme(), "val");
        assertEquals(tokens.get(11).getLexeme(), "(");
        assertEquals(tokens.get(12).getLexeme(), ")");
        assertEquals(tokens.get(13).getLexeme(), ")");
        assertEquals(tokens.get(14).getLexeme(), "+");
        assertEquals(tokens.get(15).getLexeme(), "1");
        assertEquals(tokens.get(16).getLexeme(), ")");
    }
}
