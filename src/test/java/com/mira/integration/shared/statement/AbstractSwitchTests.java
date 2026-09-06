package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractSwitchTests {

    protected abstract String runForOutput(String source);

    @Test
    void matchesFirstCase() {
        assertEquals("one", runForOutput("""
                var x : 1;
                switch(x) {
                    case(1) { print("one"); }
                    case(2) { print("two"); }
                }
                """));
    }

    @Test
    void matchesSecondCase() {
        assertEquals("two", runForOutput("""
                var x : 2;
                switch(x) {
                    case(1) { print("one"); }
                    case(2) { print("two"); }
                }
                """));
    }

    @Test
    void defaultExecutedWhenNoMatch() {
        assertEquals("other", runForOutput("""
                var x : 5;
                switch(x) {
                    case(1) { print("one"); }
                    default { print("other"); }
                }
                """));
    }

    @Test
    void matchesStringCase() {
        assertEquals("hello", runForOutput("""
                var s : "hi";
                switch(s) {
                    case("hi") { print("hello"); }
                    default { print("other"); }
                }
                """));
    }

    @Test
    void switchInsideFunction() {
        assertEquals("positive", runForOutput("""
                fn classify(n) {
                    if(n > 0) { return "positive"; }
                    if(n < 0) { return "negative"; }
                    return "zero";
                }
                print(classify(5));
                """));
    }
}
