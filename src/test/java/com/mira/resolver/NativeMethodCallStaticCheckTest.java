package com.mira.resolver;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class NativeMethodCallStaticCheckTest {

    @TempDir
    Path tempDir;

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
    void knownNativeMethodOnDeclaredStringIsClean() {
        assertClean("import string; var s : String : \"hi\"; s.upper();");
    }

    @Test
    void unknownNativeMethodProducesE334() {
        List<MiraError> errors = errorsFor("import string; var s : String : \"hi\"; s.frobnicate();");
        assertTrue(hasCode(errors, "E334"), "Expected E334 for an unknown method on a known native type");
    }

    @Test
    void nativeMethodArityMismatchProducesE307() {
        List<MiraError> errors = errorsFor("import string; var s : String : \"hi\"; s.upper(1, 2);");
        assertTrue(hasCode(errors, "E307"), "Expected E307 for a native method called with the wrong argument count");
    }

    @Test
    void nativeMethodWithoutImportStaysSilent() {
        assertClean("var s : String : \"hi\"; s.upper();");
    }
}
