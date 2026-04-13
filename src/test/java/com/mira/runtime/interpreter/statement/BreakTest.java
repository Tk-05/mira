package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class BreakTest extends InterpreterTestBase {

    @Test
    void breakAtTopLevelThrows() {
        assertThrows(BreakSignal.class, () -> run("break;"));
    }

    @Test
    void breakInsideWhile() {
        assertNull(run("""
                while(1){
                    break;
                }
                """));
    }

    @Test
    void breakInsideFor() {
        assertNull(run("""
                for (var i : 0; $i < 100; $i : eval($i + 1)) {
                    break;
                }
                """));
    }

    @Test
    void breakInsideForeach() {
        assertNull(run("""
                var list : {1, 2, 3};
                foreach(var e in $list) {
                    break;
                }
                """));
    }

    @Test
    void deepNestedBreak() {
        run("""
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

        assertEquals(3.0, normNum(interpreter.getGlobalEnvironment().get("outer")));
        assertEquals(5.0, normNum(interpreter.getGlobalEnvironment().get("middle")));
        assertEquals(5.0, normNum(interpreter.getGlobalEnvironment().get("inner")));
    }

    @Test
    void breakDoesNotAffectPostLoopExecution() {
        run("""
                var x : 0;

                while ($x < 3) {
                    $x : eval($x + 1);

                    while (1) {
                        break;
                    }
                }

                $x : eval($x + 10);
                """);
        assertEquals(13.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void breakOnlyExitsInnermostLoop() {
        run("""
                var outer : 0;
                while($outer < 3) {
                    $outer : eval($outer + 1);
                    while(1) { break; }
                }
                """);
        assertEquals(3.0, normNum(interpreter.getGlobalEnvironment().get("outer")));
    }
}
