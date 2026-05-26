package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractForTests {

    protected abstract String runForOutput(String source);

    @Test
    void simpleCounterLoop() {
        assertEquals("5", runForOutput("""
                var count : 0;
                for (var i : 0; $i < 5; $i : eval($i + 1)) {
                    $count : eval($count + 1);
                }
                print($count);
                """));
    }

    @Test
    void forWithFunctionInBody() {
        assertEquals("88", runForOutput("""
                var result : 0;
                fn fibonacci(n){
                    if($n<=1){ return $n; }
                    else{ return fibonacci(eval($n-2)) + fibonacci(eval($n-1)); }
                    return 0;
                }
                for (var i : 0, var j : 0; $i < 10 && $j == 0; $i : eval($i + 1)) {
                    $result : $result + fibonacci($i);
                }
                print($result);
                """));
    }

    @Test
    void forWithBreak() {
        assertEquals("5", runForOutput("""
                var count : 0;
                for (var i : 0; $i < 100; $i : eval($i + 1)) {
                    if($i == 5) { break; }
                    $count : eval($count + 1);
                }
                print($count);
                """));
    }

    @Test
    void forWithRangeBecomesForEach() {
        assertEquals("4", runForOutput("""
                var last : 0;
                for(var i in <0..5>) {
                    $last : $i;
                }
                print($last);
                """));
    }
}
