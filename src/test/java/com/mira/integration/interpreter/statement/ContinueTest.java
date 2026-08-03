package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ContinueSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractContinueTests;

public class ContinueTest extends AbstractContinueTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void continueAtTopLevelThrows() {
        assertThrows(ContinueSignal.class, () -> backend.runAndGetValue("continue;"));
    }

    @Test
    void continueInsideForeachList() {
        backend.runAndGetValue("""
                var sum : 0;
                var list : {1, 2, 3, 4, 5};
                for (var e in $list) {
                    if ($e == 3) { continue; }
                    $sum : eval($sum + $e);
                }
                """);
        assertEquals(12.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("sum")));
    }

    @Test
    void continueOnlyAffectsInnermostLoop() {
        backend.runAndGetValue("""
                var outer : 0;
                var inner : 0;
                var j : 0;
                while ($outer < 3) {
                    $outer : eval($outer + 1);
                    $j : 0;
                    while ($j < 4) {
                        $j : eval($j + 1);
                        if ($j == 2) { continue; }
                        $inner : eval($inner + 1);
                    }
                }
                """);
        assertEquals(3.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("outer")));
        assertEquals(9.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("inner")));
    }

    @Test
    void continueDoesNotAffectPostLoopExecution() {
        backend.runAndGetValue("""
                var x : 0;
                while ($x < 3) {
                    $x : eval($x + 1);
                    continue;
                }
                $x : eval($x + 10);
                """);
        assertEquals(13.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }
}
