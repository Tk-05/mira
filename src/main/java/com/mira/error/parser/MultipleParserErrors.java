package com.mira.error.parser;

import com.mira.error.MiraError;
import java.util.List;

public class MultipleParserErrors extends RuntimeException {

    private final List<MiraError> errors;

    public MultipleParserErrors(List<MiraError> errors) {
        super("Multiple parser errors (" + errors.size() + ")");
        this.errors = errors;
    }

    public List<MiraError> getErrors() {
        return errors;
    }
}
