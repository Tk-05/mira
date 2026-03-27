package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Evaluator;

public class Math implements Lib {

    @Override
    public void loadLib(Environment environment) {
        //constants
        environment.define("pi", java.lang.Math.PI);
        environment.define("e", java.lang.Math.E);

        //functions
        environment.define("pow",
                new NativeFunction(2, args -> {
                    return java.lang.Math.pow(
                            (double) Evaluator.evaluate(String.valueOf(args.get(0)), false),
                            (double) Evaluator.evaluate(String.valueOf(args.get(1)), false));
                }));

        environment.define("max",
                new NativeFunction(2, args -> {
                    return java.lang.Math.max(
                            (double) Evaluator.evaluate(String.valueOf(args.get(0)), false),
                            (double) Evaluator.evaluate(String.valueOf(args.get(1)), false));
                }));

        environment.define("min",
                new NativeFunction(2, args -> {
                    return java.lang.Math.min(
                            (double) Evaluator.evaluate(String.valueOf(args.get(0)), false),
                            (double) Evaluator.evaluate(String.valueOf(args.get(1)), false));
                }));

        environment.define("abs", new NativeFunction(1, args -> java.lang.Math.abs((double) Evaluator.evaluate(String.valueOf(args.get(0)), false))));
        environment.define("rand", new NativeFunction(0, args -> java.lang.Math.random()));
        environment.define("round", new NativeFunction(1, args -> java.lang.Math.round((double) Evaluator.evaluate(String.valueOf(args.get(0)), false))));
    }
}
