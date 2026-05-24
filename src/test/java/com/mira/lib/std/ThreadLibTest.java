package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.MutexValue;

public class ThreadLibTest {

    static ThreadLib threadLib = new ThreadLib();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        threadLib.loadLib(environment);
    }

    @Test
    void newMutexReturnsMutexValue() {
        NativeFunction fn = (NativeFunction) environment.get("newMutex");
        assertInstanceOf(MutexValue.class, fn.call(interpreter, List.of()));
    }

    @Test
    void newMutexReturnsFreshInstanceEachTime() {
        NativeFunction fn = (NativeFunction) environment.get("newMutex");
        Object a = fn.call(interpreter, List.of());
        Object b = fn.call(interpreter, List.of());
        assertNotSame(a, b);
    }
}
