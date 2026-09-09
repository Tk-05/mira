package com.mira.lexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mira.error.MiraError;
import com.mira.error.lexer.LexerError;
import com.mira.error.lexer.LexerError.InvalidEscapeSequenceError;
import com.mira.error.lexer.LexerError.UnexpectedCharacterError;
import com.mira.error.lexer.LexerError.UnterminatedStringError;
import com.mira.error.lexer.MultipleLexerErrors;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.vocabulary.Vocabulary;

public class Tokenizer {

    private static final Set<Character> OPERATOR_START_CHARS;
    private static final Set<Character> DELIMITER_START_CHARS;

    static {
        OPERATOR_START_CHARS = new HashSet<>();
        for (String op : Vocabulary.OPERATORS) {
            OPERATOR_START_CHARS.add(op.charAt(0));
        }
        DELIMITER_START_CHARS = new HashSet<>();
        for (String d : Vocabulary.delimiters) {
            DELIMITER_START_CHARS.add(d.charAt(0));
        }
    }

    private String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<MiraError> errors = new ArrayList<>();
    private boolean ignoreSequences;

    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 0;
    private int tokenStartLine = 1;
    private int tokenStartColumn = 1;

    public List<Token> tokenize(String source, boolean ignoreSequences) {
        this.source = source;
        this.ignoreSequences = ignoreSequences;
        reset();

        while (!isAtEnd()) {
            start = current;
            try {
                scanToken();
            } catch (LexerError e) {
                errors.add(e);
                recoverFromError();
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));

        if (!errors.isEmpty()) {
            throw new MultipleLexerErrors(errors);
        }

        return tokens;
    }

    /**
     * Guarantees the scan makes forward progress after a bad token so the outer
     * loop in {@link #tokenize} can keep collecting further errors instead of
     * looping forever at the same position. Most error sites already consume at
     * least one character before throwing; this is a safety net for any that don't.
     */
    private void recoverFromError() {
        if (current == start && !isAtEnd()) {
            advance();
        }
    }

    private void reset() {
        start = 0;
        current = 0;
        line = 1;
        column = 0;
        tokenStartLine = 1;
        tokenStartColumn = 1;
        tokens.clear();
        errors.clear();
    }

