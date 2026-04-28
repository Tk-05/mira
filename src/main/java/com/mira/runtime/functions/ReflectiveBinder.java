package com.mira.runtime.functions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mira.error.runtime.RuntimeError;
import com.mira.runtime.interpreter.Environment;

public final class ReflectiveBinder {

    private static final String BP_CLASS_NAME = "org.bytedeco.javacpp.BytePointer";

    private ReflectiveBinder() {
    }

    private static boolean isBytePointerType(Class<?> type) {
        Class<?> c = type;
        while (c != null) {
            if (BP_CLASS_NAME.equals(c.getName())) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    public static void bindMethods(Class<?> source, Environment env) {
        for (Map.Entry<String, Method> entry : selectMethods(source).entrySet()) {
            if (!env.exists(entry.getKey())) {
                registerMethod(entry.getKey(), entry.getValue(), env);
            }
        }
    }

    public static void bindConstants(Class<?> source, Environment env) {
        for (Field f : source.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (!Modifier.isFinal(f.getModifiers())) {
                continue;
            }
            if (f.getDeclaringClass() != source) {
                continue;
            }
            if (env.exists(f.getName())) {
                continue;
            }
            try {
                env.define(f.getName(), coerceReturn(f.get(null), f.getType()));
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static Map<String, Method> selectMethods(Class<?> source) {
        Map<String, List<Method>> grouped = new LinkedHashMap<>();
        for (Method m : source.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getDeclaringClass() != source) {
                continue;
            }
            grouped.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        Map<String, Method> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Method>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), pickWinner(entry.getValue()));
        }
        return result;
    }

    private static Method pickWinner(List<Method> overloads) {
        return overloads.stream()
                .max(Comparator.comparingInt(ReflectiveBinder::methodScore)
                        .thenComparingInt(Method::getParameterCount))
                .orElseThrow();
    }

    private static int methodScore(Method m) {
        int score = 0;
        for (Class<?> p : m.getParameterTypes()) {
            score += typeScore(p);
        }
        return score;
    }

    private static int typeScore(Class<?> type) {
        if (isBytePointerType(type)) {
            return -1;
        }
        if (type == double.class || type == Double.class) {
            return 4;
        }
        if (type == float.class || type == Float.class) {
            return 3;
        }
        if (type == long.class || type == Long.class) {
            return 2;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        return 10;
    }

    private static void registerMethod(String name, Method method, Environment env) {
        int arity = method.getParameterCount();
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();

        env.define(name, new NativeFunction(arity, args -> {
            try {
                Object[] javaArgs = new Object[arity];
                for (int i = 0; i < arity; i++) {
                    javaArgs[i] = coerceArg(args.get(i), paramTypes[i]);
                }
                return coerceReturn(method.invoke(null, javaArgs), returnType);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new RuntimeError.InvalidArgumentError(name,
                        cause != null ? cause.getMessage() : e.getMessage());
            } catch (IllegalAccessException e) {
                throw new RuntimeError.InvalidArgumentError(name, "method not accessible");
            }
        }));
    }

    private static Object coerceArg(Object val, Class<?> target) {
        if (target == int.class || target == Integer.class) {
            return ((Number) val).intValue();
        }
        if (target == float.class || target == Float.class) {
            return ((Number) val).floatValue();
        }
        if (target == double.class || target == Double.class) {
            return ((Number) val).doubleValue();
        }
        if (target == long.class || target == Long.class) {
            return ((Number) val).longValue();
        }
        if (target == boolean.class || target == Boolean.class) {
            return val;
        }
        if (target == String.class) {
            return String.valueOf(val);
        }
        if (isBytePointerType(target)) {
            try {
                byte[] utf8 = (String.valueOf(val) + "\0").getBytes(StandardCharsets.UTF_8);
                return target.getConstructor(byte[].class).newInstance((Object) utf8);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to convert string to BytePointer", e);
            }
        }
        return target.cast(val);
    }

    static Object coerceReturn(Object val, Class<?> type) {
        if (type == void.class || val == null) {
            return null;
        }
        if (isBytePointerType(val.getClass())) {
            try {
                Method isNull = val.getClass().getMethod("isNull");
                if ((Boolean) isNull.invoke(val)) {
                    return "";
                }
                Method getString = val.getClass().getMethod("getString");
                return getString.invoke(val);
            } catch (ReflectiveOperationException e) {
                return "";
            }
        }
        if (type == int.class || type == Integer.class) {
            return ((Number) val).doubleValue();
        }
        if (type == float.class || type == Float.class) {
            return ((Number) val).doubleValue();
        }
        if (type == long.class || type == Long.class) {
            return ((Number) val).doubleValue();
        }
        return val;
    }
}
