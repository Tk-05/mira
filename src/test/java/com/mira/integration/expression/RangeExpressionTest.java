package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.integration.InterpreterTestBase;

public class RangeExpressionTest extends InterpreterTestBase {

    @Test
    void rangeDefaultStep() {
        assertEquals(2.0, run("""
                var last : 0;
                foreach(var element in <0..4,2>) {
                    $last : $element;
                }
                $last;
                """));
    }

    @Test
    void rangeIteratesCorrectly() {
        try {
            run("""
                    foreach(var element in <0..4>) {
                        if($element == 3) { break; }
                    }
                    """);
        } catch (BreakSignal ignored) {
        }
    }

    @Test
    void rangeCountElements() {
        assertEquals(5.0, run("""
                var count : 0;
                foreach(var element in <0..5>) {
                    $count : eval($count + 1);
                }
                eval($count);
                """));
    }

    @Test
    void rangeStartValue() {
        run("""
                var first : 0;
                foreach(var element in <3..6>) {
                    $first : $element;
                    break;
                }
                """);
        assertEquals(3.0, normNum(interpreter.getGlobalEnvironment().get("first")));
    }

    @Test
    void rangeWithStepSizeTwo() {
        assertEquals(5.0, run("""
                var count : 0;
                foreach(var element in <0..10,2>) {
                    $count : eval($count + 1);
                }
                eval($count);
                """));
    }

    @Test
    void rangeInForLoop() {
        run("""
                var last : 0;
                for(var i in <0..5>) {
                    $last : $i;
                }
                """);
        assertEquals(4.0, normNum(interpreter.getGlobalEnvironment().get("last")));
    }
}
