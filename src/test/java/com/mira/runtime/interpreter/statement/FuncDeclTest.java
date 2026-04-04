package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class FuncDeclTest extends InterpreterTestBase {

    @Test
    void simpleFunctionDeclaration() {
        assertNull(run("fn test() {}"));
    }

    @Test
    void voidFunctionCallReturnsNull() {
        assertNull(run("fn test() {} test();"));
    }

    @Test
    void functionWithReturn() {
        assertEquals(42.0, run("fn answer() { ret(42); } eval(answer());"));
    }

    @Test
    void functionWithSingleParameter() {
        assertEquals(10.0, run("fn double(n) { ret(eval($n * 2)); } eval(double(5));"));
    }

    @Test
    void functionWithMultipleParameters() {
        assertEquals(7.0, run("fn add(a, b) { ret(eval($a + $b)); } eval(add(3, 4));"));
    }

    @Test
    void functionWithStringReturn() {
        try {
            run("""
                    fn greet(name) {
                        ret("Hello " $name);
                    }
                    var greeting : "ret(greet(\"World\"));";
                    exec($greeting);
                    """);
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }

    @Test
    void recursiveFunctionFibonacci() {
        assertEquals(6765.0, run("""
                fn fibonacci(n){
                    if($n<=1){
                        ret($n);
                    }else{
                        ret(fibonacci(eval($n-2)) + fibonacci(eval($n-1)));
                    }
                    ret(0);
                }
                eval(fibonacci(20));
                """));
    }

    @Test
    void functionCallingAnotherFunction() {
        assertEquals(20.0, run("""
                fn double(n) { ret(eval($n * 2)); }
                fn quadruple(n) { ret(eval(double(eval($n * 2)))); }
                eval(quadruple(5));
                """));
    }

    @Test
    void functionWithMultipleReturnPaths() {
        assertEquals(1.0, run("""
                fn sign(n) {
                    if($n > 0) { ret(1); }
                    if($n < 0) { ret(-1); }
                    ret(0);
                }
                eval(sign(42));
                """));
    }

    @Test
    void functionUsedInForLoop() {
        assertEquals(88.0, run("""
                var result : 0;

                fn fibonacci(n){
                    if($n<=1){ ret($n); }
                    else{ ret(fibonacci(eval($n-2)) + fibonacci(eval($n-1))); }
                    ret(0);
                }

                for (var i : 0, var j : 0; $i < 10 && $j == 0; $i : eval($i + 1)) {
                    $result : $result + fibonacci($i);
                }

                eval($result);
                """));
    }
}
