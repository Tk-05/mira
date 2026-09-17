package com.mira.lexer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.error.lexer.LexerError.InvalidEscapeSequenceError;
import com.mira.error.lexer.LexerError.UnexpectedCharacterError;
import com.mira.error.lexer.LexerError.UnterminatedStringError;
import com.mira.error.lexer.MultipleLexerErrors;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.vocabulary.Vocabulary;

public class LexerTest {

    Tokenizer tokenizer = new Tokenizer();

    @Test
    void testKeywords() {
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("var", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("return", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("fn", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("if", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("else", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("for", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("while", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("import", false).getFirst().getTokenType());
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
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("native", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("do", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("finally", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("await", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("async", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("typeof", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("pure", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("test", false).getFirst().getTokenType());
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
        assertEquals(TokenType.OPERATION, tokenizer.tokenize(":", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("!", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("+:", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("-:", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("*:", false).getFirst().getTokenType());
        assertEquals(TokenType.OPERATION, tokenizer.tokenize("/:", false).getFirst().getTokenType());
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
        String functionDeclaration = "fn foo() {print}";
        List<Token> tokens = tokenizer.tokenize(functionDeclaration, false);
        assertEquals(tokens.get(0).getLexeme(), "fn");
        assertEquals(tokens.get(1).getLexeme(), "foo");
        assertEquals(tokens.get(2).getLexeme(), "(");
        assertEquals(tokens.get(3).getLexeme(), ")");
        assertEquals(tokens.get(4).getLexeme(), "{");
        assertEquals(tokens.get(5).getLexeme(), "print");
        assertEquals(tokens.get(6).getLexeme(), "}");
    }

    @Test
    void testFunctionCall() {
        String functionCall = "foo()";
        List<Token> tokens = tokenizer.tokenize(functionCall, false);
        assertEquals(tokens.get(0).getLexeme(), "foo");
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
        String complexExpression = "(((val1 + val3) + val()) + 1)";
        List<Token> tokens = tokenizer.tokenize(complexExpression, false);
        assertEquals(tokens.get(0).getLexeme(), "(");
        assertEquals(tokens.get(1).getLexeme(), "(");
        assertEquals(tokens.get(2).getLexeme(), "(");
        assertEquals(tokens.get(3).getLexeme(), "val1");
        assertEquals(tokens.get(4).getLexeme(), "+");
        assertEquals(tokens.get(5).getLexeme(), "val3");
        assertEquals(tokens.get(6).getLexeme(), ")");
        assertEquals(tokens.get(7).getLexeme(), "+");
        assertEquals(tokens.get(8).getLexeme(), "val");
        assertEquals(tokens.get(9).getLexeme(), "(");
        assertEquals(tokens.get(10).getLexeme(), ")");
        assertEquals(tokens.get(11).getLexeme(), ")");
        assertEquals(tokens.get(12).getLexeme(), "+");
        assertEquals(tokens.get(13).getLexeme(), "1");
        assertEquals(tokens.get(14).getLexeme(), ")");
    }

    @Test
    void testUnterminatedString() {
        String unterminatedString = "\"Hello World";
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class,
                () -> tokenizer.tokenize(unterminatedString, false));
        assertEquals(1, ex.getErrors().size());
        assertTrue(ex.getErrors().getFirst() instanceof UnterminatedStringError);
    }

    @Test
    void testUnexpectedSymbol() {
        String unexpectedSymbol = "@";
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class,
                () -> tokenizer.tokenize(unexpectedSymbol, false));
        assertEquals(1, ex.getErrors().size());
        assertTrue(ex.getErrors().getFirst() instanceof UnexpectedCharacterError);
    }

    @Test
    void testMultipleUnexpectedSymbolsAreAllCollected() {
        String source = "@ # `";
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class, () -> tokenizer.tokenize(source, false));
        assertEquals(3, ex.getErrors().size());
        assertTrue(ex.getErrors().stream().allMatch(e -> e instanceof UnexpectedCharacterError));
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

    @Test
    void testTextBlock() {
        List<Token> tokens = tokenizer.tokenize("\"\"\"Hello World\"\"\"", false);
        assertEquals(TokenType.STRING_LITERAL, tokens.getFirst().getTokenType());
        assertEquals("Hello World", tokens.getFirst().getLexeme());
    }

    @Test
    void testTextBlockStripsLeadingNewline() {
        List<Token> tokens = tokenizer.tokenize("\"\"\"\nHello World\"\"\"", false);
        assertEquals("Hello World", tokens.getFirst().getLexeme());
    }

    @Test
    void testTextBlockMultiLine() {
        List<Token> tokens = tokenizer.tokenize("\"\"\"\nline1\nline2\n\"\"\"", false);
        assertEquals("line1\nline2\n", tokens.getFirst().getLexeme());
    }

    @Test
    void testTextBlockEmpty() {
        List<Token> tokens = tokenizer.tokenize("\"\"\"\"\"\"", false);
        assertEquals("", tokens.getFirst().getLexeme());
    }

    @Test
    void testHexNumber() {
        List<Token> tokens = tokenizer.tokenize("0xFF", false);
        assertEquals("0xFF", tokens.getFirst().getLexeme());
    }

    @Test
    void testComparisonOperators() {
        for (String op : Vocabulary.COMPARISON_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for comparison operator: " + op);
        }
    }

    @Test
    void testLogicalOperators() {
        for (String op : Vocabulary.LOGICAL_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for logical operator: " + op);
        }
    }

    @Test
    void testArithmeticOperators() {
        for (String op : Vocabulary.ARITHMETIC_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for arithmetic operator: " + op);
        }
    }

    @Test
    void testBitwiseOperators() {
        for (String op : Vocabulary.BITWISE_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for bitwise operator: " + op);
        }
    }

    @Test
    void testCompoundAssignmentOperators() {
        for (String op : Vocabulary.COMPOUND_ASSIGNMENT_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for compound assignment operator: " + op);
        }
    }

    @Test
    void testUnaryOperators() {
        for (String op : Vocabulary.UNARY_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for unary operator: " + op);
        }
    }

    @Test
    void testSpecialOperators() {
        for (String op : Vocabulary.SPECIAL_OPERATORS) {
            assertEquals(TokenType.OPERATION, tokenizer.tokenize(op, false).getFirst().getTokenType(),
                    "Expected OPERATION for special operator: " + op);
        }
    }

    @Test
    void testVocabularySubsetsAreSubsetsOfOperations() {
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.COMPARISON_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.LOGICAL_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.ARITHMETIC_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.BITWISE_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.COMPOUND_ASSIGNMENT_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.UNARY_OPERATORS));
        assertTrue(Vocabulary.OPERATORS.containsAll(Vocabulary.SPECIAL_OPERATORS));
    }

    @Test
    void testMissingKeywords() {
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("break", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("module", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("case", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("catch", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("lock", false).getFirst().getTokenType());
        assertEquals(TokenType.KEYWORD, tokenizer.tokenize("comptime", false).getFirst().getTokenType());
    }

    @Test
    void testRangeDelimiters() {
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("..", false).getFirst().getTokenType());
        assertEquals("..", tokenizer.tokenize("..", false).getFirst().getLexeme());
        assertEquals(TokenType.DELIMITER, tokenizer.tokenize("...", false).getFirst().getTokenType());
        assertEquals("...", tokenizer.tokenize("...", false).getFirst().getLexeme());
    }

    @Test
    void testArrowOperator() {
        List<Token> tokens = tokenizer.tokenize("->", false);
        assertEquals(TokenType.OPERATION, tokens.getFirst().getTokenType());
        assertEquals("->", tokens.getFirst().getLexeme());
    }

    @Test
    void testFloorDivOperator() {
        List<Token> tokens = tokenizer.tokenize("\\%", false);
        assertEquals(TokenType.OPERATION, tokens.getFirst().getTokenType());
        assertEquals("\\%", tokens.getFirst().getLexeme());
    }

    @Test
    void testForeachIsNoLongerAKeyword() {
        List<Token> tokens = tokenizer.tokenize("foreach", false);
        assertEquals(TokenType.EXPRESSION, tokens.getFirst().getTokenType());
        assertEquals("foreach", tokens.getFirst().getLexeme());
    }

    @Test
    void testIntegerLiteral() {
        List<Token> tokens = tokenizer.tokenize("42", false);
        assertEquals(TokenType.EXPRESSION, tokens.getFirst().getTokenType());
        assertEquals("42", tokens.getFirst().getLexeme());
    }

    @Test
    void testFloatLiteral() {
        List<Token> tokens = tokenizer.tokenize("3.14", false);
        assertEquals(TokenType.EXPRESSION, tokens.getFirst().getTokenType());
        assertEquals("3.14", tokens.getFirst().getLexeme());
    }

    @Test
    void testAdditionalEscapeSequences() {
        assertEquals("Hello\tWorld", tokenizer.tokenize("\"Hello\\tWorld\"", false).getFirst().getLexeme());
        assertEquals("Hello\\World", tokenizer.tokenize("\"Hello\\\\World\"", false).getFirst().getLexeme());
    }

    @Test
    void testEmptyInput() {
        List<Token> tokens = tokenizer.tokenize("", false);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.getFirst().getTokenType());
    }

    @Test
    void testWhitespaceOnlyInput() {
        List<Token> tokens = tokenizer.tokenize("   \t\n  ", false);
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.getFirst().getTokenType());
    }

    @Test
    void testTokenCount() {
        List<Token> tokens = tokenizer.tokenize("var x : 10;", false);
        assertEquals(6, tokens.size());
    }

    @Test
    void testMultiLinePositions() {
        List<Token> tokens = tokenizer.tokenize("var x;\nvar y;", false);
        assertEquals(1, tokens.get(0).getLine());
        assertEquals(2, tokens.get(3).getLine());
    }

    @Test
    void testNestedBlockComment() {
        List<Token> tokens = tokenizer.tokenize("/* outer /* inner */ still outer */ var x;", false);
        assertEquals("var", tokens.get(0).getLexeme());
        assertEquals("x", tokens.get(1).getLexeme());
        assertEquals(";", tokens.get(2).getLexeme());
    }

    @Test
    void testTrailingDotIsFloat() {
        List<Token> tokens = tokenizer.tokenize("5.", false);
        assertEquals(TokenType.EXPRESSION, tokens.getFirst().getTokenType());
        assertEquals("5.", tokens.getFirst().getLexeme());
        assertEquals(5.0, Double.parseDouble(tokens.getFirst().getLexeme()));
    }

    @Test
    void testTrailingDotDoesNotSwallowRangeSeparator() {
        List<Token> tokens = tokenizer.tokenize("0..5", false);
        assertEquals("0", tokens.get(0).getLexeme());
        assertEquals("..", tokens.get(1).getLexeme());
        assertEquals("5", tokens.get(2).getLexeme());
    }

    @Test
    void testScientificNotation() {
        assertEquals("1e10", tokenizer.tokenize("1e10", false).getFirst().getLexeme());
        assertEquals("1.5e-3", tokenizer.tokenize("1.5e-3", false).getFirst().getLexeme());
        assertEquals("1E+2", tokenizer.tokenize("1E+2", false).getFirst().getLexeme());
    }

    @Test
    void testScientificNotationWithoutDigitFallsBackToPlainNumber() {
        List<Token> tokens = tokenizer.tokenize("1e", false);
        assertEquals("1", tokens.get(0).getLexeme());
        assertEquals("e", tokens.get(1).getLexeme());
    }

    @Test
    void testDigitSeparators() {
        assertEquals("1000", tokenizer.tokenize("1_000", false).getFirst().getLexeme());
        assertEquals("1000000", tokenizer.tokenize("1_000_000", false).getFirst().getLexeme());
        assertEquals("0xFFFF", tokenizer.tokenize("0xFF_FF", false).getFirst().getLexeme());
        assertEquals("1000.5", tokenizer.tokenize("1_000.5", false).getFirst().getLexeme());
    }

    @Test
    void testDigitSeparatorDoesNotConsumeTrailingUnderscore() {
        List<Token> tokens = tokenizer.tokenize("100_", false);
        assertEquals("100", tokens.get(0).getLexeme());
        assertEquals("_", tokens.get(1).getLexeme());
    }

    @Test
    void testUnicodeEscape() {
        assertEquals("A", tokenizer.tokenize("\"\\u0041\"", false).getFirst().getLexeme());
        assertEquals("héllo", tokenizer.tokenize("\"h\\u00e9llo\"", false).getFirst().getLexeme());
    }

    @Test
    void testUnknownEscapeThrows() {
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class, () -> tokenizer.tokenize("\"\\q\"", false));
        assertEquals(1, ex.getErrors().size());
        assertTrue(ex.getErrors().getFirst() instanceof InvalidEscapeSequenceError);
    }

    @Test
    void testIncompleteUnicodeEscapeThrows() {
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class, () -> tokenizer.tokenize("\"\\u12\"", false));
        assertEquals(1, ex.getErrors().size());
        assertTrue(ex.getErrors().getFirst() instanceof InvalidEscapeSequenceError);
    }

    @Test
    void testMultipleErrorsAcrossFileAreAllCollected() {
        String source = """
                var x : @;
                var y : #;
                """;
        MultipleLexerErrors ex = assertThrows(MultipleLexerErrors.class, () -> tokenizer.tokenize(source, false));
        assertEquals(2, ex.getErrors().size());
    }
}
