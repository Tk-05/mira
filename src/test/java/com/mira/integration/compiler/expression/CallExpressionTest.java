package com.mira.integration.compiler.expression;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.expression.AbstractCallExpressionTests;

public class CallExpressionTest extends AbstractCallExpressionTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }
}
