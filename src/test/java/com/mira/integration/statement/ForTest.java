package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class ForTest extends InterpreterTestBase {

    @Test
    void simpleCounterLoop() {
        run("""
                var count : 0;
                for (var i : 0; $i < 5; $i : eval($i + 1)) {
                    $count : eval($count + 1);
                }
                """);
        assertEquals(5.0, normNum(interpreter.getGlobalEnvironment().get("count")));
    }

    @Test
    void forWithoutInitializer() {
        assertNull(run("""
                var i : 0;
                for (; $i < 3; $i : eval($i + 1)) {}
                """));
    }

    @Test
    void forWithMultipleInitializers() {
        assertNull(run("""
                for (var i : 0, var j : 0; $i < 10 && $j == 0; $i : eval($i + 1)) {
                    print($i);
                }
                """));
    }

    @Test
    void forWithFunctionInBody() {
        assertEquals(88.0, run("""
                var result : 0;

                fn fibonacci(n){
                    if($n<=1){ return $n; }
                    else{ return fibonacci(eval($n-2)) + fibonacci(eval($n-1)); }
                    return 0;
                }

                for (var i : 0, var j : 0; $i < 10 && $j == 0; $i : eval($i + 1)) {
                    $result : $result + fibonacci($i);
                }

                eval($result);
                """));
    }

    @Test
    void forWithBreak() {
        run("""
                var count : 0;
                for (var i : 0; $i < 100; $i : eval($i + 1)) {
                    if($i == 5) { break; }
                    $count : eval($count + 1);
                }
                """);
        assertEquals(5.0, normNum(interpreter.getGlobalEnvironment().get("count")));
    }

    @Test
    void emptyFor() {
        assertNull(run("""
                var broken : false;
                for (;;) {
                    $broken : true;
                    break;
                }
                """));
    }

    @Test
    void forWithRangeBecomesForEach() {
        run("""
                var last : 0;
                for(var i in <0..5>) {
                    $last : $i;
                }
                """);
        assertEquals(4.0, normNum(interpreter.getGlobalEnvironment().get("last")));
    }
}
