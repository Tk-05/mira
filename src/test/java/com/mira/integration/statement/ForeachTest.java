package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterTestBase;

public class ForeachTest extends InterpreterTestBase {

    @Test
    void foreachOnList() {
        assertEquals(3.0, run("""
                var list : {1,2,3};
                var lastResult;
                foreach(var element in $list) {
                    $lastResult : $element;
                }
                $lastResult;
                """));
    }

    @Test
    void foreachOnTuple() {
        assertEquals(3.0, run("""
                var list : [1,2,3];
                var lastResult;
                foreach(var element in $list) {
                    $lastResult : $element;
                }
                $lastResult;
                """));
    }

    @Test
    void foreachOnString() {
        assertEquals("C", run("""
                var string : "ABC";
                var lastResult;
                foreach(var element in $string) {
                    $lastResult : $element;
                }
                $lastResult;
                """));
    }

    @Test
    void foreachWithBreak() {
        try {
            run("""
                    var list : {1,2,3};
                    foreach(var element in $list) {
                        if($element == 1) { break; }
                    }
                    """);
        } catch (BreakSignal ignored) {
        }
    }

    @Test
    void foreachWithReturn() {
        try {
            run("""
                    var list : {1,2,3};
                    foreach(var element in $list) {
                        if($element == 1) { return; }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void nestedForeach() {
        try {
            run("""
                    var list1 : {1,2,3};
                    var list2 : {4,5,6};
                    foreach(var e1 in $list1) {
                        foreach(var e2 in $list2) {
                            if($e1 == 3 && $e2 == 6) { break; }
                        }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void foreachOnNestedList() {
        try {
            run("""
                    var list1 : {{1,2,3}};
                    foreach(var element in $list1[0]) {
                        if($element == 3) { break; }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void foreachWithRange() {
        try {
            run("""
                    foreach(var element in <0..4>) {
                        if($element == 3) { break; }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void foreachWithCounter() {
        assertEquals(3.0, run("""
                var count : 0;
                var list : {10, 20, 30};
                foreach(var element in $list) {
                    $count : eval($count + 1);
                }
                eval($count);
                """));
    }

    @Test
    void foreachIteratesAllElements() {
        assertEquals(6.0, run("""
                var sum : 0;
                var list : {1, 2, 3};
                foreach(var element in $list) {
                    $sum : $sum + $element;
                }
                eval($sum);
                """));
    }
}
