package com.mira.error.parser;

import com.mira.error.MiraError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;

public class ParserError extends MiraError {

    protected ParserError(String errorCode, String message, int line, int column, String hint) {
        super(errorCode, message, line, column, hint);
    }

    public static class LexemeMismatchError extends ParserError {

        public LexemeMismatchError(Token token, String message) {
            super("E102",
                    message + ", but found '" + token.getLexeme() + "'",
                    token.getLine(), token.getColumn(),
                    token.getTokenType() == TokenType.EOF
                    ? "Unexpected end of file — check for unclosed blocks or missing terminators"
                    : null);
        }
    }

    public static class TypeMismatchError extends ParserError {

        public TypeMismatchError(Token token, String message) {
            super("E103",
                    message + ", but got '" + token.getLexeme() + "'",
                    token.getLine(), token.getColumn(),
                    null);
        }
    }

    public static class UnexpectedToken extends ParserError {

        public UnexpectedToken(Token token, String message) {
            super("E101",
                    message,
                    token.getLine(), token.getColumn(),
                    null);
        }
    }
}
