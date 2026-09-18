package com.mira.runtime.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.NullValue;
import com.mira.runtime.visitors.ExprVisitor;

public class Function implements Callable {

    private static final class WrappedValue extends Expression {

        private final Object value;

        WrappedValue(Object value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T accept(ExprVisitor<T> visitor) {
            return (T) value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private final Environment environment;
    private final Environment globalContext;
    private final List<Parameter> parameters;
    private final List<Node> body;
    private final int arity;
    private final int maxArity;
    private final String variadicParam;
    private final boolean isAsync;
    // Resolver-assigned ordered local names for this function's own scope (see
    // Resolver.java), or null if never analyzed - every call gets the same static
    // shape, so this can be shared by reference across every call frame.
    private final String[] slotNames;

    public Function(Environment environment, List<Node> body, List<Parameter> parameters, int arity, int maxArity,
            String variadicParam, Environment globalContext) {
        this(environment, body, parameters, arity, maxArity, variadicParam, false, globalContext, null);
    }

    public Function(Environment environment, List<Node> body, List<Parameter> parameters, int arity, int maxArity,
            String variadicParam, boolean isAsync, Environment globalContext) {
        this(environment, body, parameters, arity, maxArity, variadicParam, isAsync, globalContext, null);
    }

    public Function(Environment environment, List<Node> body, List<Parameter> parameters, int arity, int maxArity,
            String variadicParam, boolean isAsync, Environment globalContext, String[] slotNames) {
        this.environment = environment;
        this.globalContext = globalContext;
        this.body = body;
        this.parameters = parameters;
        this.arity = arity;
        this.maxArity = maxArity;
        this.variadicParam = variadicParam;
        this.isAsync = isAsync;
        this.slotNames = slotNames;
    }

    private static Expression wrap(Object val) {
        if (val instanceof Expression e) {
            return e;
        }
        return new WrappedValue(val);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment localEnv = slotNames != null
                ? new Environment(environment, slotNames)
                : new Environment(environment);

        for (int i = 0; i < parameters.size(); i++) {
            Object value;
            if (i < arguments.size()) {
                value = arguments.get(i);
            } else if (parameters.get(i).hasDefault()) {
                value = parameters.get(i).defaultValue().accept(interpreter);
            } else {
                value = NullValue.INSTANCE;
            }
            if (slotNames != null) {
                localEnv.defineAt(i, value, false);
            } else {
                localEnv.define(parameters.get(i).name(), value);
            }
        }

        if (variadicParam != null) {
            List<Expression> rest = new ArrayList<>();
            for (int i = parameters.size(); i < arguments.size(); i++) {
                rest.add(wrap(arguments.get(i)));
            }
            if (slotNames != null) {
                localEnv.defineAt(parameters.size(), new ListExpression(rest), false);
            } else {
                localEnv.define(variadicParam, new ListExpression(rest));
            }
        }

        if (isAsync) {
            Interpreter forked = interpreter.fork();
            forked.setGlobalEnvironment(globalContext);
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                forked.setLocalEnvironment(localEnv);
                try {
                    forked.runBody(body);
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
        Environment previousGlobal = interpreter.getGlobalEnvironment();
        interpreter.setLocalEnvironment(localEnv);
        interpreter.setGlobalEnvironment(globalContext);

        try {
            interpreter.runBody(body);
        } catch (ReturnSignal returnSignal) {
            return returnSignal.getValue();
        } finally {
            interpreter.setLocalEnvironment(previous);
            interpreter.setGlobalEnvironment(previousGlobal);
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

    public List<Parameter> getParameters() {
        return parameters;
    }
}
