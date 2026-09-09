package com.mira.runtime.functions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mira.error.runtime.RuntimeError;
import com.mira.lib.NativeOverride;
import com.mira.lib.NativeType;
import com.mira.lib.ReflectiveLib;
import com.mira.runtime.interpreter.Environment;

/**
 * Shared resolution core for {@link ReflectiveLib}: binds a library's declared
 * {@code overrides()} and auto-scanned {@code targets()} into an
 * {@link Environment} at native-import time, and (via {@link #describe})
 * produces the same signatures for the build-time manifest generator - the two
 * paths share this logic so they can never drift apart.
 */
public final class NativeInterop {

    private NativeInterop() {
    }

    /**
     * A single exposed member's declared Mira-facing signature.
     */
    public record ResolvedBinding(String name, List<NativeType> paramTypes, NativeType returnType) {

    }

    public static void bind(ReflectiveLib lib, Environment env) {
        for (NativeOverride override : lib.overrides()) {
            bindOverride(lib, override, env);
        }
        for (Map.Entry<String, Object> entry : lib.constants().entrySet()) {
            if (!env.exists(entry.getKey())) {
                env.define(entry.getKey(), entry.getValue());
            }
        }
        for (Class<?> target : lib.targets()) {
            ReflectiveBinder.bindMethods(target, env);
            ReflectiveBinder.bindConstants(target, env);
        }
    }

    private static void bindOverride(ReflectiveLib lib, NativeOverride override, Environment env) {
        if (env.exists(override.name())) {
            return;
        }
        int arity = override.paramTypes().size();
        if (override.isCustom()) {
            env.define(override.name(),
                    new NativeFunction(arity, override.paramHint(), args -> override.body().execute(args)));
            return;
        }
        Method method = resolveTypedMethod(lib.targets(), override.name(), override.paramTypes());
        registerTypedMethod(override.name(), override.paramHint(), method, env);
    }

    private static Method resolveTypedMethod(List<Class<?>> targets, String name, List<NativeType> paramTypes) {
        Method match = null;
        for (Class<?> target : targets) {
            for (Method m : target.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || !name.equals(m.getName())) {
                    continue;
                }
                if (m.getParameterCount() != paramTypes.size() || !paramsMatch(m.getParameterTypes(), paramTypes)) {
                    continue;
                }
                if (match != null) {
                    throw new RuntimeError.NativeBindingError(name,
                            "multiple methods match the declared signature - narrow the override's types");
                }
                match = m;
            }
        }
        if (match == null) {
            throw new RuntimeError.NativeBindingError(name,
                    "no method on the declared targets matches the override's declared signature");
        }
        return match;
    }

    private static boolean paramsMatch(Class<?>[] javaParams, List<NativeType> declared) {
        for (int i = 0; i < javaParams.length; i++) {
            if (!declared.get(i).matchesJavaType(javaParams[i])) {
                return false;
            }
        }
        return true;
    }

    private static void registerTypedMethod(String name, String paramHint, Method method, Environment env) {
        int arity = method.getParameterCount();
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();
        env.define(name, new NativeFunction(arity, paramHint, args -> {
            try {
                Object[] javaArgs = new Object[arity];
                for (int i = 0; i < arity; i++) {
                    javaArgs[i] = ReflectiveBinder.coerceArg(args.get(i), paramTypes[i]);
                }
                return ReflectiveBinder.coerceReturn(method.invoke(null, javaArgs), returnType);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new RuntimeError.InvalidArgumentError(name, cause != null ? cause.getMessage() : e.getMessage());
            } catch (IllegalAccessException e) {
                throw new RuntimeError.InvalidArgumentError(name, "method not accessible");
            }
        }));
    }

    /**
     * Typed signatures for every declared/auto-scanned member - consumed only by
     * the build-time manifest generator (never at runtime bind time, and never by
     * anything that must avoid loading the target classes).
     */
    public static List<ResolvedBinding> describe(ReflectiveLib lib) {
        List<ResolvedBinding> result = new ArrayList<>();
        for (NativeOverride override : lib.overrides()) {
            result.add(new ResolvedBinding(override.name(), override.paramTypes(), override.returnType()));
        }
        for (Map.Entry<String, Object> entry : lib.constants().entrySet()) {
            Object value = entry.getValue();
            NativeType type = value != null ? NativeType.fromJavaClass(value.getClass()) : NativeType.ANY;
            result.add(new ResolvedBinding(entry.getKey(), List.of(), type));
        }
        for (Class<?> target : lib.targets()) {
            for (Map.Entry<String, Method> entry : ReflectiveBinder.selectMethods(target).entrySet()) {
                Method m = entry.getValue();
                List<NativeType> params = new ArrayList<>();
                for (Class<?> p : m.getParameterTypes()) {
                    params.add(NativeType.fromJavaClass(p));
                }
                result.add(new ResolvedBinding(entry.getKey(), params, NativeType.fromJavaClass(m.getReturnType())));
            }
            for (Map.Entry<String, java.lang.reflect.Field> entry : ReflectiveBinder.selectConstants(target)
                    .entrySet()) {
                result.add(new ResolvedBinding(entry.getKey(), List.of(),
                        NativeType.fromJavaClass(entry.getValue().getType())));
            }
        }
        return result;
    }
}
