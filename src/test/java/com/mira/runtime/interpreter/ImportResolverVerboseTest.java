package com.mira.runtime.interpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.integration.InterpreterRunner;

public class ImportResolverVerboseTest {

    @TempDir
    Path tempDir;

    private InterpreterRunner backend;

    @BeforeEach
    void setup() throws IOException {
        backend = new InterpreterRunner();
        ImportResolver.reset();

        Files.writeString(tempDir.resolve("mymod.mira"), """
                module MyMod;
                pub fn greet() { return "hi"; }
                """);

        Flags.inputPath.set(tempDir.resolve("main.mira"));
        Flags.verbose = true;
    }

    @AfterEach
    void teardown() {
        Flags.inputPath.remove();
        Flags.verbose = false;
        ImportResolver.reset();
    }

    @Test
    void firstLoadParsesSecondLoadHitsCache() {
        String source = "import module \"mymod.mira\"; greet();";

        String firstOutput = backend.run(source);
        assertTrue(firstOutput.contains("parsing:"));

        ImportResolver.reset();

        String secondOutput = backend.run(source);
        assertTrue(secondOutput.contains("cache hit:"));
    }
}
