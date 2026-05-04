package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;
import com.mira.runtime.interpreter.NullValue;

public class OptionalChainingTest extends InterpreterTestBase {

    @Test
    void accessesFieldWhenObjectIsNotNull() {
        run("""
                var obj : { var name : "Alice"; };
                var result : $obj?.name;
                """);
        assertEquals("Alice", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void returnsNullWhenObjectIsNull() {
        run("""
                var obj : null;
                var result : $obj?.name;
                """);
        assertEquals(NullValue.INSTANCE, interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void returnsNullWhenObjectIsUninitialized() {
        run("""
                var obj;
                var result : $obj?.name;
                """);
        assertEquals(NullValue.INSTANCE, interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void combinesWithNullCoalescing() {
        run("""
                var obj : null;
                var result : $obj?.name ?? "unknown";
                """);
        assertEquals("unknown", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void combinesWithNullCoalescingNonNull() {
        run("""
                var obj : { var name : "Bob"; };
                var result : $obj?.name ?? "unknown";
                """);
        assertEquals("Bob", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void chainsMultipleOptionalAccesses() {
        run("""
                var inner : { var value : 42; };
                var outer : { var inner : $inner; };
                var result : $outer?.inner?.value;
                """);
        assertEquals(42.0, normNum(interpreter.getGlobalEnvironment().get("result")));
    }

    @Test
    void chainShortCircuitsToNullOnFirstNull() {
        run("""
                var outer : null;
                var result : $outer?.inner?.value;
                """);
        assertEquals(NullValue.INSTANCE, interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void normalDotAccessStillThrowsOnNull() {
        try {
            run("""
                    var obj : null;
                    $obj.name;
                    """);
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    void optionalAndNormalChainMixed() {
        run("""
                var inner : { var city : "Berlin"; };
                var outer : { var address : $inner; };
                var result : $outer?.address.city;
                """);
        assertEquals("Berlin", interpreter.getGlobalEnvironment().get("result"));
    }
}
