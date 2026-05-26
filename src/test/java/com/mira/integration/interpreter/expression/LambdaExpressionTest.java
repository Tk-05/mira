package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractLambdaExpressionTests;

public class LambdaExpressionTest extends AbstractLambdaExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void lambdaAssignedToVariableIsFunction() {
        try {
            backend.runAndGetValue("var f : fn(x) { return $x; }; return $f;");
        } catch (ReturnSignal r) {
            assertInstanceOf(Function.class, r.getValue());
        }
    }

    @Test
    void lambdaReturnsString() {
        try {
            backend.runAndGetValue("var greet : fn(name) { return \"Hello \" $name; }; return greet(\"World\");");
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }

    @Test
    void lambdaVoidReturn() {
        assertNull(backend.runAndGetValue("var f : fn() {}; f();"));
    }

    @Test
    void lambdaInlineAsArgument() {
        assertEquals(9.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn apply(f, x) { return $f($x); }
                eval(apply(fn(n) { return eval($n * $n); }, 3));
                """)));
    }

    @Test
    void higherOrderFunctionWithMultipleParams() {
        assertEquals(12.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn applyTwo(f, a, b) { return $f($a, $b); }
                var multiply : fn(x, y) { return eval($x * $y); };
                eval(applyTwo($multiply, 3, 4));
                """)));
    }

    @Test
    void constLambda() {
        assertEquals(4.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "const square : fn(x) { return eval($x * $x); }; eval(square(2));")));
    }
}
