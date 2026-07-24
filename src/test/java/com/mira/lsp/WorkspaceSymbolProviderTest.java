package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SymbolInformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WorkspaceSymbolProviderTest {

    @Test
    void findsSymbolsAcrossFilesByQuery(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                fn addNumbers() {
                    return 1;
                }
                """);
        Files.writeString(tempDir.resolve("b.mira"), """
                fn subtractNumbers() {
                    return 1;
                }
                fn unrelated() {
                    return 2;
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("Numbers", tempDir, index, Map.of());

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(s -> s.getName().equals("addNumbers")));
        assertTrue(results.stream().anyMatch(s -> s.getName().equals("subtractNumbers")));
    }

    @Test
    void emptyQueryReturnsAllSymbols(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                fn foo() {
                    return 1;
                }
                var bar : 2;
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("", tempDir, index, Map.of());

        assertEquals(2, results.size());
    }
}
