package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractBreakTests {

    protected abstract String runForOutput(String source);

    @Test
    void breakInsideWhile() {
        assertEquals("5", runForOutput("""
                var x : 0;
                while($x < 100) {
                    $x : eval($x + 1);
                    if($x == 5) { break; }
                }
                print($x);
                """));
    }

    @Test
    void breakInsideFor() {
        assertEquals("3", runForOutput("""
                var result : 0;
                for(var i : 0; $i < 10; $i : eval($i + 1)) {
                    $result : $i;
                    if($i == 3) { break; }
                }
                print($result);
                """));
    }

    @Test
    void breakInsideForeach() {
        assertEquals("2", runForOutput("""
                var last : 0;
                for(var i in {1, 2, 3, 4, 5}) {
                    if($i == 3) { break; }
                    $last : $i;
                }
                print($last);
                """));
    }

    @Test
    void breakOnlyExitsInnermostLoop() {
        assertEquals("3", runForOutput("""
                var outer : 0;
                var inner : 0;
                while($outer < 3) {
                    $outer : eval($outer + 1);
                    $inner : 0;
                    while($inner < 100) {
                        $inner : eval($inner + 1);
                        break;
                    }
                }
                print($outer);
                """));
    }
}
