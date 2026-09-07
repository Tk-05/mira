package com.mira.resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class ModuleCheckerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearInputPath() {
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

        ModuleChecker.ModuleCheckResult result = checkRoot(
                "module Main; import module \"broken.mira\" as bm;", mainPath);

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

        ModuleChecker.ModuleCheckResult result = checkRoot(
                "module Main; import module \"valid.mira\" as vm;", mainPath);

        assertFalse(result.hadErrors(), "A cleanly-parsing imported module must not be reported as an error");
        assertTrue(result.modules().containsKey(validModule), "The valid module should be in the parsed-modules map");
    }
}
