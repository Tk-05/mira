package com.mira.lib.internal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
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
            double eval = ((Number) nativeFunction.call(interpreter, List.of("1+1"))).doubleValue();
            assertEquals(2.0, eval);
        }
    }

    @Test
    void testEvalWithAlreadyEvaluatedNumberArgumentReturnsItUnchanged() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            double tiny = 0.016667 - 0.016667000001;
            Object result = nativeFunction.call(interpreter, List.of(tiny));
            assertEquals(tiny, ((Number) result).doubleValue());
        }
    }

    @Test
    void testEvalMultiStatement() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            double result = ((Number) nativeFunction.call(interpreter, List.of("var evalX : 10; evalX * 2;"))).doubleValue();
            assertEquals(20.0, result);
        }
    }

    @Test
    void testEvalSeesLiveEnvironmentState() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            nativeFunction.call(interpreter, List.of("var evalShared : 5;"));
            double result = ((Number) nativeFunction.call(interpreter, List.of("evalShared + 1;"))).doubleValue();
            assertEquals(6.0, result);
        }
    }

    @Test
    void testEvalSyntaxErrorThrowsCatchableSignal() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            ThrowSignal signal = assertThrows(ThrowSignal.class,
                    () -> nativeFunction.call(interpreter, List.of("var x : ;")));
            assertEquals("EvalError", signal.getExceptionType());
        }
    }

    @Test
    void testEvalRuntimeErrorThrowsCatchableSignal() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            ThrowSignal signal = assertThrows(ThrowSignal.class,
                    () -> nativeFunction.call(interpreter, List.of("undefinedEvalVar + 1;")));
            assertEquals("EvalError", signal.getExceptionType());
        }
    }

    @Test
    void testEvalNotMemoizedAcrossCalls() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            nativeFunction.call(interpreter, List.of("var evalCounter : 0;"));
            String bump = "evalCounter : evalCounter + 1; evalCounter;";
            double first = ((Number) nativeFunction.call(interpreter, List.of(bump))).doubleValue();
            double second = ((Number) nativeFunction.call(interpreter, List.of(bump))).doubleValue();
            assertNotEquals(first, second);
            assertEquals(1.0, first);
            assertEquals(2.0, second);
        }
    }

    @Test
    void testExec() {
        if (environment.get("eval") instanceof NativeFunction nativeFunction) {
            try {
                nativeFunction.call(interpreter, List.of("return 2.0;"));
            } catch (ReturnSignal returnSignal) {
                assertEquals(2.0, ((Number) returnSignal.getValue()).doubleValue());
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
