package com.mira.resolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class ModuleCheckerTest {

    @TempDir
    Path tempDir;

    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureStderr() {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void clearInputPath() {
        System.setErr(originalErr);
        Flags.inputPath.remove();
    }

    private ModuleChecker.ModuleCheckResult checkRoot(String rootSource, Path rootPath) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(rootSource, false));
        Flags.inputPath.set(rootPath);
        return ModuleChecker.check(ast, Set.of());
    }

    @Test
    void syntaxErrorInImportedModuleIsReportedNotSilentlyDropped() throws IOException {
        Path brokenModule = tempDir.resolve("broken.mira");
        Files.writeString(brokenModule, """
                module Broken;
                pub fn greet(name) {
                    println("hi " + name
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = checkRoot("module Main; import module \"broken.mira\" as bm;",
                mainPath);

        assertTrue(result.hadErrors(), "A syntax error in an imported module must be reported as a check failure");
        assertFalse(result.modules().containsKey(brokenModule),
                "A module that failed to parse should not appear as a successfully parsed module");
    }

    @Test
    void validImportedModuleProducesNoErrors() throws IOException {
        Path validModule = tempDir.resolve("valid.mira");
        Files.writeString(validModule, """
                module Valid;
                pub fn greet(name) { return "hi " + name; }
                """);
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = checkRoot("module Main; import module \"valid.mira\" as vm;",
                mainPath);

        assertFalse(result.hadErrors(), "A cleanly-parsing imported module must not be reported as an error");
        assertTrue(result.modules().containsKey(validModule), "The valid module should be in the parsed-modules map");
    }

    @Test
    void indirectExternalCallThroughDiamondImportSuppressesUnusedHint() throws IOException {
        Files.writeString(tempDir.resolve("d.mira"), """
                module D;
                pub fn helper() { return 1; }
                """);
        Files.writeString(tempDir.resolve("b.mira"), """
                module B;
                import module "d.mira" as d;
                pub fn useD() { return d.helper(); }
                """);
        Files.writeString(tempDir.resolve("c.mira"), """
                module C;
                pub fn ok() { return 1; }
                """);
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = checkRoot("""
                module Main;
                import module "b.mira" as b;
                import module "c.mira" as c;
                """, mainPath);

        assertFalse(result.hadErrors());
        assertFalse(capturedErr.toString().contains("'helper' is declared but never used"),
                "helper() is called externally via b.mira -> d.mira, should not be flagged unused: " + capturedErr);
    }

    @Test
    void unreachedPubFunctionStillGetsUnusedHintWithoutExternalCall() throws IOException {
        Files.writeString(tempDir.resolve("d.mira"), """
                module D;
                pub fn helper() { return 1; }
                """);
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = checkRoot("module Main; import module \"d.mira\" as d;", mainPath);

        assertFalse(result.hadErrors());
        assertTrue(capturedErr.toString().contains("'helper' is declared but never used"),
                "helper() is never called by anyone, should still be flagged unused: " + capturedErr);
    }

    @Test
    void importCycleDoesNotDeadlock() throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                module A;
                import module "b.mira" as b;
                pub fn fromA() { return 1; }
                """);
        Files.writeString(tempDir.resolve("b.mira"), """
                module B;
                import module "a.mira" as a;
                pub fn fromB() { return a.fromA(); }
                """);
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = Assertions.assertTimeout(Duration.ofSeconds(10),
                () -> checkRoot("module Main; import module \"a.mira\" as a;", mainPath));

        assertFalse(result.hadErrors());
        assertEquals(2, result.modules().size(), "both sides of the cycle should be discovered exactly once");
    }

    @Test
    void fanOutOfManyModulesAllGetCheckedWithNoCrossTalk() throws IOException {
        int moduleCount = 50;
        StringBuilder rootSource = new StringBuilder("module Main;\n");
        for (int i = 0; i < moduleCount; i++) {
            String name = "m" + i;
            Files.writeString(tempDir.resolve(name + ".mira"),
                    "module " + name + ";\npub fn value" + i + "() { return " + i + "; }\n");
            rootSource.append("import module \"").append(name).append(".mira\" as ").append(name).append(";\n");
        }
        Path mainPath = tempDir.resolve("main.mira");

        ModuleChecker.ModuleCheckResult result = checkRoot(rootSource.toString(), mainPath);

        assertFalse(result.hadErrors());
        assertEquals(moduleCount, result.modules().size());
        assertEquals(moduleCount, result.checkTimingsMs().size());
    }
}
