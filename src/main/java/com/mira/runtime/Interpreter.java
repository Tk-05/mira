package com.mira.runtime;

import java.util.ArrayList;
import java.util.List;

import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.UnknownOperatorError;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.nativeFunction.NativeFunctions;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;

public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Object> {

    private static Interpreter instance;
    private static final Environment globalEnvironment = new Environment();
    private Environment localEnvironment;

    public Interpreter() {
        setup();
    }

    public static Interpreter getInstance() {
        if (instance == null) {
            instance = new Interpreter();
        }
        return instance;
    }

    private void setup() {
        NativeFunctions.defineNativeFunctions(globalEnvironment);
    }

    public <T> T run(List<Node> asts) {
        Object lastResult = null;

        for (Node ast : asts) {
            lastResult = switch (ast) {
                case Expression expression ->
                    expression.accept(this);
                case Statement statement ->
                    statement.accept(this);
                default -> {
                    throw new AssertionError();
                }
            };
        }

        return (T) lastResult;
    }

    @Override
    public Void visitVarDecl(VarDecl varDecl) {
        Object value = null;

        if (varDecl.getInitializer() != null) {
            value = varDecl.getInitializer().accept(this);
        }

        if (localEnvironment == null) {
            globalEnvironment.define(varDecl.getName(), value);
        } else {
            localEnvironment.define(varDecl.getName(), value);
        }

        return null;
    }

    @Override
    public <T> T visitValueExpr(DumbExpression expression) {
        return (T) expression.getValue();
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        Object callee = globalEnvironment.get((String) expression.getCallee().accept(this));

        if (!(callee instanceof Callable callable)) {
            throw new RuntimeException("Object is not callable");
        }

        List<Object> arguments = new ArrayList<>();

        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (arguments.size() != callable.getArity()) {
            throw new ArgMismatchError((String) expression.getCallee().accept(this), callable.getArity(), arguments.size());
        }

        Object result = callable.call(this, arguments);

        return (T) result;
    }

    @Override
    public Void visitFuncDecl(FuncDecl funcDecl) {
        globalEnvironment.define(funcDecl.getName(),
                new Function(localEnvironment,
                        funcDecl.getBody(),
                        funcDecl.getParameters(),
                        funcDecl.getArity()));

        return null;
    }

    @Override
    public Void visitReturn(Return ret) {

        Object value = null;

        if (ret.getValue() != null) {
            value = ret.getValue().accept(this);
        }

        throw new ReturnSignal(value);
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        List<Expression> expressions = expression.getExpressions();
        StringBuilder builder = new StringBuilder();

        Object first = expressions.getFirst().accept(this);

        if (expressions.getFirst() instanceof ComplexExpression) {
            builder.append("(").append(first).append(")");
        } else {
            builder.append(first);
        }

        int i = 1;

        while (i < expressions.size()) {

            Expression operatorExpr = expressions.get(i);

            Object right;
            String operator = null;

            if (operatorExpr instanceof UnaryExpression unary) {

                operator = unary.getOperation().getLexeme();

                if (unary.getRight() != null) {
                    right = unary.accept(this);
                    i += 1;
                } else {
                    builder.append(operator);
                    i += 1;
                    continue;
                }

            } else {
                right = operatorExpr.accept(this);
                i++;

                if (operator == null) {
                    builder.append(right);
                    continue;
                }
            }

            switch (operator) {
                case "+", "-", "*", "/" ->
                    builder.append(operator).append(right);
                case "$" ->
                    builder.append(right);
                default ->
                    throw new UnknownOperatorError("Unknown operator: " + operator);
            }
        }

        return (T) builder.toString();
    }

    public Environment getLocalEnvironment() {
        return localEnvironment;
    }

    public void setLocalEnvironment(Environment localEnvironment) {
        this.localEnvironment = localEnvironment;
    }

    public static Environment getGlobalEnvironment() {
        return globalEnvironment;
    }

    public static void setGlobalEnvironment() {

    }

    @Override
    public <T> T visitUnaryExpr(Expression.UnaryExpression expression) {
        String operator = expression.getOperation().getLexeme();

        Object right = null;

        if (expression.getRight() != null) {
            right = expression.getRight().accept(this);
        }

        switch (operator) {
            case "$" -> {

                String name = (String) right;

                if (localEnvironment != null) {
                    return (T) localEnvironment.get(name);
                }

                return (T) globalEnvironment.get(name);
            }
            case "-" -> {
                if (right == null) {
                    return (T) "-";
                } else {
                    return (T) ("-" + String.valueOf(right));
                }
            }
            default ->
                throw new UnknownOperatorError("Unknown unary operator: " + operator);
        }
    }

    @Override
    public Void visitAssign(Assign assign) {
        String name = assign.getName();
        Object expression = assign.getExpression().accept(this);

        if (localEnvironment == null) {
            globalEnvironment.assign(name, expression);
        } else {
            localEnvironment.assign(name, expression);
        }

        return null;
    }

    @Override
    public Object visitIf(If stmt) {
        String condition = (String) stmt.getCondition().accept(this);
        boolean value = (boolean) Evaluator.evaluate(condition);

        List<Node> body = value ? stmt.getThenBody() : stmt.getElseBody();

        for (Node node : body) {
            switch (node) {
                case Expression expression ->
                    expression.accept(this);
                case Statement statement ->
                    statement.accept(this);
                default ->
                    throw new AssertionError();
            }
        }

        return null;
    }
}
