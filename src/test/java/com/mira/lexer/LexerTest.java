package com.mira.lexer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.lexer.LexerError.UnexpectedCharacterError;
import com.mira.error.lexer.LexerError.UnterminatedStringError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;

public class LexerTest {

    Tokenizer tokenizer = new Tokenizer();

    @Test
    void testKeywords() {
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("var", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("ret", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("fn", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("if", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("else", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("for", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("while", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("import", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("overwrite", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("foreach", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("in", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("as", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("const", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("true", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("false", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("continue", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("null", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("switch", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("default", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("enum", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("try", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("throw", false).getFirst().getTokenType());
    }

    @Test
    void testOperations() {
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("+", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("-", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("*", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("/", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("==", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("!=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("<", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(">", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("<=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(">=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("&&", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("||", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("$", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(":", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("!", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("+=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("-=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("*=", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("/=", false).getFirst().getTokenType());
    }

    @Test
    void testDelimiters() {
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("(", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(")", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("{", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("}", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(";", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(",", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("[", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("]", false).getFirst().getTokenType());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize(".", false).getFirst().getTokenType());
    }

    @Test
    void testVariableDeclaration() {
        String varDeclaration = "var x : 10.1;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration, false);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ":");
        assertEquals(tokens.get(3).getLexeme(), "10.1");
        assertEquals(tokens.get(4).getLexeme(), ";");
    }

    @Test
    void testEmptyVariableDeclaration() {
        String varDeclaration = "var x;";
        List<Token> tokens = tokenizer.tokenize(varDeclaration, false);
        assertEquals(tokens.get(0).getLexeme(), "var");
        assertEquals(tokens.get(1).getLexeme(), "x");
        assertEquals(tokens.get(2).getLexeme(), ";");
    }

    @Test
    void testFunctionDeclaration() {
        String functionDeclaration = "fn test() {print}";
        List<Token> tokens = tokenizer.tokenize(functionDeclaration, false);
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
        List<Token> tokens = tokenizer.tokenize(functionCall, false);
        assertEquals(tokens.get(0).getLexeme(), "test");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), ")");
    }

    @Test
    void testExplicitString() {
        String explicitString = "\"Hello World\"";
        List<Token> tokens = tokenizer.tokenize(explicitString, false);
        assertEquals(tokens.get(0).getLexeme(), "Hello World");
    }

    @Test
    void testSimpleExpression() {
        String simpleExpression = "((1+2)+3)";
        List<Token> tokens = tokenizer.tokenize(simpleExpression, false);
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
        List<Token> tokens = tokenizer.tokenize(complexExpression, false);
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
        assertThrows(UnterminatedStringError.class, () -> tokenizer.tokenize(unterminatedString, false));
    }

    @Test
    void testUnexpectedSymbol() {
        String unexpectedSymbol = "@";
        assertThrows(UnexpectedCharacterError.class, () -> tokenizer.tokenize(unexpectedSymbol, false));
    }

    @Test
    void testSingleLineComment() {
        List<Token> tokens = tokenizer.tokenize("// this is a comment", false);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.getFirst().getTokenType());
    }

    @Test
    void testSingleLineCommentDoesNotConsumeNextLine() {
        List<Token> tokens = tokenizer.tokenize("// comment\nvar x;", false);
        assertEquals(TokenType.KEYWORD, tokens.get(0).getTokenType());
        assertEquals("var", tokens.get(0).getLexeme());
    }

    @Test
    void testSingleLineCommentInCode() {
        List<Token> tokens = tokenizer.tokenize("var x; // declare x", false);
        assertEquals("var", tokens.get(0).getLexeme());
        assertEquals("x", tokens.get(1).getLexeme());
        assertEquals(";", tokens.get(2).getLexeme());
        assertEquals(TokenType.EOF, tokens.get(3).getTokenType());
    }

    @Test
    void testMultiLineComment() {
        List<Token> tokens = tokenizer.tokenize("/* this is\na comment */", false);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.getFirst().getTokenType());
    }

    @Test
    void testMultiLineCommentInCode() {
        List<Token> tokens = tokenizer.tokenize("var /* comment */ x;", false);
        assertEquals("var", tokens.get(0).getLexeme());
        assertEquals("x", tokens.get(1).getLexeme());
        assertEquals(";", tokens.get(2).getLexeme());
        assertEquals(TokenType.EOF, tokens.get(3).getTokenType());
    }

    @Test
    void testDivisionOperatorNotConfusedWithComment() {
        List<Token> tokens = tokenizer.tokenize("10 / 2", false);
        assertEquals("10", tokens.get(0).getLexeme());
        assertEquals("/", tokens.get(1).getLexeme());
        assertEquals("2", tokens.get(2).getLexeme());
    }

    @Test
    void testEscapedString() {
        String escapedString = "\"Hello World\n\"";
        assertEquals("Hello World\n", tokenizer.tokenize(escapedString, false).getFirst().getLexeme());
        escapedString = "\"\\\"Hello World\\\"\"";
        assertEquals("\"Hello World\"", tokenizer.tokenize(escapedString, false).getFirst().getLexeme());
    }
}
