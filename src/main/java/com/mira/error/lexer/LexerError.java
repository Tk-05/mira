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

    public static class UnexpectedSymbolError extends LexerError {
        public UnexpectedSymbolError(int line, int column, char c) {
            super("Unexpected symbol at line " + line + ":" + column + " '" + c + "'");
        }

        public UnexpectedSymbolError(int line, int column) {
            super("Unexpected symbol at line " + line + ":" + column);
        }
    }
}
