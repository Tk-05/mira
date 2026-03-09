package com.mira.parser.nodes.statement;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.runtime.visitors.StmtVisitor;

public abstract class Statement implements Node {

    public abstract <T> T accept(StmtVisitor<T> visitor);

    public static class VarDecl extends Statement {

        private final String name;
        private final Expression initializer;

        public VarDecl(String name, Expression initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        public String getName() {
            return name;
        }

        public Expression getInitializer() {
            return initializer;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitVarDecl(this);
        }
    }

    public static class FuncDecl extends Statement {

        private final String name;
        private final java.util.List<Expression.DumbExpression> parameters;
        private final java.util.List<Node> body;

        public FuncDecl(String name, java.util.List<Expression.DumbExpression> parameters,
                java.util.List<Node> body) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        public String getName() {
            return name;
        }

        public java.util.List<Expression.DumbExpression> getParameters() {
            return parameters;
        }

        public java.util.List<Node> getBody() {
            return body;
        }

        public int getArity() {
            return parameters.size();
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitFuncDecl(this);
        }
    }

    public static class Return extends Statement {

        private final Expression value;

        public Return(Expression value) {
            this.value = value;
        }

        public Expression getValue() {
            return value;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitReturn(this);
        }
    }

    public static class Assign extends Statement {

        private final String name;
        private final Expression expression;

        public Assign(String name, Expression expression) {
            this.name = name;
            this.expression = expression;
        }

        public String getName() {
            return name;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitAssign(this);
        }
    }
}
