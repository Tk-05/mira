package com.mira.runtime.interpreter;

import java.util.List;

import com.mira.error.parser.ParserError.LexemeMismatchError;
import com.mira.error.parser.ParserError.UnexpectedToken;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;

public class Evaluator {

    private static List<Token> tokens;
    private static int current;

    public static Object evaluate(String expression, boolean ignoreSequences) {
        Tokenizer tokenizer = new Tokenizer();
        tokens = tokenizer.tokenize(expression, ignoreSequences);
        current = 0;

        Object result = or();

        if (!isAtEnd()) {
            throw new UnexpectedToken(peek(), peek().getLexeme());
        }

        return result;
    }

    private static Object or() {
        Object left = and();

        while (match("||")) {
            Object right = and();
            left = toBoolean(left) || toBoolean(right);
        }

        return left;
    }

    private static Object and() {
        Object left = equality();

        while (match("&&")) {
            Object right = equality();
            left = toBoolean(left) && toBoolean(right);
        }

        return left;
    }

    private static Object equality() {
        Object left = comparison();

        while (match("==", "!=")) {
            String op = previous().getLexeme();
            Object right = comparison();

            if (op.equals("==")) {
                left = left.equals(right);
            } else {
                left = !left.equals(right);
            }
        }

        return left;
    }

    private static Object comparison() {
        Object left = term();

        while (match(">", "<", ">=", "<=")) {

            String op = previous().getLexeme();
            Object right = term();

            double l = toNumber(left);
            double r = toNumber(right);

            left = switch (op) {
                case ">" ->
                    l > r;
                case "<" ->
                    l < r;
                case ">=" ->
                    l >= r;
                case "<=" ->
                    l <= r;
                default ->
                    false;
            };
        }

        return left;
    }

    private static Object term() {
        Object left = factor();

        while (match("+", "-")) {

            String op = previous().getLexeme();
            Object right = factor();

            double l = toNumber(left);
            double r = toNumber(right);

            if (op.equals("+")) {
                left = l + r;
            } else {
                left = l - r;
            }
        }

        return left;
    }

    private static Object factor() {
        Object left = unary();

        while (match("*", "/")) {

            String op = previous().getLexeme();
            Object right = unary();

            double l = toNumber(left);
            double r = toNumber(right);

            if (op.equals("*")) {
                left = l * r;
            } else {
                left = l / r;
            }
        }

        return left;
    }

    private static Object unary() {
        if (match("!")) {
            return !toBoolean(unary());
        }

        if (match("-")) {
            return -toNumber(unary());
        }

        return primary();
    }

    private static Object primary() {
        if (match("(")) {
            Object value = or();

            if (!match(")")) {
                throw new LexemeMismatchError(peek(), "Expected ')'");
            }

            return value;
        }

        Token token = advance();

        if (token.getTokenType() == TokenType.EXPRESSION) {
            String value = token.getLexeme();

            if (isNumber(value)) {
                return Double.valueOf(value);
            }

            return value;
        }

        throw new UnexpectedToken(token, token.getLexeme());
    }

    private static boolean match(String... ops) {
        if (isAtEnd()) {
            return false;
        }

        for (String op : ops) {
            if (peek().getLexeme().equals(op)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private static Token advance() {
        return tokens.get(current++);
    }

    private static Token peek() {
        return tokens.get(current);
    }

    private static Token previous() {
        return tokens.get(current - 1);
    }

    private static boolean isAtEnd() {
        return peek().getTokenType() == TokenType.EOF;
    }

    private static boolean toBoolean(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }

        if (o instanceof Number n) {
            return n.doubleValue() != 0;
        }

        return o != null;
    }

    private static double toNumber(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }

        if (o instanceof String s && isNumber(s)) {
            return Double.parseDouble(s);
        }

        throw new NumberFormatException("Expected number but got " + o);
    }

    private static boolean isNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }
}
