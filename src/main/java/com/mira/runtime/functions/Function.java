package com.mira.runtime.functions;

import java.util.ArrayList;
import java.util.List;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
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
    private final List<DumbExpression> parameters;
    private final List<Node> body;
    private final int arity;
    private final String variadicParam;

    public Function(Environment environment, List<Node> body, List<DumbExpression> parameters, int arity, String variadicParam) {
        this.environment = environment;
        this.body = body;
        this.parameters = parameters;
        this.arity = arity;
        this.variadicParam = variadicParam;
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
            localEnv.define(parameters.get(i).getValue(), arguments.get(i));
        }

        if (variadicParam != null) {
            List<Expression> rest = new ArrayList<>();
            for (int i = parameters.size(); i < arguments.size(); i++) {
                rest.add(wrap(arguments.get(i)));
            }
            localEnv.define(variadicParam, new ListExpression(rest));
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
            interpreter.setLocalEnvironment(previous);
            return returnSignal.getValue();
        }

        return null;
    }

    @Override
    public int getArity() {
        return arity;
    }
}
