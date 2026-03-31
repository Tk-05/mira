package com.mira.parser.nodes.expression;

import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.parser.nodes.Node;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.utils.Formatter;

public abstract class Expression implements Node {

    public static interface Mutability {

        public abstract boolean isMutable();
    }

    public abstract <T> T accept(ExprVisitor<T> visitor);

    @Override
    public abstract String toString();

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
            return visitor.visitDumbExpr(this);
        }

        @Override
        public String toString() {
            return token.getLexeme();
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

        @Override
        public String toString() {
            if (right != null) {
                return operation.getLexeme() + right.toString();
            } else {
                return operation.getLexeme();
            }
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

        @Override
        public String toString() {
            String value = "";
            for (Expression expression : expressions) {
                value += expression.toString();
            }
            return value;
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

        @Override
        public String toString() {
            return callee.toString();
        }
    }

    public static class TupleExpression extends Expression implements Mutability {

        private final List<Expression> members;

        public TupleExpression(List<Expression> members) {
            this.members = members;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitTupleExpr(this);
        }

        public int getLength() {
            return members.size();
        }

        public List<Expression> getMembers() {
            return members;
        }

        @Override
        public String toString() {
            return Formatter.formatToString(this);
        }

        @Override
        public boolean isMutable() {
            return false;
        }
    }

    public static class AccessExpression extends Expression {

        private final Expression reference;
        private final List<Expression> indecies;

        public AccessExpression(Expression reference, List<Expression> indecies) {
            this.reference = reference;
            this.indecies = indecies;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitAccessExpr(this);
        }

        @Override
        public String toString() {
            return reference.toString() + "[" + indecies.toString() + "]";
        }

        public Expression getReference() {
            return reference;
        }

        public List<Expression> getIndecies() {
            return indecies;
        }
    }

    public static class ListExpression extends Expression implements Mutability {

        private final List<Expression> members;

        public ListExpression(List<Expression> members) {
            this.members = members;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitListExpr(this);
        }

        @Override
        public String toString() {
            return Formatter.formatToString(this);
        }

        @Override
        public boolean isMutable() {
            return true;
        }

        public List<Expression> getMembers() {
            return members;
        }

        public int getLength() {
            return members.size();
        }
    }

    public static class ImportExpression extends Expression {

        private final Expression module;
        private final String namespace;
        private final boolean isModule;

        public ImportExpression(Expression module, String namespace, boolean isModule) {
            this.module = module;
            this.namespace = namespace;
            this.isModule = isModule;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return null;
        }

        @Override
        public String toString() {
            throw new AssertionError();
        }

        public String getModule() {
            return module.toString();
        }

        public String getNamespace() {
            return namespace;
        }

        public boolean isExternalModule() {
            return isModule;
        }
    }

    public static class NamespaceCallExpression extends Expression {

        private final String alias;
        private final String functionName;
        private final List<Expression> arguments;

        public NamespaceCallExpression(String alias, String functionName, List<Expression> arguments) {
            this.alias = alias;
            this.functionName = functionName;
            this.arguments = arguments;
        }

        public String getAlias() {
            return alias;
        }

        public String getFunctionName() {
            return functionName;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitNamespaceCallExpr(this);
        }

        @Override
        public String toString() {
            return alias + "." + functionName;
        }
    }
}
