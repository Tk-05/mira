package com.mira.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.Promise;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.Namespace;
import com.mira.runtime.values.NullValue;

public final class Runtime {

    public static final ThreadLocal<Environment> METHOD_ENV = new ThreadLocal<>();

    public static final Object CACHE_MISS = new Object();

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> moduleMainAdditions
            = new java.util.concurrent.ConcurrentHashMap<>();

    public static Object cacheGet(java.util.concurrent.ConcurrentHashMap<java.util.List<Object>, Object> cache, Object[] args) {
        return cache.getOrDefault(java.util.Arrays.asList(args), CACHE_MISS);
    }

    public static void cachePut(java.util.concurrent.ConcurrentHashMap<java.util.List<Object>, Object> cache, Object[] args, Object value) {
        cache.put(java.util.Arrays.asList(args), value);
    }

    public static void loadCompiledModule(Environment globals, String alias, String dotClassName) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = Runtime.class.getClassLoader();
            }
            Class<?> cls = Class.forName(dotClassName, true, cl);

            java.lang.reflect.Field globalsField = cls.getDeclaredField("GLOBALS");
            globalsField.setAccessible(true);
            Environment moduleGlobals = (Environment) globalsField.get(null);

            java.util.Set<String> mainAdditions = moduleMainAdditions.get(dotClassName);
            if (mainAdditions == null) {
                java.util.Set<String> beforeMain = new java.util.HashSet<>(moduleGlobals.keySet());
                try {
                    java.lang.reflect.Method mainMethod = cls.getMethod("main", String[].class);
                    mainMethod.invoke(null, (Object) new String[0]);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(cause);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to initialize module: " + dotClassName, e);
                }
                java.util.Set<String> delta = new java.util.HashSet<>();
                for (String key : moduleGlobals.keySet()) {
                    if (!beforeMain.contains(key) && !key.equals("args")) {
                        delta.add(key);
                    }
                }
                moduleMainAdditions.put(dotClassName, delta);
                mainAdditions = delta;
            }

            Namespace ns = new Namespace(alias);
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!m.getName().startsWith("mira$")) {
                    continue;
                }
                String fnName = m.getName().substring(5);
                if (fnName.equals("main")) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (Exception ignored) {
                }
                java.lang.reflect.Method fm = m;
                ns.define(fnName, new Callable() {
                    @Override
                    public Object call(Interpreter interp, List<Object> args) {
                        try {
                            return fm.invoke(null, (Object) args.toArray());
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            if (cause instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new RuntimeException(cause);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public int getArity() {
                        return fm.getParameterCount();
                    }
                });
            }

            for (String name : mainAdditions) {
                ns.forceDefine(name, moduleGlobals.get(name));
            }

            globals.forceDefine(alias, ns);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Compiled module not found: " + dotClassName, e);
        }
    }

    private Runtime() {
    }

    public static Object nullVal() {
        return NullValue.INSTANCE;
    }

    public static Object wrapLong(long v) {
        return v;
    }

    public static Object wrapDouble(double v) {
        return v;
    }

    public static Object wrapBool(boolean v) {
        return v;
    }

    public static boolean isTruthy(Object value) {
        return switch (value) {
            case Boolean b ->
                b;
            case NullValue n ->
                false;
            case Number n ->
                n.doubleValue() != 0;
            case String s -> {
                if (s.equals("true")) {
                    yield true;
                }
                if (s.equals("false")) {
                    yield false;
                }
                try {
                    yield Double.parseDouble(s) != 0;
                } catch (NumberFormatException e) {
                    yield !s.isEmpty();
                }
            }
            case null ->
                false;
            default ->
                true;
        };
    }

    private static double toNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            return Double.parseDouble(s);
        }
        throw new RuntimeException("Cannot convert to number: " + value);
    }

    public static Object add(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.addExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la + (double) lb;
            }
        }
        try {
            return toNumber(a) + toNumber(b);
        } catch (RuntimeException e) {
            return String.valueOf(a) + String.valueOf(b);
        }
    }

    public static Object sub(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.subtractExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la - (double) lb;
            }
        }
        return toNumber(a) - toNumber(b);
    }

    public static Object mul(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.multiplyExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la * (double) lb;
            }
        }
        return toNumber(a) * toNumber(b);
    }

    public static Object div(Object a, Object b) {
        return toNumber(a) / toNumber(b);
    }

    public static Object mod(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            return la % lb;
        }
        return toNumber(a) % toNumber(b);
    }

    public static Object pow(Object a, Object b) {
        return Math.pow(toNumber(a), toNumber(b));
    }

    public static Object floorDiv(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            return la / lb;
        }
        return Math.floor(toNumber(a) / toNumber(b));
    }

    public static Object negate(Object a) {
        if (a instanceof Long l) {
            return -l;
        }
        if (a instanceof Double d) {
            return -d;
        }
        return -toNumber(a);
    }

    public static Object bitwiseAnd(Object a, Object b) {
        return (long) toNumber(a) & (long) toNumber(b);
    }

    public static Object bitwiseOr(Object a, Object b) {
        return (long) toNumber(a) | (long) toNumber(b);
    }

    public static Object bitwiseXor(Object a, Object b) {
        return (long) toNumber(a) ^ (long) toNumber(b);
    }

    public static Object shiftLeft(Object a, Object b) {
        return (long) toNumber(a) << (long) toNumber(b);
    }

    public static Object shiftRight(Object a, Object b) {
        return (long) toNumber(a) >> (long) toNumber(b);
    }

    public static Object bitwiseNot(Object a) {
        return ~(long) toNumber(a);
    }

    public static Object eq(Object a, Object b) {
        return compare(a, "==", b);
    }

    public static Object neq(Object a, Object b) {
        return compare(a, "!=", b);
    }

    public static Object lt(Object a, Object b) {
        return compare(a, "<", b);
    }

    public static Object gt(Object a, Object b) {
        return compare(a, ">", b);
    }

    public static Object lte(Object a, Object b) {
        return compare(a, "<=", b);
    }

    public static Object gte(Object a, Object b) {
        return compare(a, ">=", b);
    }

    private static Boolean compare(Object left, String op, Object right) {
        if (left instanceof Number ln && right instanceof Number rn) {
            double l = ln.doubleValue(), r = rn.doubleValue();
            return switch (op) {
                case "==" ->
                    l == r;
                case "!=" ->
                    l != r;
                case "<" ->
                    l < r;
                case ">" ->
                    l > r;
                case "<=" ->
                    l <= r;
                case ">=" ->
                    l >= r;
                default ->
                    throw new RuntimeException("Unknown op: " + op);
            };
        }
        if (left instanceof Boolean lb && right instanceof Boolean rb) {
            return switch (op) {
                case "==" ->
                    lb.equals(rb);
                case "!=" ->
                    !lb.equals(rb);
                default ->
                    throw new RuntimeException("Cannot compare booleans with: " + op);
            };
        }
        if ((left instanceof NullValue || left == null) || (right instanceof NullValue || right == null)) {
            boolean bothNull = (left instanceof NullValue || left == null) && (right instanceof NullValue || right == null);
            return switch (op) {
                case "==" ->
                    bothNull;
                case "!=" ->
                    !bothNull;
                default ->
                    false;
            };
        }
        String l = String.valueOf(left), r = String.valueOf(right);
        try {
            double ld = Double.parseDouble(l), rd = Double.parseDouble(r);
            return switch (op) {
                case "==" ->
                    ld == rd;
                case "!=" ->
                    ld != rd;
                case "<" ->
                    ld < rd;
                case ">" ->
                    ld > rd;
                case "<=" ->
                    ld <= rd;
                case ">=" ->
                    ld >= rd;
                default ->
                    throw new RuntimeException("Unknown op: " + op);
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==" ->
                    l.equals(r);
                case "!=" ->
                    !l.equals(r);
                case "<" ->
                    l.compareTo(r) < 0;
                case ">" ->
                    l.compareTo(r) > 0;
                case "<=" ->
                    l.compareTo(r) <= 0;
                case ">=" ->
                    l.compareTo(r) >= 0;
                default ->
                    throw new RuntimeException("Unknown op: " + op);
            };
        }
    }

    public static Object nullCoalesce(Object a, Object b) {
        return (a == null || a instanceof NullValue) ? b : a;
    }

    public static boolean isNullValue(Object a) {
        return a == null || a instanceof NullValue;
    }

    public static Object typeofVal(Object val) {
        if (val == null || val instanceof NullValue) {
            return "null";
        }
        if (val instanceof Boolean) {
            return "bool";
        }
        if (val instanceof Number) {
            return "number";
        }
        if (val instanceof String) {
            return "string";
        }
        if (val instanceof Promise) {
            return "promise";
        }
        if (val instanceof Callable) {
            return "fn";
        }
        if (val instanceof ListExpression) {
            return "list";
        }
        if (val instanceof ArrayExpression) {
            return "array";
        }
        if (val instanceof MapExpression) {
            return "map";
        }
        if (val instanceof Environment) {
            return "object";
        }
        return "unknown";
    }

    public static Expression wrapExpr(Object val) {
        if (val instanceof Expression e) {
            return e;
        }
        final Object captured = val;
        return new Expression() {
            @Override
            public <T> T accept(com.mira.runtime.visitors.ExprVisitor<T> v) {
                return (T) captured;
            }

            @Override
            public String toString() {
                return String.valueOf(captured);
            }
        };
    }

    public static Object makeArray(Object[] elements) {
        List<Expression> members = new ArrayList<>(elements.length);
        for (Object e : elements) {
            members.add(wrapExpr(e));
        }
        return new ArrayExpression(members);
    }

    public static Object makeList(Object[] elements) {
        List<Expression> members = new ArrayList<>(elements.length);
        for (Object e : elements) {
            members.add(wrapExpr(e));
        }
        return new ListExpression(members);
    }

    public static Object makeMap(Object[] keysAndValues) {
        LinkedHashMap<String, Expression> entries = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            entries.put(String.valueOf(keysAndValues[i]), wrapExpr(keysAndValues[i + 1]));
        }
        return new MapExpression(entries);
    }

    public static Object makeRange(Object start, Object end, Object step) {
        long s = ((Number) start).longValue();
        long e = ((Number) end).longValue();
        long st = step != null ? ((Number) step).longValue() : 1L;
        if (st == 0) {
            throw new RuntimeException("Range stepsize cannot be zero");
        }
        List<Expression> members = new ArrayList<>();
        for (long i = s; st > 0 ? i < e : i > e; i += st) {
            long finalI = i;
            members.add(new Expression() {
                @Override
                public <T> T accept(com.mira.runtime.visitors.ExprVisitor<T> v) {
                    return (T) (Object) finalI;
                }

                @Override
                public String toString() {
                    return String.valueOf(finalI);
                }
            });
        }
        return new ListExpression(members);
    }

    private static Object evalExpr(Expression expr) {
        if (expr instanceof ArrayExpression || expr instanceof ListExpression
                || expr instanceof MapExpression) {
            return expr;
        }
        if (expr instanceof DumbExpression dumb) {
            String val = dumb.getValue();
            return switch (val) {
                case "true" ->
                    Boolean.TRUE;
                case "false" ->
                    Boolean.FALSE;
                case "null" ->
                    NullValue.INSTANCE;
                default -> {
                    if (!val.isEmpty() && (Character.isDigit(val.charAt(0)) || val.charAt(0) == '-')) {
                        try {
                            yield Long.parseLong(val);
                        } catch (NumberFormatException ignored) {
                        }
                        try {
                            yield Double.parseDouble(val);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    yield val;
                }
            };
        }
        return expr.accept(null);
    }

    public static Object arrayGet(Object container, Object index) {
        return switch (container) {
            case MapExpression map -> {
                String key = String.valueOf(index);
                Expression val = map.getEntries().get(key);
                if (val == null) {
                    throw new RuntimeException("Map key not found: " + key);
                }
                yield evalExpr(val);
            }
            case ArrayExpression arr -> {
                int i = ((Number) index).intValue();
                yield evalExpr(arr.getMembers().get(i));
            }
            case ListExpression list -> {
                int i = ((Number) index).intValue();
                yield evalExpr(list.getMembers().get(i));
            }
            default ->
                throw new RuntimeException("Not indexable: " + container);
        };
    }

    public static Object safeArrayGet(Object container, Object index) {
        try {
            return arrayGet(container, index);
        } catch (IndexOutOfBoundsException e) {
            return NullValue.INSTANCE;
        }
    }

    public static void arraySet(Object container, Object index, Object value) {
        switch (container) {
            case ArrayExpression arr ->
                arr.getMembers().set(((Number) index).intValue(), wrapExpr(value));
            case ListExpression list ->
                list.getMembers().set(((Number) index).intValue(), wrapExpr(value));
            case MapExpression map ->
                map.getEntries().put(String.valueOf(index), wrapExpr(value));
            default ->
                throw new RuntimeException("Not indexable: " + container);
        }
    }

    public static Object resolveIfNamespace(Object val, Environment globals) {
        if (val instanceof String name && globals.exists(name)) {
            return globals.get(name);
        }
        return val;
    }

    public static Object fieldGet(Object obj, String field) {
        if (obj instanceof String name) {
            throw new RuntimeException("Field access on string name - use $ to look up: " + name);
        }
        if (!(obj instanceof Environment env)) {
            throw new RuntimeException("Field access on non-object: " + obj);
        }
        return env.get(field);
    }

    public static Object optionalFieldGet(Object obj, String field) {
        if (obj == null || obj instanceof NullValue) {
            return NullValue.INSTANCE;
        }
        return fieldGet(obj, field);
    }

    public static void fieldSet(Object obj, String field, Object value) {
        if (!(obj instanceof Environment env)) {
            throw new RuntimeException("Field assign on non-object");
        }
        env.assign(field, value);
    }

    public static Object methodCall(Object obj, String method, Object[] args) {
        if (!(obj instanceof Environment env)) {
            throw new RuntimeException("Method call on non-object: " + obj);
        }
        Object fn = env.get(method);
        if (!(fn instanceof Callable callable)) {
            throw new RuntimeException("Not callable: " + method);
        }
        Environment prev = METHOD_ENV.get();
        METHOD_ENV.set(env);
        try {
            return callable.call(null, Arrays.asList(args));
        } finally {
            METHOD_ENV.set(prev);
        }
    }

    public static Object optionalMethodCall(Object obj, String method, Object[] args) {
        if (obj == null || obj instanceof NullValue) {
            return NullValue.INSTANCE;
        }
        return methodCall(obj, method, args);
    }

    public static Object localCallableError(String name) {
        throw new com.mira.error.runtime.RuntimeError.LocalCallableError(name);
    }

    public static Object callNamed(Environment globals, String name, Object[] args) {
        Object callee = globals.get(name);
        if (!(callee instanceof Callable callable)) {
            throw new RuntimeException("Not callable: " + name);
        }
        return callable.call(null, Arrays.asList(args));
    }

    public static Object namespaceCall(Environment globals, String ns, String fn, Object[] args) {
        Object nsObj = globals.get(ns);
        if (!(nsObj instanceof Namespace namespace)) {
            throw new RuntimeException("Not a namespace: " + ns);
        }
        Object callee = namespace.get(fn);
        if (!(callee instanceof Callable callable)) {
            throw new RuntimeException("Not callable: " + ns + "." + fn);
        }
        return callable.call(null, Arrays.asList(args));
    }

    public static Object dynamicCall(Object callee, Object[] args) {
        if (!(callee instanceof Callable callable)) {
            throw new RuntimeException("Not callable: " + callee);
        }
        return callable.call(null, Arrays.asList(args));
    }

    public static Object pipe(Object value, Object fn, Object[] extraArgs) {
        Object[] allArgs = new Object[1 + extraArgs.length];
        allArgs[0] = value;
        System.arraycopy(extraArgs, 0, allArgs, 1, extraArgs.length);
        return dynamicCall(fn, allArgs);
    }

    public static Environment makeObject() {
        return new Environment();
    }

    public static Environment makeEnum(String[] keys, Object[] values) {
        Environment env = new Environment(null, keys.length);
        for (int i = 0; i < keys.length; i++) {
            env.defineConst(keys[i], values[i]);
        }
        return env;
    }

    public static ThrowSignal makeThrow(String type, Object value) {
        return new ThrowSignal(type, value);
    }

    public static String toDisplayString(Object val) {
        if (val == null || val instanceof NullValue) {
            return "null";
        }
        return String.valueOf(val);
    }

    public static int collectionSize(Object container) {
        return switch (container) {
            case ArrayExpression arr ->
                arr.getMembers().size();
            case ListExpression list ->
                list.getMembers().size();
            case String s ->
                s.length();
            default ->
                throw new RuntimeException("Not iterable: " + container);
        };
    }

    public static Object collectionGet(Object container, int index) {
        return switch (container) {
            case ArrayExpression arr ->
                evalExpr(arr.getMembers().get(index));
            case ListExpression list ->
                evalExpr(list.getMembers().get(index));
            case String s ->
                String.valueOf(s.charAt(index));
            default ->
                throw new RuntimeException("Not iterable: " + container);
        };
    }

    public static Object wrapArgs(String[] args) {
        List<Expression> members = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                members.add(wrapExpr(arg));
            }
        }
        return new ListExpression(members);
    }

    public static Object variadicTail(Object[] args, int from) {
        List<Expression> members = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            members.add(wrapExpr(args[i]));
        }
        return new ListExpression(members);
    }

    public static Object asyncWrap(Callable callable, Object[] args) {
        java.util.List<Object> argsList = java.util.Arrays.asList(args);
        java.util.concurrent.CompletableFuture<Object> future
                = java.util.concurrent.CompletableFuture.supplyAsync(() -> callable.call(null, argsList));
        return new com.mira.runtime.functions.Promise(future);
    }

    public static Object awaitPromise(Object value) {
        if (value instanceof com.mira.runtime.functions.Promise promise) {
            try {
                return promise.getFuture().get();
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ThrowSignal ts) {
                    throw ts;
                }
                throw new ThrowSignal("AsyncError", cause != null ? cause.getMessage() : "async error");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ThrowSignal("InterruptedError", e.getMessage());
            }
        }
        return value;
    }
}
