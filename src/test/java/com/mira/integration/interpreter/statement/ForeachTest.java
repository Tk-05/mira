package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractForeachTests;

public class ForeachTest extends AbstractForeachTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void foreachOnTuple() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var list : [1,2,3];
                var lastResult;
                for(var element in list) {
                    lastResult : element;
                }
                lastResult;
                """)));
    }

    @Test
    void foreachWithBreak() {
        try {
            backend.runAndGetValue("""
                    var list : {1,2,3};
                    for(var element in list) {
                        if(element == 1) { break; }
                    }
                    """);
        } catch (BreakSignal ignored) {
        }
    }

    @Test
    void foreachWithReturn() {
        try {
            backend.runAndGetValue("""
                    var list : {1,2,3};
                    for(var element in list) {
                        if(element == 1) { return; }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void nestedForeach() {
        try {
            backend.runAndGetValue("""
                    var list1 : {1,2,3};
                    var list2 : {4,5,6};
                    for(var e1 in list1) {
                        for(var e2 in list2) {
                            if(e1 == 3 && e2 == 6) { break; }
                        }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }

    @Test
    void foreachOnNestedList() {
        try {
            backend.runAndGetValue("""
                    var list1 : {{1,2,3}};
                    for(var element in list1[0]) {
                        if(element == 3) { break; }
                    }
                    """);
        } catch (ReturnSignal ignored) {
        }
    }
}
