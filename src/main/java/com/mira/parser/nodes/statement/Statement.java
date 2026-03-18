package com.mira.parser.nodes.statement;

import java.util.List;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
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
        private final List<DumbExpression> parameters;
        private final List<Node> body;

        public FuncDecl(String name, List<DumbExpression> parameters,
                List<Node> body) {
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

    public static class If extends Statement {

        private final Expression condition;
        private final List<Node> thenBody;
        private final List<Node> elseBody;

        public If(Expression condition, List<Node> ifBody, List<Node> elseBody) {
            this.condition = condition;
            this.thenBody = ifBody;
            this.elseBody = elseBody;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitIf(this);
        }

        public Expression getCondition() {
            return condition;
        }

        public List<Node> getThenBody() {
            return thenBody;
        }

        public List<Node> getElseBody() {
            return elseBody;
        }
    }

    public static class For extends Statement {

        private final List<Node> varDecls;
        private final List<Expression> conditions;
        private final List<Node> postExpressions;
        private final List<Node> body;

        public For(List<Node> varDecls, List<Expression> conditions, List<Node> postExpressions, List<Node> body) {
            this.varDecls = varDecls;
            this.conditions = conditions;
            this.postExpressions = postExpressions;
            this.body = body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitFor(this);
        }

        public List<Node> getVarDecls() {
            return varDecls;
        }

        public List<Expression> getConditions() {
            return conditions;
        }

        public List<Node> getPostExpressions() {
            return postExpressions;
        }

        public List<Node> getBody() {
            return body;
        }
    }
}
