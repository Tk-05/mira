package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class SwitchTest extends InterpreterTestBase {

    @Test
    void matchesFirstCase() {
        try {
            run("""
                    var x : 1;
                    switch ($x) {
                        case (1) { ret(true); }
                        case (2) { ret(false); }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void matchesSecondCase() {
        try {
            run("""
                    var x : 2;
                    switch ($x) {
                        case (1) { ret(false); }
                        case (2) { ret(true); }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void defaultExecutedWhenNoMatch() {
        try {
            run("""
                    var x : 99;
                    switch ($x) {
                        case (1) { ret(false); }
                        case (2) { ret(false); }
                        default { ret(true); }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void noMatchNoDefaultReturnsNull() {
        assertNull(run("""
                var x : 99;
                switch ($x) {
                    case (1) { ret(false); }
                    case (2) { ret(false); }
                }
                """));
    }

    @Test
    void onlyFirstMatchExecutes() {
        run("""
                var x : 1;
                var count : 0;
                switch ($x) {
                    case (1) { $count : eval($count + 1); }
                    case (1) { $count : eval($count + 1); }
                }
                """);
        assertEquals(1.0, Interpreter.getGlobalEnvironment().get("count"));
    }

    @Test
    void matchesStringCase() {
        try {
            run("""
                    var x : "hello";
                    switch ($x) {
                        case ("world") { ret(false); }
                        case ("hello") { ret(true); }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void caseBodyCanHaveMultipleStatements() {
        run("""
                var x : 2;
                var a : 0;
                var b : 0;
                switch ($x) {
                    case (1) { $a : 1; $b : 1; }
                    case (2) { $a : 10; $b : 20; }
                }
                """);
        assertEquals("10", Interpreter.getGlobalEnvironment().get("a"));
        assertEquals("20", Interpreter.getGlobalEnvironment().get("b"));
    }

    @Test
    void switchWithExpressionAsSubject() {
        try {
            run("""
                    var x : 3;
                    var y : 2;
                    switch (eval($x - $y)) {
                        case (1) { ret(true); }
                        case (2) { ret(false); }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void switchInsideFunction() {
        assertEquals(42.0, run("""
                fn classify(n) {
                    switch ($n) {
                        case (1) { ret(10); }
                        case (2) { ret(42); }
                        default  { ret(0); }
                    }
                }
                eval(classify(2));
                """));
    }

    @Test
    void defaultWithNoMatchingCase() {
        run("""
                var result : 0;
                switch (eval(5)) {
                    case (1) { $result : 1; }
                    case (2) { $result : 2; }
                    default  { $result : 99; }
                }
                """);
        assertEquals("99", Interpreter.getGlobalEnvironment().get("result"));
    }
}
