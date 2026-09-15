package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractSwitchExpressionTests;

public class SwitchExpressionTest extends AbstractSwitchExpressionTests {

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
    void noMatchNoDefaultReturnsNull() {
        assertNull(backend.runAndGetValue("switch(5) { case 1 -> 1 }"));
    }

    @Test
    void usedAsReturnValue() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn classify(n) {
                    return switch(n) {
                        case 1 -> 10
                        case 2 -> 20
                        default -> 0
                    };
                }
                (classify(1));
                """)));
    }

    @Test
    void defaultWhenNoMatch() {
        assertEquals(0.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn classify(n) {
                    return switch(n) {
                        case 1 -> 10
                        case 2 -> 20
                        default -> 0
                    };
                }
                (classify(99));
                """)));
    }

    @Test
    void caseWithExpression() {
        assertEquals(6.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 3;
                (switch(x) {
                    case 3 -> eval(x * 2)
                    default -> 0
                });
                """)));
    }

    @Test
    void statementSwitchArrowDefault() {
        backend.runAndGetValue("""
                var result : 0;
                switch(5) {
                    case 1 -> result : 1;
                    default -> result : 99;
                }
                """);
        assertEquals(99.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("result")));
    }
}
