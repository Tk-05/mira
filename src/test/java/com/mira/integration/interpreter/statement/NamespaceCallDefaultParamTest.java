package com.mira.integration.interpreter.statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.integration.InterpreterRunner;
import com.mira.runtime.interpreter.ImportResolver;

public class NamespaceCallDefaultParamTest {

    @TempDir
    Path tempDir;

    private InterpreterRunner backend;

    @BeforeEach
    void setup() throws IOException {
        backend = new InterpreterRunner();
        ImportResolver.reset();

        Files.writeString(tempDir.resolve("mymod.mira"), """
                module MyMod;
                pub fn greet(name, greeting: "Hello") {
                    return greeting + " " + name;
                }
                """);

        Flags.inputPath.set(tempDir.resolve("main.mira"));
    }

    @AfterEach
    void teardown() {
        Flags.inputPath.remove();
        ImportResolver.reset();
    }

    private Object run(String src) {
        backend.reset();
        return backend.runAndGetValue(src);
    }

    @Test
    void namespaceCallUsesDefaultWhenArgOmitted() {
        assertEquals("Hello World", run("import module \"mymod.mira\" as lib; lib.greet(\"World\");"));
    }

    @Test
    void namespaceCallAcceptsExplicitOptionalArg() {
        assertEquals("Hi World", run("import module \"mymod.mira\" as lib; lib.greet(\"World\", \"Hi\");"));
    }

    @Test
    void namespaceCallTooManyArgsStillThrows() {
        assertThrows(RuntimeException.class,
                () -> run("import module \"mymod.mira\" as lib; lib.greet(\"a\", \"b\", \"c\");"));
    }

    @Test
    void namespaceCallTooFewArgsStillThrows() {
        assertThrows(RuntimeException.class, () -> run("import module \"mymod.mira\" as lib; lib.greet();"));
    }
}
