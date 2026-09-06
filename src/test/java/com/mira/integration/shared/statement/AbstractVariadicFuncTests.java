package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractVariadicFuncTests {

    protected abstract String runForOutput(String source);

    @Test
    void variadicOnlyParamNoArgs() {
        assertEquals("0", runForOutput("""
                fn countArgs(...args) {
                    var n : 0;
                    for(var x in args) { n : (n + 1); }
                    return n;
                }
                print(countArgs());
                """));
    }

    @Test
    void variadicOnlyParamWithArgs() {
        assertEquals("3", runForOutput("""
                fn countArgs(...args) {
                    var n : 0;
                    for(var x in args) { n : (n + 1); }
                    return n;
                }
                print(countArgs(1, 2, 3));
                """));
    }

    @Test
    void variadicSumAllArgs() {
        assertEquals("6", runForOutput("""
                fn sum(...args) {
                    var total : 0;
                    for(var n in args) { total : (total + n); }
                    return total;
                }
                print(sum(1, 2, 3));
                """));
    }

    @Test
    void fixedPlusVariadic() {
        assertEquals("3", runForOutput("""
                fn countRest(first, ...rest) {
                    var n : 0;
                    for(var x in rest) { n : (n + 1); }
                    return n;
                }
                print(countRest(1, 2, 3, 4));
                """));
    }

    @Test
    void lambdaVariadic() {
        assertEquals("3", runForOutput("""
                var countArgs : fn(...args) {
                    var n : 0;
                    for(var x in args) { n : (n + 1); }
                    return n;
                };
                print(countArgs(1, 2, 3));
                """));
    }
}
