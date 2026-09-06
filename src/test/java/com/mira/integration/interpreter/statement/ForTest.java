package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractForTests;

public class ForTest extends AbstractForTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void forWithoutInitializer() {
        assertNull(backend.runAndGetValue("""
                var i : 0;
                for (; i < 3; i : (i + 1)) {}
                """));
    }

    @Test
    void forWithMultipleInitializers() {
        assertNull(backend.runAndGetValue("""
                for (var i : 0, var j : 0; i < 10 && j == 0; i : (i + 1)) {
                    print(i);
                }
                """));
    }

    @Test
    void emptyFor() {
        assertNull(backend.runAndGetValue("""
                var broken : false;
                for (;;) {
                    broken : true;
                    break;
                }
                """));
    }
}
