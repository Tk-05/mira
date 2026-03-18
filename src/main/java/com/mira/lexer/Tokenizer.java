package com.mira.lexer;

import java.util.ArrayList;
import java.util.List;

import com.mira.error.lexer.LexerError.UnexpectedSymbolError;
import com.mira.error.lexer.LexerError.UnterminatedStringError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.vocabulary.Vocabulary;

public class Tokenizer {

    private String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 0;

    public List<Token> tokenize(String source) {

        this.source = source;

        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private void scanToken() {

        char c = advance();

        switch (c) {

            case '\n' -> {
                line++;
                column = 0;
            }

            case ' ', '\r', '\t' -> {
            }

            case '"' ->
                scanString();

            default -> {

                if (Character.isDigit(c)) {
                    scanNumber();
                    return;
                }

                if (isIdentifierStart(c)) {
                    scanIdentifier();
                    return;
                }

                if (Vocabulary.stringIsDelimiter(String.valueOf(c))) {
                    addToken(TokenType.DELIMITER);
                    return;
                }

                if (startsOperator(c)) {
                    scanOperator();
                    return;
                }

                throw new RuntimeException("Unexpected character: " + c);
            }
        }
    }

    private void scanString() {

        while (!isAtEnd() && peek() != '"') {

            if (peek() == '\\') {
                source = source.substring(0, current) + source.substring(current + 1);
                advance();
            }

            if (peek() == '\n') {
                line++;
                column = 0;
            }

            advance();
        }

        if (isAtEnd()) {
            throw new UnterminatedStringError(line);
        }

        advance();

        String value = source.substring(start + 1, current - 1);

        tokens.add(new Token(
                TokenType.EXPRESSION,
                value,
                line,
                column
        ));
    }

    private void scanIdentifier() {

        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }

        String text = source.substring(start, current);

        TokenType type = Vocabulary.stringIsKeyword(text)
                ? TokenType.KEYWORD
                : TokenType.EXPRESSION;

        tokens.add(new Token(type, text, line, column));
    }

    private void scanNumber() {

        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }

        if (!isAtEnd() && peek() == '.' && Character.isDigit(peekNext())) {
            advance();

            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }

        String value = source.substring(start, current);

        tokens.add(new Token(
                TokenType.EXPRESSION,
                value,
                line,
                column
        ));
    }

    private void scanOperator() {
        String bestMatch = null;

        for (int len = 1; len <= Vocabulary.MAX_OPERATOR_LENGTH; len++) {

            if (start + len > source.length()) {
                break;
            }

            String candidate = source.substring(start, start + len);

            if (Vocabulary.stringIsOperation(candidate)) {
                bestMatch = candidate;
            }
        }

        if (bestMatch == null) {
            throw new UnexpectedSymbolError(line, column);
        }

        current = start + bestMatch.length();

        tokens.add(new Token(
                TokenType.OPERATION,
                bestMatch,
                line,
                column
        ));
    }

    private char advance() {
        column++;
        return source.charAt(current++);
    }

    private char peek() {
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(current + 1);
    }

    private char peekSafe() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(current);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    public static boolean startsOperator(char c) {
        for (String op : Vocabulary.operations) {
            if (op.charAt(0) == c) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, line, column));
    }
}
