package com.mira.integration.compiler.expression;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.expression.AbstractPipeExpressionTests;

public class PipeExpressionTest extends AbstractPipeExpressionTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }
}
