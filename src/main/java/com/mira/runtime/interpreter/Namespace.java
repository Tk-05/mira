package com.mira.runtime.interpreter;

public class Namespace extends Environment {

    private final String alias;

    public Namespace(String alias) {
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }
}
