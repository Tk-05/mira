package com.mira.lexer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.lexer.LexerError.UnexpectedSymbolError;
import com.mira.error.lexer.LexerError.UnterminatedStringError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;

public class LexerTests {

    Tokenizer tokenizer = new Tokenizer();

    @Test
    void testKeywords() {
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("var").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("ret").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("fn").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("if").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("else").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("for").getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("while").getFirst().getTokenType());
    }

    @Test
    void testOperations() {
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("+").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("-").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("*").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("/").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("==").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("!=").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("<").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(">").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("<=").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(">=").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("&&").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("||").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("$").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(":").getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("!").getFirst().getTokenType());
    }

    @Test
    void testDelimiters() {
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("(").getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(")").getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("{").getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("}").getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(";").getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(",").getFirst().getTokenType());
    }

    @Test
    void testVariableDeclaration() {
        String varDeclaration = "var x : 10.1;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ":");
        assertEquals(tokens.get(3).getLexeme(), "10.1");
        assertEquals(tokens.get(4).getLexeme(), ";");
    }

    @Test
    void testEmptyVariableDeclaration() {
        String varDeclaration = "var x;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ";");
    }

    @Test
    void testFunctionDeclaration() {
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
    void testFunctionCall() {
        String functionCall = "test()";
        List<Token> tokens = tokenizer.tokenize(functionCall);
        assertEquals(tokens.get(0).getLexeme(), "test");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), ")");
    }

    @Test
    void testExplicitString() {
        String explicitString = "\"Hello World\"";
        List<Token> tokens = tokenizer.tokenize(explicitString);
        assertEquals(tokens.get(0).getLexeme(), "Hello World");
    }

    @Test
    void testSimpleExpression() {
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
    void testComplexExpression() {
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

    @Test
    void testUnterminatedString() {
        String unterminatedString = "\"Hello World";
        assertThrows(UnterminatedStringError.class, () -> tokenizer.tokenize(unterminatedString));
    }

    @Test
    void testUnexpectedSymbol() {
        String unexpectedSymbol = "@";
        assertThrows(UnexpectedSymbolError.class, () -> tokenizer.tokenize(unexpectedSymbol));
    }
}
