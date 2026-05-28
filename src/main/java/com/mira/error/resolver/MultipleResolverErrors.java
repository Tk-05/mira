package com.mira.error.resolver;

import com.mira.error.MiraError;
import java.util.List;

public class MultipleResolverErrors extends RuntimeException {

    private final List<MiraError> errors;

    public MultipleResolverErrors(List<MiraError> errors) {
        super("Multiple resolver errors (" + errors.size() + ")");
        this.errors = errors;
    }

    public List<MiraError> getErrors() {
        return errors;
    }
}
