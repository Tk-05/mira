package com.mira.parser.nodes.expression;

import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.statement.Statement.VarDecl;
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

    public static class RangeExpression extends Expression {

        private final Expression start;
        private final Expression end;
        private final Expression stepsize;

        public RangeExpression(Expression start, Expression end, Expression stepsize) {
            this.start = start;
            this.end = end;
            this.stepsize = stepsize;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitRangeExpression(this);
        }

        @Override
        public String toString() {
            throw new UnsupportedOperationException();
        }

        public Expression getStart() {
            return start;
        }

        public Expression getEnd() {
            return end;
        }

        public Expression getStepsize() {
            return stepsize;
        }
    }

    public static class ObjectExpression extends Expression {

        private final List<VarDecl> varDecls;

        public ObjectExpression(List<VarDecl> varDecls) {
            this.varDecls = varDecls;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitObjectExpression(this);
        }

        @Override
        public String toString() {
            throw new UnsupportedOperationException("Unimplemented method 'toString'");
        }

        public List<VarDecl> getVarDecls() {
            return varDecls;
        }
    }

    public static class FieldAccessExpression extends Expression {

        private final Expression object;
        private final String field;

        public FieldAccessExpression(Expression object, String field) {
            this.object = object;
            this.field = field;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitFieldAccessExpression(this);
        }

        @Override
        public String toString() {
            return object.toString() + "." + field;
        }

        public Expression getObject() {
            return object;
        }

        public String getField() {
            return field;
        }
    }

    public static class BinaryExpression extends Expression {

        private final Expression left;
        private final Token operator;
        private final Expression right;

        public BinaryExpression(Expression left, Token operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        public Expression getLeft() {
            return left;
        }

        public Token getOperator() {
            return operator;
        }

        public Expression getRight() {
            return right;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitBinaryExpr(this);
        }

        @Override
        public String toString() {
            return "(" + left + " " + operator.getLexeme() + " " + right + ")";
        }
    }

    public static class LambdaExpression extends Expression {

        private final List<DumbExpression> parameters;
        private final List<Node> body;

        public LambdaExpression(List<DumbExpression> parameters, List<Node> body) {
            this.parameters = parameters;
            this.body = body;
        }

        public List<DumbExpression> getParameters() {
            return parameters;
        }

        public List<Node> getBody() {
            return body;
        }

        public int getArity() {
            return parameters.size();
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitLambdaExpr(this);
        }

        @Override
        public String toString() {
            return "<lambda/" + parameters.size() + ">";
        }
    }
}
