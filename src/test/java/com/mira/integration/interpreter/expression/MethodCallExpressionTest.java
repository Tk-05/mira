package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractMethodCallExpressionTests;
import com.mira.runtime.values.NullValue;

public class MethodCallExpressionTest extends AbstractMethodCallExpressionTests {

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
    void methodAccessesObjectField() {
        assertEquals("Mira", backend.runAndGetValue("""
                var obj : {
                    var name : "Mira";
                    fn getName() { return name; }
                };
                obj.getName();
                """));
    }

    @Test
    void thisReferenceAccessesField() {
        assertEquals("hello", backend.runAndGetValue("""
                var obj : {
                    var value : "hello";
                    fn get() { return this.value; }
                };
                obj.get();
                """));
    }

    @Test
    void mixedFieldsAndMethods() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var obj : {
                    var x : 10;
                    fn getX() { return x; }
                };
                obj.getX();
                """)));
    }

    @Test
    void methodWithDefaultParameter() {
        assertEquals("Hello World", backend.runAndGetValue("""
                var obj : {
                    fn greet(name, greeting : "Hello") {
                        return greeting + " " + name;
                    }
                };
                obj.greet("World");
                """));
    }

    @Test
    void optionalChainingMethodCallOnNull() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("""
                var obj;
                obj?.greet();
                """));
    }

    @Test
    void chainedFieldAndMethodAccess() {
        assertEquals("inner", backend.runAndGetValue("""
                var outer : {
                    var inner : {
                        fn name() { return "inner"; }
                    };
                };
                outer.inner.name();
                """));
    }

    @Test
    void methodReturnsNull() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("""
                var obj : {
                    fn nothing() { return null; }
                };
                obj.nothing();
                """));
    }
}
