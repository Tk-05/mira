package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

public abstract class AbstractImportTests {

    protected abstract String runForOutput(String source);

    @Test
    void libImportMakesFunctionsAvailable() {
        assertDoesNotThrow(() -> runForOutput("import string; print(trim(\" hello \"));"));
    }

    @Test
    void libImportWithAlias() {
        assertDoesNotThrow(() -> runForOutput("import string as s; print(s.trim(\" hello \"));"));
    }
}
