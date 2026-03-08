package com.mira.error.lexer;

public class LexerError extends RuntimeException {
    public LexerError(String message) {
        super(message);
    }

    public static class UnterminatedStringError extends LexerError{
        public UnterminatedStringError(int line) {
            super("Unterminated string at line " + line);
        }
    }
}
