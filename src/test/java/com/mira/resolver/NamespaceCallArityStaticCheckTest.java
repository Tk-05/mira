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

public class NamespaceCallArityStaticCheckTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        Files.writeString(tempDir.resolve("mymod.mira"), """
                pub fn greet(name, greeting: "Hello") { return $greeting " " $name; }
                """);
    }

    private Path mainPath() {
        return tempDir.resolve("main.mira");
    }

    private List<MiraError> errorsFor(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        MultipleStaticCheckErrors ex = assertThrows(MultipleStaticCheckErrors.class,
                () -> new StaticCheck(Set.of(), mainPath()).check(ast));
        return ex.getErrors();
    }

    private void assertClean(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        assertDoesNotThrow(() -> new StaticCheck(Set.of(), mainPath()).check(ast));
    }

    private boolean hasCode(List<MiraError> errors, String code) {
        return errors.stream().anyMatch(e -> code.equals(e.getErrorCode()));
    }

    @Test
    void namespaceCallWithOptionalArgOmittedIsClean() {
        assertClean("import module \"mymod.mira\" as lib; lib.greet(\"World\");");
    }

    @Test
    void namespaceCallWithOptionalArgProvidedIsClean() {
        assertClean("import module \"mymod.mira\" as lib; lib.greet(\"World\", \"Hi\");");
    }

    @Test
    void namespaceCallMissingRequiredArgProducesE307() {
        List<MiraError> errors = errorsFor("import module \"mymod.mira\" as lib; lib.greet();");
        assertTrue(hasCode(errors, "E307"), "Expected E307 for missing required namespace-call argument");
    }

    @Test
    void namespaceCallTooManyArgsProducesE307() {
        List<MiraError> errors = errorsFor("import module \"mymod.mira\" as lib; lib.greet(\"a\", \"b\", \"c\");");
        assertTrue(hasCode(errors, "E307"), "Expected E307 for too many namespace-call arguments");
    }
}
