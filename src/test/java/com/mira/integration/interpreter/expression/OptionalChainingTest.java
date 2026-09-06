package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractOptionalChainingTests;
import com.mira.runtime.values.NullValue;

public class OptionalChainingTest extends AbstractOptionalChainingTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void returnsNullWhenObjectIsUninitialized() {
        backend.runAndGetValue("""
                var obj;
                var result : obj?.name;
                """);
        assertEquals(NullValue.INSTANCE, backend.getInterpreter().getGlobalEnvironment().get("result"));
    }

    @Test
    void combinesWithNullCoalescingNonNull() {
        backend.runAndGetValue("""
                var obj : { var name : "Bob"; };
                var result : obj?.name ?? "unknown";
                """);
        assertEquals("Bob", backend.getInterpreter().getGlobalEnvironment().get("result"));
    }

    @Test
    void chainsMultipleOptionalAccesses() {
        backend.runAndGetValue("""
                var inner : { var value : 42; };
                var outer : { var inner : inner; };
                var result : outer?.inner?.value;
                """);
        assertEquals(42.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("result")));
    }

    @Test
    void chainShortCircuitsToNullOnFirstNull() {
        backend.runAndGetValue("""
                var outer : null;
                var result : outer?.inner?.value;
                """);
        assertEquals(NullValue.INSTANCE, backend.getInterpreter().getGlobalEnvironment().get("result"));
    }

    @Test
    void optionalAndNormalChainMixed() {
        backend.runAndGetValue("""
                var inner : { var city : "Berlin"; };
                var outer : { var address : inner; };
                var result : outer?.address.city;
                """);
        assertEquals("Berlin", backend.getInterpreter().getGlobalEnvironment().get("result"));
    }
}
