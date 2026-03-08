package com.mira.runtime.functions;

import java.util.List;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.runtime.Callable;
import com.mira.runtime.Environment;
import com.mira.runtime.Interpreter;

public class Function implements Callable {

    private final Environment environment;
    private final List<DumbExpression> parameters;
    private final List<Node> body;
    private final int arity;

    public Function(Environment environment, List<Node> body, List<DumbExpression> parameters, int arity) {
        this.environment = environment;
        this.body = body;
        this.parameters = parameters;
        this.arity = arity;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {

        Environment localEnv = new Environment(environment);

        for (int i = 0; i < parameters.size(); i++) {
            localEnv.define(parameters.get(i).getValue(), arguments.get(i));
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
