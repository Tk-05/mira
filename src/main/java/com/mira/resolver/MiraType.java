package com.mira.resolver;

/**
 * The static-checker's own type model - deliberately small (no generics, no
 * unions beyond nullable). Resolved from a parsed {@code TypeAnnotation} by
 * {@link StaticCheck}, and only ever compared against types the checker itself
 * infers - it has no runtime representation, unlike Mira's actual values.
 */
public sealed interface MiraType permits MiraType.NamedType, MiraType.NullableType, MiraType.AnyType {

    record NamedType(String name) implements MiraType {

    }

    record NullableType(MiraType inner) implements MiraType {

    }

    record AnyType() implements MiraType {
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
            case NamedType n ->
                n.name();
            case NullableType n ->
                display(n.inner()) + "?";
            case AnyType ignored ->
                "Any";
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
        if (from instanceof NamedType namedFrom && to instanceof NamedType namedTo) {
            return namedFrom.name().equals(namedTo.name());
        }
        return false;
    }
}
