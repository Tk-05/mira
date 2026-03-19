package com.mira.runtime.functions.nativeFunction;

import java.util.List;

import com.mira.runtime.Callable;
import com.mira.runtime.Interpreter;

public class NativeFunction implements Callable {

    private final int arity;
    private final FunctionBody body;

    public interface FunctionBody {
        Object execute(List<Object> args);
    }

    public NativeFunction(int arity, FunctionBody body) {
        this.arity = arity;
        this.body = body;
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        return body.execute(arguments);
    }

    @Override
    public int getArity() {
        return arity;
    }
}
