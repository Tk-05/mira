package com.mira.resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class ModuleVisibilityStaticCheckTest {

    @TempDir
    Path tempDir;

    private Path modulePath;

    @BeforeEach
    void setup() throws IOException {
        modulePath = tempDir.resolve("mymod.mira");
        Files.writeString(modulePath, """
                pub fn greet(name) { return "hello " + name; }
                fn secret() { return "hidden"; }
                pub const MAGIC : 42;
                """);
    }

    private List<MiraError> errorsFor(String source, Path sourcePath) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        MultipleStaticCheckErrors ex = assertThrows(MultipleStaticCheckErrors.class,
                () -> new StaticCheck(Set.of(), sourcePath).check(ast));
        return ex.getErrors();
    }

    private void assertClean(String source, Path sourcePath) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        assertDoesNotThrow(() -> new StaticCheck(Set.of(), sourcePath).check(ast));
    }

    private boolean hasCode(List<MiraError> errors, String code) {
        return errors.stream().anyMatch(e -> code.equals(e.getErrorCode()));
    }

    private Path mainPath() {
        return tempDir.resolve("main.mira");
    }

    @Test
    void importingPrivateSymbolProducesE318() {
        List<MiraError> errors = errorsFor(
                "import module \"mymod.mira\" {secret};",
                mainPath());
        assertTrue(hasCode(errors, "E318"), "Expected E318 for private symbol import");
    }

    @Test
    void importingPrivateSymbolWithAliasProducesE318() {
        List<MiraError> errors = errorsFor(
                "import module \"mymod.mira\" {secret} as m;",
                mainPath());
        assertTrue(hasCode(errors, "E318"), "Expected E318 for aliased private symbol import");
    }

    @Test
    void importingUnknownSymbolProducesE319() {
        List<MiraError> errors = errorsFor(
                "import module \"mymod.mira\" {doesNotExist};",
                mainPath());
        assertTrue(hasCode(errors, "E319"), "Expected E319 for unknown symbol import");
    }

    @Test
    void importingMultipleUnknownSymbolsProducesMultipleE319() {
        List<MiraError> errors = errorsFor(
                "import module \"mymod.mira\" {alpha, beta};",
                mainPath());
        long count = errors.stream().filter(e -> "E319".equals(e.getErrorCode())).count();
        assertTrue(count >= 2, "Expected at least 2 E319 errors for two unknown symbols");
    }

    @Test
    void importingPubFunctionProducesNoError() {
        assertClean("import module \"mymod.mira\" {greet};", mainPath());
    }

    @Test
    void importingPubConstProducesNoError() {
        assertClean("import module \"mymod.mira\" {MAGIC};", mainPath());
    }

    @Test
    void importingMultiplePubSymbolsProducesNoError() {
        assertClean("import module \"mymod.mira\" {greet, MAGIC};", mainPath());
    }

    @Test
    void nonExistentModuleDoesNotCrashStaticCheck() {
        assertDoesNotThrow(() -> {
            List<Node> ast = new Parser().parseTokens(
                    new Tokenizer().tokenize("module Main; import module \"ghost.mira\" {foo};", false));
            new StaticCheck(Set.of(), mainPath()).check(ast);
        });
    }
}
