package com.mira.integration.compiler.statement;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.statement.AbstractForeachTests;

public class ForeachTest extends AbstractForeachTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }
}
