package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;
import com.mira.runtime.values.NullValue;

public class MethodCallExpressionTest extends InterpreterTestBase {

    @Test
    void simpleMethodCall() {
        assertEquals("hello", run("""
                var obj : {
                    fn greet() {
                        return "hello";
                    }
                };
                $obj.greet();
                """));
    }

    @Test
    void methodWithParameters() {
        assertEquals(7.0, run("""
                var obj : {
                    fn add(a, b) {
                        return eval($a + $b);
                    }
                };
                $obj.add(3, 4);
                """));
    }

    @Test
    void methodAccessesObjectField() {
        assertEquals("Mira", run("""
                var obj : {
                    var name : "Mira";
                    fn getName() {
                        return $name;
                    }
                };
                $obj.getName();
                """));
    }

    @Test
    void methodMutatesField() {
        assertEquals(2.0, run("""
                var counter : {
                    var count : 0;
                    fn increment() {
                        $count += 1;
                    }
                    fn get() {
                        return $count;
                    }
                };
                $counter.increment();
                $counter.increment();
                $counter.get();
                """));
    }

    @Test
    void thisReferenceAccessesField() {
        assertEquals("hello", run("""
                var obj : {
                    var value : "hello";
                    fn get() {
                        return $this.value;
                    }
                };
                $obj.get();
                """));
    }

    @Test
    void methodOnlyObject() {
        assertEquals("ok", run("""
                var obj : {
                    fn test() {
                        return "ok";
                    }
                };
                $obj.test();
                """));
    }

    @Test
    void mixedFieldsAndMethods() {
        assertEquals(10.0, run("""
                var obj : {
                    var x : 10;
                    var y : 20;
                    fn getX() {
                        return $x;
                    }
                };
                $obj.getX();
                """));
    }

    @Test
    void methodWithDefaultParameter() {
        assertEquals("Hello World", run("""
                var obj : {
                    fn greet(name, greeting : "Hello") {
                        return $greeting " " $name;
                    }
                };
                $obj.greet("World");
                """));
    }

    @Test
    void optionalChainingMethodCallOnNull() {
        assertInstanceOf(NullValue.class, run("""
                var obj;
                $obj?.greet();
                """));
    }

    @Test
    void chainedFieldAndMethodAccess() {
        assertEquals("inner", run("""
                var outer : {
                    var inner : {
                        fn name() {
                            return "inner";
                        }
                    };
                    fn getInner() {
                        return $inner;
                    }
                };
                $outer.inner.name();
                """));
    }

    @Test
    void methodReturnsNull() {
        assertInstanceOf(NullValue.class, run("""
                var obj : {
                    fn nothing() {
                        return null;
                    }
                };
                $obj.nothing();
                """));
    }
}
