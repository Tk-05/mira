package com.mira.runtime.functions;

import java.util.List;

import com.mira.runtime.interpreter.Interpreter;

public interface Callable {

    Object call(Interpreter interpreter, List<Object> arguments);

    int getArity();
}
