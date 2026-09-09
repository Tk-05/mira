package com.mira.runtime.functions;

import java.util.List;

import com.mira.runtime.interpreter.Interpreter;

public class NativeFunction implements Callable {

    private final int arity;
    private final String paramHint;
    private final FunctionBody body;

    public interface FunctionBody {

        Object execute(List<Object> args);
    }

    public NativeFunction(int arity, String paramHint, FunctionBody body) {
        this.arity = arity;
        this.paramHint = paramHint;
        this.body = body;
    }

    public NativeFunction(int arity, FunctionBody body) {
        this(arity, "", body);
    }

    public String getParamHint() {
        return paramHint;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        return body.execute(arguments);
    }

    @Override
    public int getArity() {
        return arity;
    }
}
