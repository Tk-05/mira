package com.mira.integration.compiler.statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.integration.CompilerRunner;
import com.mira.runtime.interpreter.ImportResolver;

public class DynamicImportTest {

    @TempDir
    Path tempDir;

    private final CompilerRunner backend = new CompilerRunner();

    @BeforeEach
    void setup() throws IOException {
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

    @Test
    void wholeModuleImportReturnsCallableNamespace() {
        assertEquals("5", backend.run("var mod : importDynamic(\"plugin.mira\"); print(mod.add(2, 3));"));
    }

    @Test
    void selectiveImportReturnsOnlyRequestedSymbol() {
        assertEquals("5", backend.run("var mod : importDynamic(\"plugin.mira\", {\"add\"}); print(mod.add(2, 3));"));
    }

    @Test
    void callableFromInsideFunctionBody() {
        assertEquals("15", backend.run("""
                fn loadPlugin() {
                    var mod : importDynamic("plugin.mira");
                    return mod.add(10, 5);
                }
                print(loadPlugin());
                """));
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
    void selectivePrivateSymbolIsCatchableByMiraTryCatch() {
        assertEquals("caught", backend.run("""
                try {
                    importDynamic("plugin.mira", {"secret"});
                } catch (ImportError e) {
                    print("caught");
                }
                """));
    }
}
