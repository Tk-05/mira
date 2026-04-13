package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.LibImportConflictError;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class ImportTest extends InterpreterTestBase {

    @BeforeEach
    void resetImports() {
        ImportResolver.reset();
    }

    @Test
    void libImportMakesfunctionsAvailable() {
        assertEquals("hello", run("import string; trim(\" hello \");"));
    }

    @Test
    void libImportUnknownThrows() {
        assertThrows(RuntimeException.class, () -> run("import nonexistent;"));
    }

    @Test
    void libImportDuplicateSameLibIsIgnored() {
        assertEquals("HELLO", run("""
                import string;
                import string;
                trim("HELLO ");
                """));
    }

    @Test
    void libImportWithAliasMakesFunctionsAvailableViaNamespace() {
        assertEquals("hello", run("import string as str; str.trim(\" hello \");"));
    }

    @Test
    void libImportWithAliasDoesNotPollutGlobalScope() {
        assertThrows(RuntimeException.class, () -> run("import string as str; trim(\" hello \");"));
    }

    @Test
    void libImportWithAliasMathFunctions() {
        assertEquals(4.0, run("import math as m; m.pow(2, 2);"));
    }

    @Test
    void libImportWithAliasTwoLibsSameAlias() {
        assertThrows(RuntimeException.class, () -> run("""
                import string as lib;
                import math as lib;
                """));
    }

    @Test
    void libImportSameLibWithAndWithoutAlias() {
        assertEquals("hello", run("""
                import string;
                import string as str;
                str.trim(" hello ");
                """));
    }

    @Test
    void libImportConflictThrows() {
        assertThrows(LibImportConflictError.class, () -> run("""
                import string;
                import collection;
                """));
    }

    @Test
    void libImportConflictResolvedWithAlias() {
        assertEquals("hello", run("""
                import string;
                import collection as col;
                trim(" hello ");
                """));
    }

    @Test
    void libImportBothAliasedNoConflict() {
        assertEquals("hello", run("""
                import string as str;
                import collection as col;
                str.trim(" hello ");
                """));
    }

    @Test
    void selectiveImportMakesOnlySelectedFunctionsAvailable() {
        assertEquals("hello", run("import string: trim; trim(\" hello \");"));
    }

    @Test
    void selectiveImportDoesNotLoadOtherFunctions() {
        assertThrows(RuntimeException.class, () ->
            run("import string: trim; split(\"a,b\", \",\");"));
    }

    @Test
    void selectiveImportWithAlias() {
        assertEquals("hello", run("import string: trim as str; str.trim(\" hello \");"));
    }

    @Test
    void selectiveImportUnknownFunctionThrows() {
        assertThrows(RuntimeException.class, () ->
            run("import string: nonexistent;"));
    }

    @Test
    void selectiveImportReducesConflicts() {
        assertEquals("hello", run("""
                import string: trim;
                import collection;
                trim(" hello ");
                """));
    }
}
