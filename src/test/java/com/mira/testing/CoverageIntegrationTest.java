package com.mira.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.resolver.ModuleChecker;
import com.mira.runtime.interpreter.ImportResolver;

public class CoverageIntegrationTest {

    @AfterEach
    void teardown() {
        CoverageTracker.setEnabled(false);
        CoverageTracker.reset();
        TestRunner.reset();
        ImportResolver.reset();
        Flags.testMode = false;
    }

    @Test
    void reportsPartialCoverageOfAnAliasedImportedModule(@TempDir Path tempDir) throws IOException {
        Path mathlib = tempDir.resolve("mathlib.mira");
        Files.writeString(mathlib, """
                module mathlib;

                pub fn square(n) {
                    return n * n;
                }

                pub fn cube(n) {
                    return n * n * n;
                }
                """);

        Path testFile = tempDir.resolve("main_test.mira");
        String source = """
                module coveragemain;
                import module "mathlib.mira" as math;

                test("square works", fn() {
                    assert(math.square(4) == 16);
                });
                """;
        Files.writeString(testFile, source);

        List<Node> asts = new Parser().parseTokens(new Tokenizer().tokenize(source, false));

        Flags.inputPath.set(testFile);
        Flags.fileName = testFile.getFileName().toString();
        Flags.sourceLines = source.split("\n", -1);
        Flags.testMode = true;

        CoverageTracker.reset();
        CoverageTracker.setEnabled(true);
        boolean failed = TestRunner.runPrePassCollecting(asts, null);
        assertFalse(failed);

        List<CoverageTracker.FileEntry> files = new ArrayList<>();
        files.add(new CoverageTracker.FileEntry(CoverageTracker.moduleNameOf(asts), "main_test.mira", asts));
        for (ModuleChecker.ParsedModule module : ModuleChecker.collectAllModules(asts, testFile).values()) {
            files.add(new CoverageTracker.FileEntry(
                    CoverageTracker.moduleNameOf(module.ast()), "mathlib.mira", module.ast()));
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CoverageTracker.printReport(new PrintStream(buffer, true, StandardCharsets.UTF_8), files);
        String report = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(report.contains("mathlib.mira"), report);
        assertTrue(report.contains("1/5"), report);

        assertTrue(report.contains("Uncovered lines:"), report);
        assertTrue(report.contains("mathlib.mira: 1, 3, 7-8"), report);
    }
}
