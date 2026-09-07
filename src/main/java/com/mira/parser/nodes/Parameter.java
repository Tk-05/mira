package com.mira.parser.nodes;

import com.mira.parser.nodes.expression.Expression;

public record Parameter(String name, Expression defaultValue, int column, TypeAnnotation type) {

    public Parameter(String name, Expression defaultValue) {
        this(name, defaultValue, 0, null);
    }

    public Parameter(String name, Expression defaultValue, int column) {
        this(name, defaultValue, column, null);
    }

    public boolean hasDefault() {
        return defaultValue != null;
    }
}
