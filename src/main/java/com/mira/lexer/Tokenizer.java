package com.mira.lexer;

import java.util.ArrayList;
import java.util.List;

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

                if (Vocabulary.stringIsOperation(String.valueOf(c))) {
                    scanOperator(c);
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

    private void scanOperator(char first) {

        if (!isAtEnd()) {

            String two = "" + first + peekSafe();

            if (Vocabulary.stringIsOperation(two)) {
                advance();
                tokens.add(new Token(TokenType.OPERATION, two, line, column));
                return;
            }
        }

        tokens.add(new Token(TokenType.OPERATION, String.valueOf(first), line, column));
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

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, line, column));
    }
}
