package com.mira.lib.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.NullValue;

public class Collection implements Lib {

    private static Object resolve(Expression e) {
        if (e instanceof DumbExpression d) {
            String s = String.valueOf(d.getValue());
            if (s.equals("true")) {
                return true;
            }
            if (s.equals("false")) {
                return false;
            }
            if (s.equals("null")) {
                return NullValue.INSTANCE;
            }
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
            return s;
        }
        return e;
    }

    private static boolean isTruthy(Object val) {
        if (val == null || val instanceof NullValue) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Double d) {
            return d != 0.0;
        }
        if (val instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    private static String toKey(Object val) {
        if (val instanceof Double d) {
            long l = d.longValue();
            return (double) l == d ? String.valueOf(l) : d.toString();
        }
        return String.valueOf(val);
    }

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
            case ArrayExpression a ->
                new ArrayList<>(a.getMembers());
            case ListExpression l ->
                new ArrayList<>(l.getMembers());
            default ->
                throw new RuntimeException("Expected array or list, got: " + arg.getClass().getSimpleName());
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

        environment.define("indexOf", new NativeFunction(2, args -> {
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
                switch (e) {
                    case ArrayExpression inner ->
                        result.addAll(inner.getMembers());
                    case ListExpression inner ->
                        result.addAll(inner.getMembers());
                    default ->
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

        environment.define("newList", new NativeFunction(0, args -> {
            return new ListExpression(new ArrayList<>());
        }));

        environment.define("remove", new NativeFunction(2, args -> {
            if (!(args.get(0) instanceof ListExpression list)) {
                throw new RuntimeException("remove requires a list");
            }
            int index = (int) Double.parseDouble(String.valueOf(args.get(1)));
            list.getMembers().remove(index);
            return list;
        }));

        // ── Higher-order functions ────────────────────────────────────────────
        environment.define("map", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                List<Expression> result = new ArrayList<>();
                for (Expression e : members) {
                    result.add(wrap(fn.call(interpreter, List.of(resolve(e)))));
                }
                return new ListExpression(result);
            }
        });

        environment.define("filter", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                List<Expression> result = new ArrayList<>();
                for (Expression e : members) {
                    if (isTruthy(fn.call(interpreter, List.of(resolve(e))))) {
                        result.add(e);
                    }
                }
                return new ListExpression(result);
            }
        });

        environment.define("reduce", new Callable() {
            @Override
            public int getArity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                Object acc = arguments.get(2);
                for (Expression e : members) {
                    acc = fn.call(interpreter, List.of(acc, resolve(e)));
                }
                return acc;
            }
        });

