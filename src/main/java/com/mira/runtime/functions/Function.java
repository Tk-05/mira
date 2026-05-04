package com.mira.runtime.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.NullValue;

public class Function implements Callable {

    private static final class WrappedValue extends Expression {

        private final Object value;

        WrappedValue(Object value) {
            this.value = value;
        }

        @Override
        public <T> T accept(com.mira.runtime.visitors.ExprVisitor<T> visitor) {
            return (T) value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private final Environment environment;
    private final List<Parameter> parameters;
    private final List<Node> body;
    private final int arity;
    private final int maxArity;
    private final String variadicParam;
    private final boolean isAsync;

    public Function(Environment environment, List<Node> body, List<Parameter> parameters, int arity, int maxArity, String variadicParam) {
        this(environment, body, parameters, arity, maxArity, variadicParam, false);
    }

    public Function(Environment environment, List<Node> body, List<Parameter> parameters, int arity, int maxArity, String variadicParam, boolean isAsync) {
        this.environment = environment;
        this.body = body;
        this.parameters = parameters;
        this.arity = arity;
        this.maxArity = maxArity;
        this.variadicParam = variadicParam;
        this.isAsync = isAsync;
    }

    private static Expression wrap(Object val) {
        if (val instanceof Expression e) {
            return e;
        }
        return new WrappedValue(val);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment localEnv = new Environment(environment);

        for (int i = 0; i < parameters.size(); i++) {
            Object value;
            if (i < arguments.size()) {
                value = arguments.get(i);
            } else if (parameters.get(i).hasDefault()) {
                value = parameters.get(i).defaultValue().accept(interpreter);
            } else {
                value = NullValue.INSTANCE;
            }
            localEnv.define(parameters.get(i).name(), value);
        }

        if (variadicParam != null) {
            List<Expression> rest = new ArrayList<>();
            for (int i = parameters.size(); i < arguments.size(); i++) {
                rest.add(wrap(arguments.get(i)));
            }
            localEnv.define(variadicParam, new ListExpression(rest));
        }

        if (isAsync) {
            Interpreter forked = interpreter.fork();
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                forked.setLocalEnvironment(localEnv);
                try {
                    for (Node node : body) {
                        switch (node) {
                            case Statement stmt ->
                                stmt.accept(forked);
                            case Expression expr ->
                                expr.accept(forked);
                            default ->
                                throw new AssertionError();
                        }
                    }
                } catch (ReturnSignal returnSignal) {
                    return returnSignal.getValue();
                } finally {
                    forked.setLocalEnvironment(null);
                }
                return null;
            });
            return new Promise(future);
        }

        Environment previous = interpreter.getLocalEnvironment();
        interpreter.setLocalEnvironment(localEnv);

        try {
            for (Node node : body) {
                switch (node) {
                    case Statement stmt ->
                        stmt.accept(interpreter);
                    case Expression expr ->
                        expr.accept(interpreter);
                    default -> {
                        throw new AssertionError();
                    }
                }
            }
        } catch (ReturnSignal returnSignal) {
            return returnSignal.getValue();
        } finally {
            interpreter.setLocalEnvironment(previous);
        }

        return null;
    }

    @Override
    public int getArity() {
        return arity;
    }

    public int getMaxArity() {
        return maxArity;
    }
}
