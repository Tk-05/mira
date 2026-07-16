package com.mira.runtime.interpreter;

import java.util.List;

import com.mira.parser.nodes.statement.Statement.FuncDecl;

public final class StructTemplate {

    private final Environment defaults;
    private final List<FuncDecl> methods;

    public StructTemplate(Environment defaults, List<FuncDecl> methods) {
        this.defaults = defaults;
        this.methods = methods;
    }

    public Environment getDefaults() {
        return defaults;
    }

    public List<FuncDecl> getMethods() {
        return methods;
    }
}
