package com.mira.integration.interpreter.statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.integration.InterpreterRunner;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.runtime.interpreter.ImportResolver;

public class DynamicImportTest {

    @TempDir
    Path tempDir;

    private InterpreterRunner backend;

    @BeforeEach
    void setup() throws IOException {
        backend = new InterpreterRunner();
        ImportResolver.reset();

        Files.writeString(tempDir.resolve("plugin.mira"), """
                module Plugin;
                pub fn add(a, b) { return a + b; }
                fn secret() { return "hidden"; }
                pub const MAGIC : 99;
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
    void wholeModuleImportReturnsCallableNamespace() {
        assertEquals(5.0, InterpreterRunner.normNum(run("var mod : importDynamic(\"plugin.mira\"); mod.add(2, 3);")));
    }

    @Test
    void selectiveImportReturnsOnlyRequestedSymbol() {
        assertEquals(5.0,
                InterpreterRunner.normNum(run("var mod : importDynamic(\"plugin.mira\", {\"add\"}); mod.add(2, 3);")));
    }

    @Test
    void selectivePrivateSymbolIsCatchableByMiraTryCatch() {
        assertEquals("caught", backend.run("""
                try {
                    importDynamic("plugin.mira", {"secret"});
                } catch (ImportError e) {
                    print("caught");
                }
                """));
    }

    @Test
    void callableFromInsideFunctionBody() {
        assertEquals(15.0, InterpreterRunner.normNum(run("""
                fn loadPlugin() {
                    var mod : importDynamic("plugin.mira");
                    return mod.add(10, 5);
                }
                loadPlugin();
                """)));
    }

    @Test
    void nonexistentPathIsCatchableByMiraTryCatch() {
        assertEquals("caught", backend.run("""
                try {
                    importDynamic("does-not-exist.mira");
                } catch (ImportError e) {
                    print("caught");
                }
                """));
    }

    @Test
    void thrownSignalCarriesImportErrorType() {
        try {
            run("importDynamic(\"does-not-exist.mira\");");
        } catch (ThrowSignal signal) {
            assertEquals("ImportError", signal.getExceptionType());
            return;
        }
        throw new AssertionError("Expected a ThrowSignal to be thrown");
    }
}
