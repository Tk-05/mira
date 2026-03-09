package com.mira.parser.nodes.expression;

import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.parser.nodes.Node;
import com.mira.runtime.visitors.ExprVisitor;

public abstract class Expression implements Node{

    public abstract <T> T accept(ExprVisitor<T> visitor);

    public static class DumbExpression extends Expression {

        private final Token token;

        public DumbExpression(Token token) {
            this.token = token;
        }

        public String getValue() {
            return token.getLexeme();
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitValueExpr(this);
        }
    }

    public static class UnaryExpression extends Expression {

        private final Token operation;
        private final Expression right;

        public UnaryExpression(Token operation, Expression right) {
            this.operation = operation;
            this.right = right;
        }

        public Token getOperation() {
            return operation;
        }

        public Expression getRight() {
            return right;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitUnaryExpr(this);
        }
    }

    public static class ComplexExpression extends Expression {

        private final List<Expression> expressions;

        public ComplexExpression(List<Expression> expressions) {
            this.expressions = expressions;
        }

        public List<Expression> getExpressions() {
            return expressions;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitComplexExpr(this);
        }
    }

    public static class CallExpression extends Expression {

        private final Expression callee;
        private final List<Expression> arguments;

        public CallExpression(Expression callee, List<Expression> arguments) {
            this.callee = callee;
            this.arguments = arguments;
        }

        public Expression getCallee() {
            return callee;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitCallExpr(this);
        }
    }
}
