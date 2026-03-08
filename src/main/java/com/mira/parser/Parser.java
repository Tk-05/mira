package com.mira.parser;

import java.util.ArrayList;
import java.util.List;

import com.mira.error.parser.ParserError.LexemeMismatchError;
import com.mira.error.parser.ParserError.TypeMismatchError;
import com.mira.error.parser.ParserError.UnexpectedToken;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.vocabulary.Vocabulary;

public class Parser {

    private List<Token> tokens;
    private int index;

    public List<Node> parseTokens(List<Token> tokens) {
        this.tokens = tokens;

        List<Node> asts = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF) {
            asts.add(parseStatement());
        }

        return asts;
    }

    private Token consume() {
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token peekNext() {
        return tokens.get(index + 1);
    }

    private Token peekNextSafe() {
        if (index + 1 >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(index + 1);
    }

    private void consumeExpected(String lexeme) {
        if (!peek().getLexeme().equals(lexeme)) {
            throw new RuntimeException("Expected '" + lexeme + "' but got " + peek().getLexeme());
        }
        consume();
    }

    private Token matchLexeme(String expectedLexeme) {
        if (peek().getLexeme().equals(expectedLexeme)) {
            return consume();
        }

        throw new LexemeMismatchError(peek(), "Expected " + expectedLexeme);
    }

    private Token matchType(TokenType expectedType) {
        if (peek().getTokenType() == TokenType.EOF) {
            return null;
        }

        if (peek().getTokenType() == expectedType) {
            return consume();
        }

        throw new TypeMismatchError(peek(), "Expected " + expectedType);
    }

    private Node parseStatement() {
        Node node;

        switch (peek().getLexeme()) {
            case "var" -> {
                node = parseVarDecl();
                matchLexeme(";");
            }
            case "fn" ->
                node = parseFuncDecl();
            case "ret" -> {
                node = parseReturn();
                matchLexeme(";");
            }
            default -> {
                node = parseExpression();
                matchLexeme(";");
            }
        }

        return node;
    }

    private Expression parseExpression() {
        List<Expression> expressions = new ArrayList<>();

        while (!peek().getLexeme().equals(";")
                && !peek().getLexeme().equals(")")
                && !peek().getLexeme().equals(",")
                && peek().getTokenType() != TokenType.EOF) {

            if (peek().getLexeme().equals("$")) {
                expressions.add(parseUnaryExpression());
            } else if (Vocabulary.stringIsOperation(peek().getLexeme())
                    && !peek().getLexeme().equals("$")) {

                expressions.add(new UnaryExpression(consume(), null));
            } else if (peek().getTokenType() == TokenType.EXPRESSION
                    && peekNextSafe().getLexeme().equals("(")) {

                expressions.add(parseCallExpression());
            } else if (peek().getLexeme().equals("(")) {
                consume();
                Expression inner = parseExpression();
                consumeExpected(")");
                expressions.add(inner);
            } else {
                expressions.add(parseDumbExpression());
            }
        }

        return (expressions.size() > 1)
                ? new ComplexExpression(expressions)
                : expressions.get(0);
    }

    private Expression parseUnaryExpression() {
        Token operation = matchType(TokenType.OPERATION);
        Expression rhs = parseDumbExpression();
        return new UnaryExpression(operation, rhs);
    }

    private Expression parseDumbExpression() {
        Token expr = matchType(TokenType.EXPRESSION);
        return new Expression.DumbExpression(expr);
    }

    private Expression parseCallExpression() {
        Token referencedFunction = matchType(TokenType.EXPRESSION);

        matchLexeme("(");

        List<Expression> args = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {

            args.add(parseExpression());

            if (peek().getLexeme().equals(",")) {
                matchLexeme(",");
            } else if (!peek().getLexeme().equals(")")) {
                throw new UnexpectedToken(peek(), "Expected ',' or ')'");
            }
        }

        matchLexeme(")");

        return new CallExpression(
                new DumbExpression(referencedFunction),
                args
        );
    }

    private Node parseVarDecl() {
        matchLexeme("var");
        String indentifier = consume().getLexeme();
        switch (peek().getLexeme()) {
            case ":" -> {
                consume();
                Expression expr = parseExpression();
                return new VarDecl(indentifier, expr);
            }
            case ";" -> {
                return new VarDecl(indentifier, null);
            }
            default -> {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }
        }
    }

    private Node parseFuncDecl() {
        matchLexeme("fn");
        String name = matchType(TokenType.EXPRESSION).getLexeme();
        matchLexeme("(");

        List<DumbExpression> parameters = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {
            if (peek().getLexeme().equals(",")) {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }

            if (peekNext().getLexeme().equals(")")) {
                parameters.add(new DumbExpression(matchType(TokenType.EXPRESSION)));
                break;
            } else {
                parameters.add(new DumbExpression(matchType(TokenType.EXPRESSION)));
                matchLexeme(",");
            }
        }
        matchLexeme(")");

        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            if (peek().getLexeme().equals("}")) {
                throw new UnexpectedToken(peek(), "Unexpected Token in function body");
            }
            body.add(parseStatement());
        }
        matchLexeme("}");

        return new FuncDecl(name, parameters, body);
    }

    private Node parseReturn() {
        consume();
        matchLexeme("(");
        Expression value = parseExpression();
        matchLexeme(")");
        return new Return(value);
    }
}
