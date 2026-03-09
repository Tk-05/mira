package com.mira.parser.nodes.statement;

import java.util.List;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.runtime.visitors.StmtVisitor;

public abstract class Statement implements Node {

    public abstract void accept(StmtVisitor<Void> visitor);

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
        public void accept(StmtVisitor<Void> visitor) {
            visitor.visitVarDecl(this);
        }
    }

    public static class FuncDecl extends Statement {

        private final String name;
        private final List<DumbExpression> parameters;
        private final List<Node> body;

        public FuncDecl(String name, List<DumbExpression> parameters, List<Node> body) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        public String getName() {
            return name;
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
        public void accept(StmtVisitor<Void> visitor) {
            visitor.visitFuncDecl(this);
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
        public void accept(StmtVisitor<Void> visitor) {
            visitor.visitReturn(this);
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
        public void accept(StmtVisitor<Void> visitor) {
            visitor.visitAssign(this);
        }
    }
}
