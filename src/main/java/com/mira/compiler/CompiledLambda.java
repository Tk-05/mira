package com.mira.compiler;

import java.lang.reflect.Method;
import java.util.List;

import com.mira.runtime.functions.Callable;
import com.mira.runtime.interpreter.Interpreter;

public class CompiledLambda implements Callable {

    private final Method method;
    private final int arity;

    public CompiledLambda(Method method, int arity) {
        this.method = method;
        this.arity = arity;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        try {
            return method.invoke(null, (Object) arguments.toArray());
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException("Error calling compiled function: " + method.getName(), e);
        }
    }

    @Override
    public int getArity() {
        return arity;
    }
}
