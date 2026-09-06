package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractDefaultParamTests {

    protected abstract String runForOutput(String source);

    @Test
    void defaultParamInLambda() {
        assertEquals("World", runForOutput("""
                var greet : fn(name : "World") { return name; };
                print(greet());
                """));
    }

    @Test
    void defaultParamInLambdaOverridden() {
        assertEquals("Alice", runForOutput("""
                var greet : fn(name : "World") { return name; };
                print(greet("Alice"));
                """));
    }

    @Test
    void defaultIsNumber() {
        assertEquals("42", runForOutput("""
                fn answer(n : 42) { return n; }
                print(answer());
                """));
    }

    @Test
    void explicitArgOverridesDefault() {
        assertEquals("7", runForOutput("""
                fn answer(n : 42) { return n; }
                print(answer(7));
                """));
    }
}
