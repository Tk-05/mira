package com.mira.integration.interpreter.statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.error.runtime.RuntimeError.ModuleSymbolNotFoundError;
import com.mira.error.runtime.RuntimeError.PrivateSymbolImportError;
import com.mira.integration.InterpreterRunner;
import com.mira.runtime.interpreter.ImportResolver;

public class ModuleVisibilityTest {

    @TempDir
    Path tempDir;

    private InterpreterRunner backend;
    private Path modulePath;

    @BeforeEach
    void setup() throws IOException {
        backend = new InterpreterRunner();
        ImportResolver.reset();

        modulePath = tempDir.resolve("mymod.mira");
        Files.writeString(modulePath, """
                module MyMod;
                pub fn greet(name) { return "hello " + $name; }
                fn secret() { return "hidden"; }
                pub const MAGIC : 42;
                pub enum Color { Red, Green, Blue }
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
    void fullImportExportsPubFunction() {
        assertEquals("hello world",
                run("import module \"mymod.mira\"; greet(\"world\");"));
    }

    @Test
    void fullImportDoesNotExportPrivateFunction() {
        assertThrows(RuntimeException.class,
                () -> run("import module \"mymod.mira\"; secret();"));
    }

    @Test
    void fullImportExportsPubConst() {
        assertEquals(42.0,
                InterpreterRunner.normNum(run("import module \"mymod.mira\"; $MAGIC;")));
    }

    @Test
    void fullImportWithAliasExportsPubFunction() {
        assertEquals("hello world",
                run("import module \"mymod.mira\" as mod; mod.greet(\"world\");"));
    }

    @Test
    void fullImportWithAliasDoesNotExposePrivateFunction() {
        assertThrows(RuntimeException.class,
                () -> run("import module \"mymod.mira\" as mod; mod.secret();"));
    }

    @Test
    void selectiveImportPubFunctionWorks() {
        assertEquals("hello world",
                run("import module \"mymod.mira\" {greet}; greet(\"world\");"));
    }

    @Test
    void selectiveImportPubConstWorks() {
        assertEquals(42.0,
                InterpreterRunner.normNum(run("import module \"mymod.mira\" {MAGIC}; $MAGIC;")));
    }

    @Test
    void selectiveImportDoesNotLoadOtherSymbols() {
        assertThrows(RuntimeException.class,
                () -> run("import module \"mymod.mira\" {greet}; $MAGIC;"));
    }

    @Test
    void selectiveImportPrivateSymbolThrowsPrivateError() {
        assertThrows(PrivateSymbolImportError.class,
                () -> run("import module \"mymod.mira\" {secret};"));
    }

    @Test
    void selectiveImportUnknownSymbolThrowsNotFoundError() {
        assertThrows(ModuleSymbolNotFoundError.class,
                () -> run("import module \"mymod.mira\" {doesNotExist};"));
    }

    @Test
    void selectiveImportWithAlias() {
        assertEquals("hello world",
                run("import module \"mymod.mira\" {greet} as m; m.greet(\"world\");"));
    }

    @Test
    void selectiveImportWithAliasDoesNotPollutGlobalScope() {
        assertThrows(RuntimeException.class,
                () -> run("import module \"mymod.mira\" {greet} as m; greet(\"world\");"));
    }

    @Test
    void selectiveImportMultipleSymbols() {
        assertDoesNotThrow(
                () -> run("import module \"mymod.mira\" {greet, MAGIC}; greet(\"x\"); $MAGIC;"));
    }

    // --- Stdlib brace syntax ---
    @Test
    void stdlibBraceSyntaxMakesFunctionAvailable() {
        assertEquals("hello",
                run("import string {trim}; trim(\" hello \");"));
    }

    @Test
    void stdlibBraceSyntaxDoesNotLoadOtherFunctions() {
        assertThrows(RuntimeException.class,
                () -> run("import string {trim}; split(\"a,b\", \",\");"));
    }

    @Test
    void stdlibBraceSyntaxWithAlias() {
        assertEquals("hello",
                run("import string {trim} as str; str.trim(\" hello \");"));
    }
}
