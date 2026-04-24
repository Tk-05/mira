package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ContinueSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class ContinueTest extends InterpreterTestBase {

    @Test
    void continueAtTopLevelThrows() {
        assertThrows(ContinueSignal.class, () -> run("continue;"));
    }

    @Test
    void continueInsideWhile() {
        run("""
                var count : 0;
                var i : 0;
                while ($i < 5) {
                    $i : eval($i + 1);
                    if ($i == 3) {
                        continue;
                    }
                    $count : eval($count + 1);
                }
                """);
        assertEquals(4.0, normNum(interpreter.getGlobalEnvironment().get("count")));
    }

    @Test
    void continueInsideFor() {
        run("""
                var sum : 0;
                for (var i : 0; $i < 5; $i : eval($i + 1)) {
                    if ($i == 2) {
                        continue;
                    }
                    $sum : eval($sum + $i);
                }
                """);
        assertEquals(8.0, normNum(interpreter.getGlobalEnvironment().get("sum")));
    }

    @Test
    void continueInsideForeachList() {
        run("""
                var sum : 0;
                var list : {1, 2, 3, 4, 5};
                foreach (var e in $list) {
                    if ($e == 3) {
                        continue;
                    }
                    $sum : eval($sum + $e);
                }
                """);
        assertEquals(12.0, normNum(interpreter.getGlobalEnvironment().get("sum")));
    }

    @Test
    void continueInsideForeachRange() {
        run("""
                var sum : 0;
                foreach (var i in <0..6>) {
                    if ($i == 4) {
                        continue;
                    }
                    $sum : eval($sum + $i);
                }
                """);
        assertEquals(11.0, normNum(interpreter.getGlobalEnvironment().get("sum")));
    }

    @Test
    void continueOnlyAffectsInnermostLoop() {
        run("""
                var outer : 0;
                var inner : 0;
                var j : 0;
                while ($outer < 3) {
                    $outer : eval($outer + 1);
                    $j : 0;
                    while ($j < 4) {
                        $j : eval($j + 1);
                        if ($j == 2) {
                            continue;
                        }
                        $inner : eval($inner + 1);
                    }
                }
                """);
        assertEquals(3.0, normNum(interpreter.getGlobalEnvironment().get("outer")));
        assertEquals(9.0, normNum(interpreter.getGlobalEnvironment().get("inner")));
    }

    @Test
    void continueDoesNotAffectPostLoopExecution() {
        run("""
                var x : 0;
                while ($x < 3) {
                    $x : eval($x + 1);
                    continue;
                }
                $x : eval($x + 10);
                """);
        assertEquals(13.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }
}
