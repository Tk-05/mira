package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.values.NullValue;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractDestructuringTests;

public class DestructuringTest extends AbstractDestructuringTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void destructuresSingleElement() {
        backend.runAndGetValue("""
                var (only,) : {99};
                """);
        assertEquals(99.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("only")));
    }

    @Test
    void moreNamesThanElementsBindsNullToExtras() {
        backend.runAndGetValue("""
                var (a, b, c) : {1, 2};
                """);
        assertEquals(1.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("a")));
        assertEquals(2.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("b")));
        assertEquals(NullValue.INSTANCE, backend.getInterpreter().getGlobalEnvironment().get("c"));
    }

    @Test
    void destructuresStrings() {
        backend.runAndGetValue("""
                var (first, second) : {"hello", "world"};
                """);
        assertEquals("hello", backend.getInterpreter().getGlobalEnvironment().get("first"));
        assertEquals("world", backend.getInterpreter().getGlobalEnvironment().get("second"));
    }

    @Test
    void destructuresInLocalScope() {
        backend.runAndGetValue("""
                var result : 0;
                fn myFunc() {
                    var (a, b) : {3, 7};
                    $result : eval($a + $b);
                }
                myFunc();
                """);
        assertEquals(10.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("result")));
    }

    @Test
    void throwsOnNonDestructurableValue() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("""
                var (a, b) : "not a collection";
                """));
    }
}
