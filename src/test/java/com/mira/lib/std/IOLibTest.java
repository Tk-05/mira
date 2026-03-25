package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class IOLibTest {
    static com.mira.runtime.lib.std.IO io = new com.mira.runtime.lib.std.IO();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        io.loadLib(environment);
    }

    @Test
    void testReadFile() {
        if (environment.get("readFile") instanceof NativeFunction activeFunction) {
            String readFile = (String) activeFunction.call(interpreter, List.of("src/main/resources/demo/Debug.mira"));
            assertNotNull(readFile);
        }
    }
}
