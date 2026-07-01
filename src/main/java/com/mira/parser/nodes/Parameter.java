package com.mira.parser.nodes;

import com.mira.parser.nodes.expression.Expression;

public record Parameter(String name, Expression defaultValue, int column) {

    public Parameter(String name, Expression defaultValue) {
        this(name, defaultValue, 0);
    }

    public boolean hasDefault() {
        return defaultValue != null;
    }
}
