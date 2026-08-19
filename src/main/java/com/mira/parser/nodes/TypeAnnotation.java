package com.mira.parser.nodes;

/**
 * An optional type annotation on a var declaration, function parameter, or
 * function return type (e.g. {@code Int} in {@code var x : Int : 5;}, or
 * {@code Int?} for the nullable form). Deliberately NOT wired into the
 * Statement/Expression visitor hierarchy - it's never executed, only read by
 * the static checker and the LSP.
 */
public record TypeAnnotation(String name, boolean nullable) {

    @Override
    public String toString() {
        return name + (nullable ? "?" : "");
    }
}
