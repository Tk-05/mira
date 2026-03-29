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
    private boolean ignoreSequences;

    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 0;

    private void reset() {
        start = 0;
        current = 0;
        line = 1;
        column = 0;
        tokens.clear();
    }

    public List<Token> tokenize(String source, boolean ignoreSequences) {
        this.source = source;
        this.ignoreSequences = ignoreSequences;
        reset();

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
                if (ignoreSequences) {
                    addToken(TokenType.EXPRESSION);
                } else {
                    line++;
                    column = 0;
                }
            }

            case ' ', '\r', '\t' -> {
                if (ignoreSequences) {
                    addToken(TokenType.EXPRESSION);
                }
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

                throw new UnexpectedSymbolError(line, column, c);
            }
        }
    }

    private void scanString() {
        StringBuilder valueBuilder = new StringBuilder();

        while (!isAtEnd() && peek() != '"') {

            if (peek() == '\\') {
                advance();

                if (isAtEnd()) {
                    throw new UnterminatedStringError(line);
                }

                char escaped = peek();

                switch (escaped) {
                    case 'n' ->
                        valueBuilder.append('\n');
                    case 't' ->
                        valueBuilder.append('\t');
                    case '"' ->
                        valueBuilder.append('"');
                    case '\\' ->
                        valueBuilder.append('\\');
                    default ->
                        valueBuilder.append(escaped);
                }

            } else {
                if (peek() == '\n') {
                    line++;
                    column = 0;
                }

                valueBuilder.append(peek());
            }

            advance();
        }

        if (isAtEnd()) {
            throw new UnterminatedStringError(line);
        }

        advance();

        tokens.add(new Token(
                TokenType.STRING_LITERAL,
                valueBuilder.toString(),
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

            do {
                advance();
            } while (!isAtEnd() && Character.isDigit(peek()));
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
