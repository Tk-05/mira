package com.mira.error.resolver;

import com.mira.error.MiraError;
import java.util.List;

public class MultipleStaticCheckErrors extends RuntimeException {

    private final List<MiraError> errors;

    public MultipleStaticCheckErrors(List<MiraError> errors) {
        super("Multiple static check errors (" + errors.size() + ")");
        this.errors = errors;
    }

    public List<MiraError> getErrors() {
        return errors;
    }
}
