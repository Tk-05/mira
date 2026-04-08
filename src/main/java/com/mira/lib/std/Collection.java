package com.mira.lib.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Collection implements Lib {

    private static Expression wrap(Object val) {
        if (val instanceof Expression expr) {
            return expr;
        }
        if (val instanceof String || val instanceof Double || val instanceof Boolean) {
            return new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(val), 0, 0));
        }
        final Object captured = val;
        return new Expression() {
            @Override
            public <T> T accept(com.mira.runtime.visitors.ExprVisitor<T> visitor) {
                return (T) captured;
            }

            @Override
            public String toString() {
                return String.valueOf(captured);
            }
        };
    }

    private static List<Expression> toMembers(Object arg) {
        return switch (arg) {
            case ListExpression l ->
                new ArrayList<>(l.getMembers());
            case TupleExpression t ->
                new ArrayList<>(t.getMembers());
            default ->
                throw new RuntimeException("Expected list or tuple, got: " + arg.getClass().getSimpleName());
        };
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("size", new NativeFunction(1, args -> {
            return (double) toMembers(args.get(0)).size();
        }));

        environment.define("push", new NativeFunction(2, args -> {
            if (!(args.get(0) instanceof ListExpression list)) {
                throw new RuntimeException("push requires a list");
            }
            list.getMembers().add(wrap(args.get(1)));
            return list;
        }));

        environment.define("pop", new NativeFunction(1, args -> {
            if (!(args.get(0) instanceof ListExpression list)) {
                throw new RuntimeException("pop requires a list");
            }
            List<Expression> members = list.getMembers();
            if (members.isEmpty()) {
                throw new RuntimeException("pop on empty list");
            }
            members.remove(members.size() - 1);
            return list;
        }));

        environment.define("first", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("first on empty collection");
            }
            return members.get(0);
        }));

        environment.define("last", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("last on empty collection");
            }
            return members.get(members.size() - 1);
        }));

        environment.define("contains", new NativeFunction(2, args -> {
            String target = String.valueOf(args.get(1));
            return toMembers(args.get(0)).stream()
                    .map(e -> e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e))
                    .anyMatch(target::equals);
        }));

        environment.define("findIndex", new NativeFunction(2, args -> {
            String target = String.valueOf(args.get(1));
            List<Expression> members = toMembers(args.get(0));
            for (int i = 0; i < members.size(); i++) {
                Expression e = members.get(i);
                String val = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                if (val.equals(target)) {
                    return (double) i;
                }
            }
            return -1.0;
        }));

        environment.define("slice", new NativeFunction(3, args -> {
            List<Expression> members = toMembers(args.get(0));
            int from = (int) Double.parseDouble(String.valueOf(args.get(1)));
            int to = (int) Double.parseDouble(String.valueOf(args.get(2)));
            return new ListExpression(new ArrayList<>(members.subList(from, to)));
        }));

        environment.define("reverse", new NativeFunction(1, args -> {
            List<Expression> members = new ArrayList<>(toMembers(args.get(0)));
            Collections.reverse(members);
            return new ListExpression(members);
        }));

        environment.define("concat", new NativeFunction(2, args -> {
            List<Expression> a = new ArrayList<>(toMembers(args.get(0)));
            List<Expression> b = new ArrayList<>(toMembers(args.get(1)));
            a.addAll(b);
            return new ListExpression(a);
        }));

        environment.define("flatten", new NativeFunction(1, args -> {
            List<Expression> result = new ArrayList<>();
            for (Expression e : toMembers(args.get(0))) {
                if (e instanceof ListExpression inner) {
                    result.addAll(inner.getMembers());
                } else if (e instanceof TupleExpression inner) {
                    result.addAll(inner.getMembers());
                } else {
                    result.add(e);
                }
            }
            return new ListExpression(result);
        }));

        environment.define("join", new NativeFunction(2, args -> {
            String separator = String.valueOf(args.get(1));
            List<Expression> members = toMembers(args.get(0));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                Expression e = members.get(i);
                sb.append(e instanceof DumbExpression d ? d.getValue() : e);
                if (i < members.size() - 1) {
                    sb.append(separator);
                }
            }
            return sb.toString();
        }));

        environment.define("emptyList", new NativeFunction(0, args -> {
            return new ListExpression(new ArrayList<>());
        }));

        environment.define("emptyTuple", new NativeFunction(0, args -> {
            return new TupleExpression(new ArrayList<>());
        }));
    }
}
