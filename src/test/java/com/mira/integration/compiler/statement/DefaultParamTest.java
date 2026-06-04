package com.mira.integration.compiler.statement;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.statement.AbstractDefaultParamTests;

public class DefaultParamTest extends AbstractDefaultParamTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }
}
