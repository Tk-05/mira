package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractRangeExpressionTests;

public class RangeExpressionTest extends AbstractRangeExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void rangeDefaultStep() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var last : 0;
                for(var element in <0..4,2>) {
                    last : element;
                }
                last;
                """)));
    }

    @Test
    void rangeBreakOnValue() {
        try {
            backend.runAndGetValue("""
                    for(var element in <0..4>) {
                        if(element == 3) { break; }
                    }
                    """);
        } catch (BreakSignal ignored) {
        }
    }

    @Test
    void rangeStartValue() {
        backend.runAndGetValue("""
                var first : 0;
                for(var element in <3..6>) {
                    first : element;
                    break;
                }
                """);
        assertEquals(3.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("first")));
    }

    @Test
    void rangeWithStepSizeTwo() {
        assertEquals(5.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var count : 0;
                for(var element in <0..10,2>) {
                    count : (count + 1);
                }
                (count);
                """)));
    }

    @Test
    void rangeInForLoop() {
        backend.runAndGetValue("""
                var last : 0;
                for(var i in <0..5>) {
                    last : i;
                }
                """);
        assertEquals(4.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("last")));
    }
}
