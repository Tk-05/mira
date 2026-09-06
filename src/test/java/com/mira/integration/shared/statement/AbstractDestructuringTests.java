package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractDestructuringTests {

    protected abstract String runForOutput(String source);

    @Test
    void destructuresListIntoVariables() {
        assertEquals("1", runForOutput("var (a, b, c) : {1, 2, 3}; print(a);"));
    }

    @Test
    void destructuresArray() {
        assertEquals("3", runForOutput("var (x, y, z) : [10, 20, 3]; print(z);"));
    }

    @Test
    void fewerNamesThanElementsIgnoresRest() {
        assertEquals("1", runForOutput("var (a, b) : {1, 2, 3}; print(a);"));
    }

    @Test
    void destructuresComputedExpression() {
        assertEquals("5", runForOutput("""
                var list : [5, 10];
                var (first, second) : list;
                print(first);
                """));
    }
}
