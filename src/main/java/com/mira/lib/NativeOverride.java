package com.mira.lib;

import java.util.List;

/**
 * One exposed member of a {@link ReflectiveLib} that the auto-scanner can't
 * (or shouldn't) resolve on its own: either a typed disambiguation hint for
 * an overload the reflective scan would otherwise guess at, or fully custom
 * glue (struct constructors, native pointer building) with no reflectable
 * Java method behind it at all. Either way, {@code paramTypes}/{@code
 * returnType} are always declared, so the static checker can typecheck
 * calls against it regardless of which case this is.
 */
public record NativeOverride(String name, List<NativeType> paramTypes, NativeType returnType,
        String paramHint, Body body) {

    public interface Body {

        Object execute(List<Object> args);
    }

    /**
     * Declares the exact signature of an overload the target class(es)
     * already implement, so it's resolved by matching Java method to
     * declared types instead of the auto-scanner's heuristic scoring.
     */
    public static NativeOverride typed(String name, List<NativeType> paramTypes, NativeType returnType) {
        return new NativeOverride(name, paramTypes, returnType, "", null);
    }

    /**
     * Custom glue with no reflectable Java method behind it (or one that
     * needs pre/post-processing reflection can't express) - the given body
     * runs directly, exactly like a hand-written {@code NativeFunction}.
     */
    public static NativeOverride custom(String name, List<NativeType> paramTypes, NativeType returnType, Body body) {
        return new NativeOverride(name, paramTypes, returnType, "", body);
    }

    public static NativeOverride custom(String name, List<NativeType> paramTypes, NativeType returnType,
            String paramHint, Body body) {
        return new NativeOverride(name, paramTypes, returnType, paramHint, body);
    }

    public boolean isCustom() {
        return body != null;
    }
}
