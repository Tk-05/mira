package com.mira.lib.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.NullValue;

public class RandomLib implements Lib {

    private final Random rng = new Random();

    private static List<Expression> toMembers(Object arg) {
        return switch (arg) {
            case ArrayExpression a ->
                new ArrayList<>(a.getMembers());
            case ListExpression l ->
                new ArrayList<>(l.getMembers());
            default ->
                throw new RuntimeException("Expected list, got: " + arg.getClass().getSimpleName());
        };
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("seed", new NativeFunction(1, args -> {
            rng.setSeed((long) Double.parseDouble(String.valueOf(args.get(0))));
            return NullValue.INSTANCE;
        }));

        environment.define("next", new NativeFunction(0, args
                -> rng.nextDouble()));

        environment.define("nextInt", new NativeFunction(2, args -> {
            int min = (int) Double.parseDouble(String.valueOf(args.get(0)));
            int max = (int) Double.parseDouble(String.valueOf(args.get(1)));
            return (double) (min + rng.nextInt(max - min));
        }));

        environment.define("nextFloat", new NativeFunction(2, args -> {
            double min = Double.parseDouble(String.valueOf(args.get(0)));
            double max = Double.parseDouble(String.valueOf(args.get(1)));
            return min + rng.nextDouble() * (max - min);
        }));

        environment.define("nextBool", new NativeFunction(0, args
                -> rng.nextBoolean()));

        environment.define("nextGaussian", new NativeFunction(0, args
                -> rng.nextGaussian()));

        environment.define("shuffle", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            Collections.shuffle(members, rng);
            return new ListExpression(members);
        }));

        environment.define("pick", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("pick on empty list");
            }
            return members.get(rng.nextInt(members.size()));
        }));

        environment.define("sample", new NativeFunction(2, args -> {
            List<Expression> members = new ArrayList<>(toMembers(args.get(0)));
            int n = (int) Double.parseDouble(String.valueOf(args.get(1)));
            if (n > members.size()) {
                throw new RuntimeException("sample: n exceeds list size");
            }
            Collections.shuffle(members, rng);
            return new ListExpression(members.subList(0, n));
        }));
    }
}
