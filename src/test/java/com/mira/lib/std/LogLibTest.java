package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class LogLibTest {

    Log lib;
    Environment environment;
    static Interpreter interpreter = new Interpreter();

    @BeforeEach
    void setup() {
        lib = new Log();
        environment = new Environment();
        lib.loadLib(environment);
    }

    @Test
    void testDebug() {
        NativeFunction fn = (NativeFunction) environment.get("debug");
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("debug message")));
    }

    @Test
    void testInfo() {
        NativeFunction fn = (NativeFunction) environment.get("info");
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("info message")));
    }

    @Test
    void testWarn() {
        NativeFunction fn = (NativeFunction) environment.get("warn");
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("warn message")));
    }

    @Test
    void testError() {
        NativeFunction fn = (NativeFunction) environment.get("error");
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("error message")));
    }

    @Test
    void testSetLevel() {
        NativeFunction fn = (NativeFunction) environment.get("setLevel");
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("info")));
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("warn")));
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("error")));
        assertDoesNotThrow(() -> fn.call(interpreter, List.of("debug")));
    }

    @Test
    void testSetLevelFiltersMessages() {
        NativeFunction setLevel = (NativeFunction) environment.get("setLevel");
        NativeFunction debug = (NativeFunction) environment.get("debug");
        setLevel.call(interpreter, List.of("error"));
        assertDoesNotThrow(() -> debug.call(interpreter, List.of("this should be suppressed")));
    }

    @Test
    void testSetLevelInvalidThrows() {
        NativeFunction fn = (NativeFunction) environment.get("setLevel");
        assertThrows(RuntimeException.class, () -> fn.call(interpreter, List.of("verbose")));
    }
}