        environment.define("any", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                for (Expression e : members) {
                    if (isTruthy(fn.call(interpreter, List.of(resolve(e))))) {
                        return true;
                    }
                }
                return false;
            }
        });

        environment.define("all", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                for (Expression e : members) {
                    if (!isTruthy(fn.call(interpreter, List.of(resolve(e))))) {
                        return false;
                    }
                }
                return true;
            }
        });

        environment.define("count", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                double n = 0;
                for (Expression e : members) {
                    if (isTruthy(fn.call(interpreter, List.of(resolve(e))))) {
                        n++;
                    }
                }
                return n;
            }
        });

        environment.define("sortBy", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = new ArrayList<>(toMembers(arguments.get(0)));
                Callable fn = (Callable) arguments.get(1);
                members.sort((a, b) -> {
                    String ka = toKey(fn.call(interpreter, List.of(resolve(a))));
                    String kb = toKey(fn.call(interpreter, List.of(resolve(b))));
                    try {
                        return Double.compare(Double.parseDouble(ka), Double.parseDouble(kb));
                    } catch (NumberFormatException e) {
                        return ka.compareTo(kb);
                    }
                });
                return new ListExpression(members);
            }
        });

        environment.define("findFirst", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                for (Expression e : members) {
                    if (isTruthy(fn.call(interpreter, List.of(resolve(e))))) {
                        return resolve(e);
                    }
                }
                return NullValue.INSTANCE;
            }
        });

        environment.define("groupBy", new Callable() {
            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                List<Expression> members = toMembers(arguments.get(0));
                Callable fn = (Callable) arguments.get(1);
                LinkedHashMap<String, Expression> groups = new LinkedHashMap<>();
                java.util.Map<String, List<Expression>> raw = new java.util.LinkedHashMap<>();
                for (Expression e : members) {
                    String key = toKey(fn.call(interpreter, List.of(resolve(e))));
                    raw.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                }
                for (var entry : raw.entrySet()) {
                    groups.put(entry.getKey(), new ListExpression(entry.getValue()));
                }
                return new MapExpression(groups);
            }
        });

        // ── Utility functions ─────────────────────────────────────────────────
        environment.define("sort", new NativeFunction(1, args -> {
            List<Expression> members = new ArrayList<>(toMembers(args.get(0)));
            members.sort((a, b) -> {
                String sa = a instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(a);
                String sb = b instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(b);
                try {
                    return Double.compare(Double.parseDouble(sa), Double.parseDouble(sb));
                } catch (NumberFormatException e) {
                    return sa.compareTo(sb);
                }
            });
            return new ListExpression(members);
        }));

        environment.define("unique", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            List<Expression> result = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (Expression e : members) {
                String key = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                if (seen.add(key)) {
                    result.add(e);
                }
            }
            return new ListExpression(result);
        }));

        environment.define("sum", new NativeFunction(1, args -> {
            double total = 0;
            for (Expression e : toMembers(args.get(0))) {
                String v = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                total += Double.parseDouble(v);
            }
            return total;
        }));

        environment.define("avg", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("avg on empty collection");
            }
            double total = 0;
            for (Expression e : members) {
                String v = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                total += Double.parseDouble(v);
            }
            return total / members.size();
        }));

        environment.define("zip", new NativeFunction(2, args -> {
            List<Expression> a = toMembers(args.get(0));
            List<Expression> b = toMembers(args.get(1));
            int len = java.lang.Math.min(a.size(), b.size());
            List<Expression> result = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                List<Expression> pair = new ArrayList<>();
                pair.add(a.get(i));
                pair.add(b.get(i));
                result.add(new ListExpression(pair));
            }
            return new ListExpression(result);
        }));

        environment.define("fill", new NativeFunction(2, args -> {
            int n = (int) Double.parseDouble(String.valueOf(args.get(0)));
            Object val = args.get(1);
            List<Expression> result = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                result.add(wrap(val));
            }
            return new ListExpression(result);
        }));

        environment.define("min", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("min on empty collection");
            }
            double min = Double.MAX_VALUE;
            for (Expression e : members) {
                String v = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                min = java.lang.Math.min(min, Double.parseDouble(v));
            }
            return min;
        }));

        environment.define("max", new NativeFunction(1, args -> {
            List<Expression> members = toMembers(args.get(0));
            if (members.isEmpty()) {
                throw new RuntimeException("max on empty collection");
            }
            double max = -Double.MAX_VALUE;
            for (Expression e : members) {
                String v = e instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(e);
                max = java.lang.Math.max(max, Double.parseDouble(v));
            }
            return max;
        }));

        environment.define("take", new NativeFunction(2, args -> {
            List<Expression> members = toMembers(args.get(0));
            int n = (int) Double.parseDouble(String.valueOf(args.get(1)));
            return new ListExpression(new ArrayList<>(members.subList(0, java.lang.Math.min(n, members.size()))));
        }));

        environment.define("drop", new NativeFunction(2, args -> {
            List<Expression> members = toMembers(args.get(0));
            int n = (int) Double.parseDouble(String.valueOf(args.get(1)));
            return new ListExpression(new ArrayList<>(members.subList(java.lang.Math.min(n, members.size()), members.size())));
        }));

        environment.define("chunk", new NativeFunction(2, args -> {
            List<Expression> members = toMembers(args.get(0));
            int size = (int) Double.parseDouble(String.valueOf(args.get(1)));
            if (size <= 0) {
                throw new RuntimeException("chunk size must be > 0");
            }
            List<Expression> result = new ArrayList<>();
            for (int i = 0; i < members.size(); i += size) {
                result.add(new ListExpression(new ArrayList<>(members.subList(i, java.lang.Math.min(i + size, members.size())))));
            }
            return new ListExpression(result);
        }));
    }
}
