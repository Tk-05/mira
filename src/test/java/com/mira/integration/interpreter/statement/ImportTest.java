package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.LibImportConflictError;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractImportTests;

public class ImportTest extends AbstractImportTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
        ImportResolver.reset();
    }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void libImportUnknownThrows() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("import nonexistent;"));
    }

    @Test
    void libImportDuplicateSameLibIsIgnored() {
        assertEquals("HELLO", backend.runAndGetValue("""
                import string;
                import string;
                trim("HELLO ");
                """));
    }

    @Test
    void libImportWithAliasMakesFunctionsAvailableViaNamespace() {
        assertEquals("hello", backend.runAndGetValue("import string as str; str.trim(\" hello \");"));
    }

    @Test
    void libImportWithAliasDoesNotPollutGlobalScope() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("import string as str; trim(\" hello \");"));
    }

    @Test
    void libImportWithAliasMathFunctions() {
        assertEquals(4.0, InterpreterRunner.normNum(backend.runAndGetValue("import math as m; m.pow(2, 2);")));
    }

    @Test
    void libImportWithAliasTwoLibsSameAlias() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("""
                import string as lib;
                import math as lib;
                """));
    }

    @Test
    void libImportConflictThrows() {
        assertThrows(LibImportConflictError.class, () -> backend.runAndGetValue("""
                import string;
                import collection;
                """));
    }

    @Test
    void libImportConflictResolvedWithAlias() {
        assertEquals("hello", backend.runAndGetValue("""
                import string;
                import collection as col;
                trim(" hello ");
                """));
    }

    @Test
    void libImportBothAliasedNoConflict() {
        assertEquals("hello", backend.runAndGetValue("""
                import string as str;
                import collection as col;
                str.trim(" hello ");
                """));
    }

    @Test
    void selectiveImportMakesOnlySelectedFunctionsAvailable() {
        assertEquals("hello", backend.runAndGetValue("import string: trim; trim(\" hello \");"));
    }

    @Test
    void selectiveImportDoesNotLoadOtherFunctions() {
        assertThrows(RuntimeException.class, () ->
            backend.runAndGetValue("import string: trim; split(\"a,b\", \",\");"));
    }

    @Test
    void selectiveImportWithAlias() {
        assertEquals("hello", backend.runAndGetValue("import string: trim as str; str.trim(\" hello \");"));
    }

    @Test
    void selectiveImportUnknownFunctionThrows() {
        assertThrows(RuntimeException.class, () ->
            backend.runAndGetValue("import string: nonexistent;"));
    }

    @Test
    void selectiveImportReducesConflicts() {
        assertEquals("hello", backend.runAndGetValue("""
                import string: trim;
                import collection;
                trim(" hello ");
                """));
    }

    @Test
    void namespaceCallInsideSameNamedMethodStillResolvesToImportAlias() {
        assertEquals("hi", backend.runAndGetValue("""
                import string as helper;
                var obj : {
                    fn helper() {
                        return helper.trim(" hi ");
                    }
                };
                obj.helper();
                """));
    }

    @Test
    void namespaceCallInsideDifferentlyNamedMethodStillWorks() {
        assertEquals("hi", backend.runAndGetValue("""
                import string as str;
                var obj : {
                    fn run() {
                        return str.trim(" hi ");
                    }
                };
                obj.run();
                """));
    }
}
