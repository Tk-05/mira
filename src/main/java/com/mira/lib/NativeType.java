package com.mira.lib;

import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;

/**
 * Mira's built-in type vocabulary, mirrored standalone here so a
 * {@link ReflectiveLib} can declare argument/return types without pulling in
 * the resolver's own resolved-type model (which lives in a different layer and
 * has no reason to know about native interop). {@link #miraTypeName()} matches
 * the resolver's built-in type names exactly, so the static checker can resolve
 * a declared native signature the same way it resolves any other type
 * annotation.
 */
public enum NativeType {
    NUMBER, STRING, BOOL, LIST, ARRAY, MAP, OBJECT, VOID, ANY;

    public String miraTypeName() {
        return switch (this) {
            case NUMBER -> "Number";
            case STRING -> "String";
            case BOOL -> "Bool";
            case LIST -> "List";
            case ARRAY -> "Array";
            case MAP -> "Map";
            case OBJECT -> "Object";
            case VOID -> "Void";
            case ANY -> "Any";
        };
    }

    public static NativeType fromMiraTypeName(String name) {
        for (NativeType t : values()) {
            if (t.miraTypeName().equals(name)) {
                return t;
            }
        }
        return ANY;
    }

    /**
     * Whether a reflected Java parameter/return type is a plausible match for this
     * declared type.
     */
    public boolean matchesJavaType(Class<?> type) {
        return switch (this) {
            case NUMBER -> type == int.class || type == Integer.class || type == double.class || type == Double.class
                    || type == float.class || type == Float.class || type == long.class || type == Long.class
                    || type == short.class || type == Short.class || type == byte.class || type == Byte.class;
            case STRING -> type == String.class;
            case BOOL -> type == boolean.class || type == Boolean.class;
            case VOID -> type == void.class;
            case LIST, ARRAY, MAP, OBJECT -> !type.isPrimitive();
            case ANY -> true;
        };
    }

    public static NativeType fromRuntimeValue(Object value) {
        return switch (value) {
            case Number ignored -> NUMBER;
            case String ignored -> STRING;
            case Boolean ignored -> BOOL;
            case ListExpression ignored -> LIST;
            case ArrayExpression ignored -> ARRAY;
            case MapExpression ignored -> MAP;
            case null, default -> null;
        };
    }

    /**
     * Best-effort classification of a reflected Java type, for describing an
     * auto-scanned method.
     */
    public static NativeType fromJavaClass(Class<?> type) {
        if (type == int.class || type == Integer.class || type == double.class || type == Double.class
                || type == float.class || type == Float.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return NUMBER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return BOOL;
        }
        if (type == String.class) {
            return STRING;
        }
        if (type == void.class) {
            return VOID;
        }
        return ANY;
    }
}
