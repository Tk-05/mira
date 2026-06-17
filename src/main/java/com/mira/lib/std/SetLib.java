package com.mira.lib.std;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class SetLib implements Lib {

    private static String exprKey(Expression e) {
        return e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
    }

    private static Expression wrap(String s) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, s, 0, 0));
    }

    private static List<Expression> toMembers(Object arg) {
        return switch (arg) {
            case ArrayExpression a ->
                new ArrayList<>(a.getMembers());
            case ListExpression l ->
                new ArrayList<>(l.getMembers());
            default ->
                throw new RuntimeException("Expected list (set), got: " + arg.getClass().getSimpleName());
        };
    }

    private static ListExpression fromSet(LinkedHashSet<String> set) {
        List<Expression> members = new ArrayList<>();
        for (String s : set) {
            members.add(wrap(s));
        }
        return new ListExpression(members);
    }

    private static LinkedHashSet<String> toSet(Object arg) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Expression e : toMembers(arg)) {
            set.add(exprKey(e));
        }
        return set;
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("newSet", new NativeFunction(0, args
                -> new ListExpression(new ArrayList<>())));

        environment.define("add", new NativeFunction(2, args -> {
            LinkedHashSet<String> set = toSet(args.get(0));
            set.add(String.valueOf(args.get(1)));
            return fromSet(set);
        }));

        environment.define("remove", new NativeFunction(2, args -> {
            LinkedHashSet<String> set = toSet(args.get(0));
            set.remove(String.valueOf(args.get(1)));
            return fromSet(set);
        }));

        environment.define("has", new NativeFunction(2, args
                -> toSet(args.get(0)).contains(String.valueOf(args.get(1)))));

        environment.define("size", new NativeFunction(1, args
                -> (double) toSet(args.get(0)).size()));

        environment.define("union", new NativeFunction(2, args -> {
            LinkedHashSet<String> set = toSet(args.get(0));
            set.addAll(toSet(args.get(1)));
            return fromSet(set);
        }));

        environment.define("intersection", new NativeFunction(2, args -> {
            LinkedHashSet<String> set = toSet(args.get(0));
            Set<String> other = toSet(args.get(1));
            set.retainAll(other);
            return fromSet(set);
        }));

        environment.define("difference", new NativeFunction(2, args -> {
            LinkedHashSet<String> set = toSet(args.get(0));
            set.removeAll(toSet(args.get(1)));
            return fromSet(set);
        }));

        environment.define("toList", new NativeFunction(1, args
                -> fromSet(toSet(args.get(0)))));

        environment.define("fromList", new NativeFunction(1, args
                -> fromSet(toSet(args.get(0)))));
    }
}
