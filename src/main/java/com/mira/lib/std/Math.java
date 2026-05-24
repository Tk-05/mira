package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Evaluator;

public class Math implements Lib {

    private static double toDouble(Object arg) {
        if (arg instanceof Number n) {
            return n.doubleValue();
        }
        return ((Number) Evaluator.evaluate(String.valueOf(arg), false)).doubleValue();
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("pi", java.lang.Math.PI);
        environment.define("e", java.lang.Math.E);
        environment.define("inf", Double.POSITIVE_INFINITY);
        environment.define("nan", Double.NaN);

        environment.define("pow",
                new NativeFunction(2, args -> java.lang.Math.pow(toDouble(args.get(0)), toDouble(args.get(1)))));

        environment.define("max",
                new NativeFunction(2, args -> java.lang.Math.max(toDouble(args.get(0)), toDouble(args.get(1)))));

        environment.define("min",
                new NativeFunction(2, args -> java.lang.Math.min(toDouble(args.get(0)), toDouble(args.get(1)))));

        environment.define("abs",
                new NativeFunction(1, args -> java.lang.Math.abs(toDouble(args.get(0)))));

        environment.define("rand",
                new NativeFunction(0, args -> java.lang.Math.random()));

        environment.define("randInt",
                new NativeFunction(2, args -> {
                    int min = (int) toDouble(args.get(0));
                    int max = (int) toDouble(args.get(1));
                    return (double) (min + (int) (java.lang.Math.random() * (max - min + 1)));
                }));

        environment.define("round",
                new NativeFunction(1, args -> (double) java.lang.Math.round(toDouble(args.get(0)))));

        environment.define("floor",
                new NativeFunction(1, args -> java.lang.Math.floor(toDouble(args.get(0)))));

        environment.define("ceil",
                new NativeFunction(1, args -> java.lang.Math.ceil(toDouble(args.get(0)))));

        environment.define("sqrt",
                new NativeFunction(1, args -> java.lang.Math.sqrt(toDouble(args.get(0)))));

        environment.define("cbrt",
                new NativeFunction(1, args -> java.lang.Math.cbrt(toDouble(args.get(0)))));

        environment.define("log",
                new NativeFunction(1, args -> java.lang.Math.log(toDouble(args.get(0)))));

        environment.define("log10",
                new NativeFunction(1, args -> java.lang.Math.log10(toDouble(args.get(0)))));

        environment.define("log2",
                new NativeFunction(1, args -> java.lang.Math.log(toDouble(args.get(0))) / java.lang.Math.log(2)));

        environment.define("sin",
                new NativeFunction(1, args -> java.lang.Math.sin(toDouble(args.get(0)))));

        environment.define("cos",
                new NativeFunction(1, args -> java.lang.Math.cos(toDouble(args.get(0)))));

        environment.define("tan",
                new NativeFunction(1, args -> java.lang.Math.tan(toDouble(args.get(0)))));

        environment.define("asin",
                new NativeFunction(1, args -> java.lang.Math.asin(toDouble(args.get(0)))));

        environment.define("acos",
                new NativeFunction(1, args -> java.lang.Math.acos(toDouble(args.get(0)))));

        environment.define("atan",
                new NativeFunction(1, args -> java.lang.Math.atan(toDouble(args.get(0)))));

        environment.define("atan2",
                new NativeFunction(2, args -> java.lang.Math.atan2(toDouble(args.get(0)), toDouble(args.get(1)))));

        environment.define("toRad",
                new NativeFunction(1, args -> java.lang.Math.toRadians(toDouble(args.get(0)))));

        environment.define("toDeg",
                new NativeFunction(1, args -> java.lang.Math.toDegrees(toDouble(args.get(0)))));

        environment.define("sign",
                new NativeFunction(1, args -> (double) java.lang.Math.signum(toDouble(args.get(0)))));

        environment.define("clamp",
                new NativeFunction(3, args -> {
                    double val = toDouble(args.get(0));
                    double min = toDouble(args.get(1));
                    double max = toDouble(args.get(2));
                    return java.lang.Math.max(min, java.lang.Math.min(max, val));
                }));

        environment.define("isNaN",
                new NativeFunction(1, args -> Double.isNaN(toDouble(args.get(0)))));

        environment.define("isInf",
                new NativeFunction(1, args -> Double.isInfinite(toDouble(args.get(0)))));

        environment.define("gcd",
                new NativeFunction(2, args -> {
                    long a = (long) toDouble(args.get(0));
                    long b = (long) toDouble(args.get(1));
                    a = java.lang.Math.abs(a);
                    b = java.lang.Math.abs(b);
                    while (b != 0) {
                        long t = b;
                        b = a % b;
                        a = t;
                    }
                    return (double) a;
                }));

        environment.define("lcm",
                new NativeFunction(2, args -> {
                    long a = (long) toDouble(args.get(0));
                    long b = (long) toDouble(args.get(1));
                    long aa = java.lang.Math.abs(a), bb = java.lang.Math.abs(b);
                    if (aa == 0 || bb == 0) {
                        return 0.0;
                    }
                    long g = aa, r = bb;
                    while (r != 0) {
                        long t = r;
                        r = g % r;
                        g = t;
                    }
                    return (double) (aa / g * bb);
                }));

        environment.define("factorial",
                new NativeFunction(1, args -> {
                    int n = (int) toDouble(args.get(0));
                    if (n < 0) {
                        throw new RuntimeException("factorial of negative number");
                    }
                    if (n > 20) {
                        throw new RuntimeException("factorial: n must be <= 20");
                    }
                    double result = 1;
                    for (int i = 2; i <= n; i++) {
                        result *= i;
                    }
                    return result;
                }));

        environment.define("trunc",
                new NativeFunction(1, args -> (double) (long) toDouble(args.get(0))));

        environment.define("hypot",
                new NativeFunction(2, args -> java.lang.Math.hypot(toDouble(args.get(0)), toDouble(args.get(1)))));
    }
}
