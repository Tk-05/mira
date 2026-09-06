package com.mira.error.lexer;

import java.util.List;

import com.mira.error.MiraError;

public class MultipleLexerErrors extends RuntimeException {

    private final List<MiraError> errors;

    public MultipleLexerErrors(List<MiraError> errors) {
        super("Multiple lexer errors (" + errors.size() + ")");
        this.errors = errors;
    }

    public List<MiraError> getErrors() {
        return errors;
    }
}
