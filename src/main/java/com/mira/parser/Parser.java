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
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.vocabulary.Vocabulary;

public class Parser {

    private List<Token> tokens;
    private int index;

    private void reset() {
        index = 0;
    }

    public List<Node> parseTokens(List<Token> tokens) {
        reset();
        this.tokens = tokens;

        List<Node> asts = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF) {
            asts.add(parseStatement(true));
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

    private Token peekOffset(int offset) {
        if (index + offset >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(index + offset);
    }

    private void consumeExpected(String lexeme) {
        if (!peek().getLexeme().equals(lexeme)) {
            throw new RuntimeException("Expected '" + lexeme + "' but got " + peek().getLexeme());
        }
        consume();
    }

    private boolean expectLexeme(String expectedLexeme) {
        if (!peek().getLexeme().equals(expectedLexeme)) {
            throw new LexemeMismatchError(peek(), "Expected '" + expectedLexeme + "'");
        }

        return true;
    }

    private Token matchLexeme(String expectedLexeme) {
        if (peek().getLexeme().equals(expectedLexeme)) {
            return consume();
        }

        throw new LexemeMismatchError(peek(), "Expected '" + expectedLexeme + "'");
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

    private Node parseStatement(boolean expectSemicolon) {
        Node node;

        switch (peek().getLexeme()) {
            case "var" -> {
                node = parseVarDecl();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "fn" ->
                node = parseFuncDecl();
            case "ret" -> {
                node = parseReturn();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "if" -> {
                node = parseIf();
            }
            case "for" -> {
                node = parseFor();
            }
            case "while" -> {
                node = parseWhile();
            }
            case "break" -> {
                node = parseBreak();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "$" -> {
                node = isAssignment() ? parseAssign() : parseExpression();

                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            default -> {
                node = parseExpression();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
        }

        return node;
    }

    private Expression parseExpression() {
        List<Expression> expressions = new ArrayList<>();

        while (!peek().getLexeme().equals(";") && !peek().getLexeme().equals(")")
                && !peek().getLexeme().equals(",")
                && !peek().getLexeme().equals("]")
                && !peek().getLexeme().equals("}")
                && peek().getTokenType() != TokenType.EOF) {

            Token current = peek();

            if (current.getLexeme().equals("$")) {
                Expression unary = parseUnaryExpression();

                if (peek().getLexeme().equals("[") || peek().getLexeme().equals("{")) {
                    expressions.add(parseAccessExpression(unary));
                } else {
                    expressions.add(unary);
                }

            } else if (current.getLexeme().equals("{")
                    && (expressions.isEmpty() || lastExpressionWasOperatorOrUnary(expressions))) {
                expressions.add(parseList());

            } else if (current.getLexeme().equals("[")
                    && (expressions.isEmpty() || lastExpressionWasOperatorOrUnary(expressions))) {
                expressions.add(parseTuple());

            } else if (Vocabulary.stringIsOperation(current.getLexeme())
                    && !current.getLexeme().equals("$")) {
                expressions.add(new UnaryExpression(consume(), null));

            } else if (current.getTokenType() == TokenType.EXPRESSION
                    && peekNextSafe().getLexeme().equals("(")) {
                expressions.add(parseCallExpression());

            } else if (current.getLexeme().equals("(")) {
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

    private boolean lastExpressionWasOperatorOrUnary(List<Expression> exprs) {
        Expression last = exprs.get(exprs.size() - 1);
        return last instanceof UnaryExpression || last instanceof ComplexExpression;
    }

    private Expression parseUnaryExpression() {
        Token operation = matchType(TokenType.OPERATION);
        Expression rhs;
        if (peek().getTokenType() != TokenType.OPERATION) {
            rhs = parseDumbExpression();
        } else {
            rhs = parseExpression();
        }
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

    private Expression parseAccessExpression(Expression accessedExpression) {
        List<Expression> indices = new ArrayList<>();

        while (peek().getLexeme().equals("[") || peek().getLexeme().equals("{")) {
            consume();

            Expression indexExpr = parseExpression();
            indices.add(indexExpr);

            if (peek().getLexeme().equals("}")) {
                matchLexeme("}");
            } else {
                matchLexeme("]");
            }
        }

        return new AccessExpression(accessedExpression, indices);
    }

    private Expression parseList() {
        matchLexeme("{");

        List<Expression> members = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            members.add(parseExpression());
            if (!peek().getLexeme().equals("}")) {
                matchLexeme(",");
            }
        }
        matchLexeme("}");

        return new ListExpression(members);
    }

    private Expression parseTuple() {
        matchLexeme("[");

        List<Expression> members = new ArrayList<>();
        while (!peek().getLexeme().equals("]")) {
            members.add(parseExpression());
            if (!peek().getLexeme().equals("]")) {
                matchLexeme(",");
            }
        }
        matchLexeme("]");

        return new TupleExpression(members);
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
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new FuncDecl(name, parameters, body);
    }

    private Node parseReturn() {
        consume();
        matchLexeme("(");
        Expression value;
        if (peek().getTokenType() == TokenType.EXPRESSION || peek().getTokenType() == TokenType.OPERATION) {
            value = parseExpression();
        } else {
            value = new DumbExpression(new Token(null, "0.0", -1, -1));
        }
        matchLexeme(")");
        return new Return(value);
    }

    private Node parseAssign() {
        Expression reference = parseUnaryExpression();
        if (peek().getLexeme().contains("[")) {
            reference = parseAccessExpression(reference);
        }
        matchLexeme(":");
        Expression expression = parseExpression();
        return new Assign(reference, expression);
    }

    private boolean isAssignment() {
        if (!peek().getLexeme().equals("$")) {
            return false;
        }

        int offset = 1;

        if (peekOffset(offset).getTokenType() != TokenType.EXPRESSION) {
            return false;
        }
        offset++;

        while (peekOffset(offset).getLexeme().equals("[")) {
            offset++;

            int bracketDepth = 1;

            while (bracketDepth > 0) {
                String lex = peekOffset(offset).getLexeme();

                if (lex.equals("[")) {
                    bracketDepth++;
                }
                if (lex.equals("]")) {
                    bracketDepth--;
                }

                offset++;
            }
        }

        return peekOffset(offset).getLexeme().equals(":");
    }

    private Node parseIf() {
        matchLexeme("if");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");

        matchLexeme("{");
        List<Node> thenBody = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            if (peek().getLexeme().equals("}")) {
                throw new UnexpectedToken(peek(), "Unexpected Token in if body");
            }
            thenBody.add(parseStatement(true));
        }
        matchLexeme("}");

        if (peek().getLexeme().equals("else")) {
            matchLexeme("else");
            matchLexeme("{");
            List<Node> elseBody = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                if (peek().getLexeme().equals("}")) {
                    throw new UnexpectedToken(peek(), "Unexpected Token in else body");
                }
                elseBody.add(parseStatement(true));
            }
            matchLexeme("}");

            return new If(condition, thenBody, elseBody);
        } else {
            return new If(condition, thenBody, null);
        }
    }

    private Node parseFor() {
        matchLexeme("for");
        matchLexeme("(");

        List<Node> varDecls = new ArrayList<>();
        while (true) {
            if (peek().getLexeme().equals(";")) {
                break;
            } else {
                varDecls.add(parseVarDecl());
                if (peek().getLexeme().equals(",")) {
                    matchLexeme(",");
                    expectLexeme("var");
                }
            }
        }
        matchLexeme(";");

        Expression condition = null;
        if (!peek().getLexeme().equals(";")) {
            condition = parseExpression();
        }
        matchLexeme(";");

        List<Node> postExpressions = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {
            postExpressions.add(parseStatement(false));
        }
        matchLexeme(")");

        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new For(varDecls, condition, postExpressions, body);
    }

    private Node parseWhile() {
        matchLexeme("while");
        matchLexeme("(");

        Expression condition = parseExpression();
        matchLexeme(")");
        matchLexeme("{");

        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new While(condition, body);
    }

    private Node parseBreak() {
        matchLexeme("break");
        matchLexeme("(");
        matchLexeme(")");
        return new Break();
    }
}
