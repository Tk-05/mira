package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractTernaryExpressionTests;

public class TernaryExpressionTest extends AbstractTernaryExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void trueBranchReturned() {
        try {
            backend.runAndGetValue("return true ? \"yes\" : \"no\";");
        } catch (ReturnSignal r) {
            assertEquals("yes", r.getValue());
        }
    }

    @Test
    void falseBranchReturned() {
        try {
            backend.runAndGetValue("return false ? \"yes\" : \"no\";");
        } catch (ReturnSignal r) {
            assertEquals("no", r.getValue());
        }
    }

    @Test
    void nestedTernaryInThenBranch() {
        try {
            backend.runAndGetValue("var x : 10; return x > 5 ? (x > 8 ? \"high\" : \"mid\") : \"low\";");
        } catch (ReturnSignal r) {
            assertEquals("high", r.getValue());
        }
    }

    @Test
    void nestedTernaryInElseBranch() {
        try {
            backend.runAndGetValue("var x : 2; return x > 5 ? \"high\" : (x > 1 ? \"mid\" : \"low\");");
        } catch (ReturnSignal r) {
            assertEquals("mid", r.getValue());
        }
    }

    @Test
    void ternaryWithArithmeticInBranch() {
        try {
            backend.runAndGetValue("var x : 4; return x > 3 ? (x * 2) : (x + 1);");
        } catch (ReturnSignal r) {
            assertEquals(8.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void ternaryWithBooleanVariable() {
        try {
            backend.runAndGetValue("var flag : true; return flag ? 1 : 0;");
        } catch (ReturnSignal r) {
            assertEquals(1.0, InterpreterRunner.normNum(r.getValue()));
        }
    }
}
