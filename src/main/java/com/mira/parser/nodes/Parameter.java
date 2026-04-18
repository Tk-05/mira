package com.mira.parser.nodes;

import com.mira.parser.nodes.expression.Expression;

public record Parameter(String name, Expression defaultValue) {

    public boolean hasDefault() {
        return defaultValue != null;
    }
}
