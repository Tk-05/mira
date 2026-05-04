package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class SwitchExpressionTest extends InterpreterTestBase {

    @Test
    void matchesFirstCase() {
        assertEquals(42.0, run("eval(switch(1) { case(1) -> 42 case(2) -> 99 });"));
    }

    @Test
    void matchesSecondCase() {
        assertEquals(99.0, run("eval(switch(2) { case(1) -> 42 case(2) -> 99 });"));
    }

    @Test
    void fallsToDefault() {
        assertEquals(0.0, run("eval(switch(5) { case(1) -> 1 default -> 0 });"));
    }

    @Test
    void noMatchNoDefaultReturnsNull() {
        assertNull(run("switch(5) { case(1) -> 1 }"));
    }

    @Test
    void stringMatch() {
        assertEquals("yes", run("var r : switch(\"hello\") { case(\"hello\") -> \"yes\" default -> \"no\" }; $r;"));
    }

    @Test
    void booleanMatch() {
        assertEquals("yes", run("var r : switch(true) { case(true) -> \"yes\" default -> \"no\" }; $r;"));
    }

    @Test
    void usedAsReturnValue() {
        assertEquals(10.0, run("""
                fn classify(n) {
                    return switch($n) {
                        case(1) -> 10
                        case(2) -> 20
                        default -> 0
                    };
                }
                eval(classify(1));
                """));
    }

    @Test
    void defaultWhenNoMatch() {
        assertEquals(0.0, run("""
                fn classify(n) {
                    return switch($n) {
                        case(1) -> 10
                        case(2) -> 20
                        default -> 0
                    };
                }
                eval(classify(99));
                """));
    }

    @Test
    void caseWithExpression() {
        assertEquals(6.0, run("""
                var x : 3;
                eval(switch($x) {
                    case(3) -> eval($x * 2)
                    default -> 0
                });
                """));
    }

    @Test
    void statementSwitchArrowSyntax() {
        assertEquals(1.0, run("""
                var result : 0;
                switch(2) {
                    case(1) -> $result : 1;
                    case(2) -> $result : 1;
                }
                eval($result);
                """));
    }

    @Test
    void statementSwitchArrowDefault() {
        assertEquals(99.0, run("""
                var result : 0;
                switch(5) {
                    case(1) -> $result : 1;
                    default -> $result : 99;
                }
                eval($result);
                """));
    }
}
