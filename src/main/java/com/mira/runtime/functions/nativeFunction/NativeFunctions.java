package com.mira.runtime.functions.nativeFunction;

import com.mira.runtime.Environment;
import com.mira.runtime.Evaluator;

public class NativeFunctions {

    public static Environment defineNativeFunctions(Environment environment) {
        environment.define("print",
                new NativeFunction(1, args -> {
                    System.out.println(args.get(0));
                    return null;
                }));

        environment.define("eval",
                new NativeFunction(1, args -> {
                    return Evaluator.evaluate(String.valueOf(args.get(0)));
                }));
        return environment;
    }
}
