package com.mira.error.lexer;

import com.mira.error.MiraError;

public class LexerError extends MiraError {

    protected LexerError(String errorCode, String message, int line, int column, String hint) {
        super(errorCode, message, line, column, hint);
    }

    public static class UnterminatedStringError extends LexerError {

        public UnterminatedStringError(int line, int column) {
            super("E001", "Unterminated string literal", line, column,
                    "Add a closing '\"' to end the string");
        }
    }

    public static class UnexpectedCharacterError extends LexerError {

        public UnexpectedCharacterError(int line, int column, char c) {
            super("E002", "Unexpected character '" + c + "'", line, column,
                    "Remove or replace this character — it is not part of the Mira syntax");
        }

        public UnexpectedCharacterError(int line, int column) {
            super("E002", "Unexpected character", line, column,
                    "Remove or replace this character — it is not part of the Mira syntax");
        }
    }
}
