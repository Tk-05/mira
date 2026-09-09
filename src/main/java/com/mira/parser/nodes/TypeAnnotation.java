package com.mira.parser.nodes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An optional type annotation on a var declaration, function parameter, or
 * function return type (e.g. {@code Int} in {@code var x : Int : 5;}, or
 * {@code Int?} for the nullable form). Deliberately NOT wired into the
 * Statement/Expression visitor hierarchy - it's never executed, only read by
 * the static checker and the LSP. Carries its own line/column (the type name
 * token's position) so type errors can point at the annotation itself rather
 * than the whole enclosing declaration.
 *
 * <p>
 * {@code paramTypes}/{@code returnType} are non-null only for a function-type
 * annotation ({@code Fn(Number, Number) -> Number}) - {@code name} is still
 * "Fn" in that case, so existing name-based checks keep working unchanged.
 */
public record TypeAnnotation(String name, boolean nullable, int line, int column, List<TypeAnnotation> paramTypes,
        TypeAnnotation returnType) {

    public TypeAnnotation(String name, boolean nullable) {
        this(name, nullable, 0, 0, null, null);
    }

    public TypeAnnotation(String name, boolean nullable, int line, int column) {
        this(name, nullable, line, column, null, null);
    }

    public boolean isFunctionType() {
        return paramTypes != null;
    }

    @Override
    public String toString() {
        String base = name;
        if (paramTypes != null) {
            base += "(" + paramTypes.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
            if (returnType != null) {
                base += " -> " + returnType;
            }
        }
        return base + (nullable ? "?" : "");
    }
}
