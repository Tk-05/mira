package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class WhileTest extends InterpreterTestBase {

    @Test
    void simpleWhile() {
        assertNull(run("""
                var i : 0;
                while($i <= 10){
                    $i : eval($i + 1);
                }
                """));
    }

    @Test
    void whileCountsCorrectly() {
        run("""
                var i : 0;
                while($i < 5){
                    $i : eval($i + 1);
                }
                """);
        assertEquals(5.0, Interpreter.getGlobalEnvironment().get("i"));
    }

    @Test
    void whileFalseNeverExecutes() {
        run("""
                var executed : false;
                while(0){
                    $executed : true;
                }
                """);
        assertEquals(Boolean.FALSE, Interpreter.getGlobalEnvironment().get("executed"));
    }

    @Test
    void whileWithBreak() {
        assertNull(run("""
                var x : 0;
                while($x < 100) {
                    $x : eval($x + 1);
                    if($x == 5) { break(); }
                }
                """));
    }

    @Test
    void whileWithBreakChecksValue() {
        run("""
                var x : 0;
                while(1) {
                    $x : eval($x + 1);
                    if($x >= 3) { break(); }
                }
                """);
        assertEquals(3.0, Interpreter.getGlobalEnvironment().get("x"));
    }

    @Test
    void nestedWhile() {
        run("""
                var outer : 0;
                var inner : 0;
                var total : 0;
                while($outer < 3) {
                    $outer : eval($outer + 1);
                    $inner : 0;
                    while($inner < 3) {
                        $inner : eval($inner + 1);
                        $total : eval($total + 1);
                    }
                }
                """);
        assertEquals(9.0, Interpreter.getGlobalEnvironment().get("total"));
    }
}
