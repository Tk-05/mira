package com.mira.error.parser;

import com.mira.lexer.token.Token;

public class ParserError extends RuntimeException {

    public ParserError(String message) {
        super(message);
    }

    public ParserError(Token token, String message) {
        super(message + " " + getTokenErrorInfo(token));
    }

    public static String getTokenErrorInfo(Token token) {
        return "at line " + token.getLine() + ":" + token.getColumn();
    }

    public static class UnexpectedToken extends ParserError {

        public UnexpectedToken(Token token, String message) {
            super(token, message);
        }
    }

    public static class LexemeMismatchError extends ParserError {

        public LexemeMismatchError(Token token, String message) {
            super(token, message);
        }
    }

    public static class TypeMismatchError extends ParserError {

        public TypeMismatchError(Token token, String message) {
            super(token, message);
        }
    }
}
