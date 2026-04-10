package com.mira.lib.internal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class InternalLibTest {

    static com.mira.lib.internal.Internal internal = new com.mira.lib.internal.Internal();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        internal.loadLib(environment);
    }

    @Test
    void testPrint() {
        if (environment.get("print") instanceof NativeFunction nativeFunction) {
            assertNull(nativeFunction.call(interpreter, List.of("Test")));
        }
    }

    @Test
    void testEval() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            Double eval = (Double) nativeFunction.call(interpreter, List.of("1+1"));
            assertEquals(2.0, eval);
        }
    }

    @Test
    void testExec() {
        if (environment.get("exec") instanceof NativeFunction nativeFunction) {
            try {
                nativeFunction.call(interpreter, List.of("return 2.0;"));
            } catch (ReturnSignal returnSignal) {
                assertEquals("2.0", returnSignal.getValue());
            }
        }
    }

    @Test
    void testLength() {
        if (environment.get("length") instanceof NativeFunction nativeFunction) {
            int length = (int) nativeFunction.call(interpreter, List.of("Hello World"));
            assertEquals(11, length);
        }
    }
}
