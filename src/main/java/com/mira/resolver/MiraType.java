package com.mira.resolver;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The static-checker's own type model - deliberately small (no generics, no
 * unions beyond nullable). Resolved from a parsed {@code TypeAnnotation} by
 * {@link StaticCheck}, and only ever compared against types the checker itself
 * infers - it has no runtime representation, unlike Mira's actual values.
 */
public sealed interface MiraType
        permits MiraType.NamedType, MiraType.NullableType, MiraType.AnyType, MiraType.FunctionType {

    record NamedType(String name) implements MiraType {

    }

    record NullableType(MiraType inner) implements MiraType {

    }

    record AnyType() implements MiraType {
    }

    record FunctionType(List<MiraType> params, MiraType returnType) implements MiraType {

    }

    MiraType ANY = new AnyType();
    MiraType NUMBER = new NamedType("Number");
    MiraType STRING = new NamedType("String");
    MiraType BOOL = new NamedType("Bool");
    MiraType LIST = new NamedType("List");
    MiraType ARRAY = new NamedType("Array");
    MiraType MAP = new NamedType("Map");
    MiraType OBJECT = new NamedType("Object");
    MiraType FN = new NamedType("Fn");
    MiraType NULL = new NamedType("Null");
    MiraType VOID = new NamedType("Void");

    static boolean isVoid(MiraType type) {
        return type instanceof NamedType n && "Void".equals(n.name());
    }

    static String display(MiraType type) {
        return switch (type) {
            case NamedType n -> n.name();
            case NullableType n -> display(n.inner()) + "?";
            case AnyType ignored -> "Any";
            case FunctionType f -> "Fn(" + f.params().stream().map(MiraType::display).collect(Collectors.joining(", "))
                    + ") -> " + display(f.returnType());
        };
    }

    static boolean isAssignable(MiraType from, MiraType to) {
        if (from instanceof AnyType || to instanceof AnyType) {
            return true;
        }
        if (to instanceof NullableType nullableTo) {
            if (from instanceof NamedType named && "Null".equals(named.name())) {
                return true;
            }
            if (from instanceof NullableType nullableFrom) {
                return isAssignable(nullableFrom.inner(), nullableTo.inner());
            }
            return isAssignable(from, nullableTo.inner());
        }
        if (from instanceof NullableType) {
            // a possibly-null value can't flow into a non-nullable target
            return false;
        }
        // a value of some specific function shape always fits the plain "Fn"
        // type, and a plain "Fn" value fits any specific shape too - it's an
        // unknown-shaped function, not a wrong-shaped one, so don't guess wrong
        if (from instanceof FunctionType && to instanceof NamedType namedTo && "Fn".equals(namedTo.name())) {
            return true;
        }
        if (to instanceof FunctionType && from instanceof NamedType namedFrom && "Fn".equals(namedFrom.name())) {
            return true;
        }
        if (from instanceof FunctionType fnFrom && to instanceof FunctionType fnTo) {
            return isFunctionAssignable(fnFrom, fnTo);
        }
        if (from instanceof NamedType namedFrom && to instanceof NamedType namedTo) {
            return namedFrom.name().equals(namedTo.name());
        }
        return false;
    }

    private static boolean isFunctionAssignable(FunctionType from, FunctionType to) {
        if (from.params().size() != to.params().size()) {
            return false;
        }
        for (int i = 0; i < from.params().size(); i++) {
            MiraType a = from.params().get(i);
            MiraType b = to.params().get(i);
            if (!isAssignable(a, b) && !isAssignable(b, a)) {
                return false;
            }
        }
        return isAssignable(from.returnType(), to.returnType()) || isAssignable(to.returnType(), from.returnType());
    }
}
