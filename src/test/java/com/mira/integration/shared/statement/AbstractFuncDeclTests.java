package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractFuncDeclTests {

    protected abstract String runForOutput(String source);

    @Test
    void functionWithReturn() {
        assertEquals("42", runForOutput("fn answer() { return 42; } print(answer());"));
    }

    @Test
    void functionWithSingleParameter() {
        assertEquals("10", runForOutput("fn double(n) { return (n * 2); } print(double(5));"));
    }

    @Test
    void functionWithMultipleParameters() {
        assertEquals("7", runForOutput("fn add(a, b) { return (a + b); } print(add(3, 4));"));
    }

    @Test
    void recursiveFunctionFibonacci() {
        assertEquals("6765", runForOutput("""
                fn fibonacci(n){
                    if(n<=1){ return n; }
                    else{ return fibonacci((n-2)) + fibonacci((n-1)); }
                    return 0;
                }
                print(fibonacci(20));
                """));
    }

    @Test
    void functionCallingAnotherFunction() {
        assertEquals("20", runForOutput("""
                fn double(n) { return (n * 2); }
                fn quadruple(n) { return (double(eval(n * 2))); }
                print(quadruple(5));
                """));
    }

    @Test
    void functionWithMultipleReturnPaths() {
        assertEquals("1", runForOutput("""
                fn sign(n) {
                    if(n > 0) { return 1; }
                    if(n < 0) { return -1; }
                    return 0;
                }
                print(sign(42));
                """));
    }

    @Test
    void functionUsedInForLoop() {
        assertEquals("88", runForOutput("""
                var result : 0;
                fn fibonacci(n){
                    if(n<=1){ return n; }
                    else{ return fibonacci((n-2)) + fibonacci((n-1)); }
                    return 0;
                }
                for (var i : 0, var j : 0; i < 10 && j == 0; i : (i + 1)) {
                    result : result + fibonacci(i);
                }
                print(result);
                """));
    }
}
