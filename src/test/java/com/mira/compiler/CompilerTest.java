package com.mira.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class CompilerTest extends CompilerTestBase {

    @Test
    void helloWorld() {
        assertEquals("Hello, World!", run("""
                module hello;
                print("Hello, World!");
                """));
    }

    @Test
    void foreachSum() {
        assertEquals("45", run("""
                module counter;
                var sum : 0;
                foreach(var i in <0..10>) {
                    $sum : $sum + $i;
                }
                print($sum);
                """));
    }

    @Test
    void functionCall() {
        assertEquals("49", run("""
                module funcs;
                fn square(x) { return $x * $x; }
                print(square(7));
                """));
    }
}