    private void scanToken() {
        tokenStartLine = line;
        tokenStartColumn = column + 1;
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

            case '"' -> scanString();

            default -> {
                if (c == '/' && !isAtEnd() && peek() == '/') {
                    while (!isAtEnd() && peek() != '\n') {
                        advance();
                    }
                    return;
                }

                if (c == '/' && !isAtEnd() && peek() == '*') {
                    advance();
                    int depth = 1;
                    while (!isAtEnd() && depth > 0) {
                        if (peek() == '*' && peekNext() == '/') {
                            advance();
                            advance();
                            depth--;
                        } else if (peek() == '/' && peekNext() == '*') {
                            advance();
                            advance();
                            depth++;
                        } else {
                            if (peek() == '\n') {
                                line++;
                                column = 0;
                            }
                            advance();
                        }
                    }
                    return;
                }

                if (Character.isDigit(c)) {
                    scanNumber();
                    return;
                }

                if (isIdentifierStart(c)) {
                    scanIdentifier();
                    return;
                }

                if (startsDelimiter(c)) {
                    scanDelimiter();
                    return;
                }

                if (startsOperator(c)) {
                    scanOperator();
                    return;
                }

                throw new UnexpectedCharacterError(line, column, c);
            }
        }
    }

    private void scanString() {
        if (!isAtEnd() && peek() == '"' && peekAt(1) == '"') {
            advance();
            advance();
            scanTextBlock();
            return;
        }

        StringBuilder valueBuilder = new StringBuilder();

        while (!isAtEnd() && peek() != '"') {

            if (peek() == '\\') {
                advance();

                if (isAtEnd()) {
                    throw new UnterminatedStringError(line, column);
                }

                char escaped = peek();

                switch (escaped) {
                    case 'n' -> valueBuilder.append('\n');
                    case 't' -> valueBuilder.append('\t');
                    case 'r' -> valueBuilder.append('\r');
                    case '"' -> valueBuilder.append('"');
                    case '\\' -> valueBuilder.append('\\');
                    case 'u' -> {
                        advance();
                        StringBuilder hex = new StringBuilder();
                        boolean validEscape = true;
                        for (int i = 0; i < 4; i++) {
                            if (isAtEnd() || !isHexDigit(peek())) {
                                errors.add(new InvalidEscapeSequenceError(line, column, 'u'));
                                validEscape = false;
                                break;
                            }
                            hex.append(peek());
                            advance();
                        }
                        if (validEscape) {
                            valueBuilder.append((char) Integer.parseInt(hex.toString(), 16));
                        }
                        continue;
                    }
                    default -> {
                        errors.add(new InvalidEscapeSequenceError(line, column, escaped));
                        valueBuilder.append(escaped);
                    }
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
            throw new UnterminatedStringError(line, column);
        }

        advance();

        tokens.add(new Token(TokenType.STRING_LITERAL, valueBuilder.toString(), tokenStartLine, tokenStartColumn));
    }

    private void scanTextBlock() {
        StringBuilder valueBuilder = new StringBuilder();

        if (!isAtEnd() && peek() == '\n') {
            line++;
            column = 0;
            advance();
        }

        while (!isAtEnd()) {
            if (peek() == '"' && peekAt(1) == '"' && peekAt(2) == '"') {
                advance();
                advance();
                advance();
                tokens.add(
                        new Token(TokenType.STRING_LITERAL, valueBuilder.toString(), tokenStartLine, tokenStartColumn));
                return;
            }

            if (peek() == '\n') {
                line++;
                column = 0;
            }

            valueBuilder.append(advance());
        }

        throw new UnterminatedStringError(line, column);
    }

    private void scanIdentifier() {
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }

        String text = source.substring(start, current);

        TokenType type = Vocabulary.stringIsKeyword(text) ? TokenType.KEYWORD : TokenType.EXPRESSION;

        tokens.add(new Token(type, text, tokenStartLine, tokenStartColumn));
    }

    private void scanNumber() {
        if (source.charAt(start) == '0' && !isAtEnd() && (peek() == 'x' || peek() == 'X')) {
            advance();
            while (!isAtEnd() && (isHexDigit(peek()) || (peek() == '_' && isHexDigit(peekNext())))) {
                advance();
            }
            addNumberToken();
            return;
        }

        while (!isAtEnd() && (Character.isDigit(peek()) || (peek() == '_' && Character.isDigit(peekNext())))) {
            advance();
        }

        if (!isAtEnd() && peek() == '.' && peekNext() != '.') {
            advance();
            while (!isAtEnd() && (Character.isDigit(peek()) || (peek() == '_' && Character.isDigit(peekNext())))) {
                advance();
            }
        }

        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            int offset = 1;
            if (peekAt(offset) == '+' || peekAt(offset) == '-') {
                offset++;
            }
            if (Character.isDigit(peekAt(offset))) {
                advance();
                if (peek() == '+' || peek() == '-') {
                    advance();
                }
                while (!isAtEnd() && Character.isDigit(peek())) {
                    advance();
                }
            }
        }

        addNumberToken();
    }

    private void addNumberToken() {
        String text = source.substring(start, current);
        if (text.indexOf('_') >= 0) {
            text = text.replace("_", "");
        }
        tokens.add(new Token(TokenType.EXPRESSION, text, tokenStartLine, tokenStartColumn));
    }

    private void scanOperator() {
        String bestMatch = null;

        for (int len = Vocabulary.MAX_OPERATOR_LENGTH; len > 0; len--) {
            if (start + len > source.length()) {
                continue;
            }

            String candidate = source.substring(start, start + len);

            if (Vocabulary.stringIsOperation(candidate)) {
                bestMatch = candidate;
                break;
            }
        }

        if (bestMatch == null) {
            throw new UnexpectedCharacterError(line, column);
        }

        current = start + bestMatch.length();

        tokens.add(new Token(TokenType.OPERATION, bestMatch, tokenStartLine, tokenStartColumn));
    }

    private void scanDelimiter() {
        String bestMatch = null;

        for (int len = Vocabulary.MAX_OPERATOR_LENGTH; len > 0; len--) {
            if (start + len > source.length()) {
                continue;
            }

            String candidate = source.substring(start, start + len);

            if (Vocabulary.stringIsDelimiter(candidate)) {
                bestMatch = candidate;
                break;
            }
        }

        if (bestMatch == null) {
            throw new UnexpectedCharacterError(line, column);
        }

        current = start + bestMatch.length();

        tokens.add(new Token(TokenType.DELIMITER, bestMatch, tokenStartLine, tokenStartColumn));
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, tokenStartLine, tokenStartColumn));
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

    private char peekAt(int offset) {
        if (current + offset >= source.length()) {
            return '\0';
        }
        return source.charAt(current + offset);
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

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean startsOperator(char c) {
        return OPERATOR_START_CHARS.contains(c);
    }

    private boolean startsDelimiter(char c) {
        return DELIMITER_START_CHARS.contains(c);
    }
}
