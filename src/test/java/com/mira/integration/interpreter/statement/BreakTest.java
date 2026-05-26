package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractBreakTests;

public class BreakTest extends AbstractBreakTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void breakAtTopLevelThrows() {
        assertThrows(BreakSignal.class, () -> backend.runAndGetValue("break;"));
    }

    @Test
    void deepNestedBreak() {
        backend.runAndGetValue("""
                var outer : 0;
                var middle : 0;
                var inner : 0;
                while ($outer < 3) {
                    $outer : eval($outer + 1);
                    while ($middle < 5) {
                        $middle : eval($middle + 1);
                        while (1) {
                            $inner : eval($inner + 1);
                            break;
                        }
                    }
                }
                """);
        assertEquals(3.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("outer")));
        assertEquals(5.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("middle")));
        assertEquals(5.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("inner")));
    }

    @Test
    void breakDoesNotAffectPostLoopExecution() {
        backend.runAndGetValue("""
                var x : 0;
                while ($x < 3) {
                    $x : eval($x + 1);
                    while (1) { break; }
                }
                $x : eval($x + 10);
                """);
        assertEquals(13.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }
}
