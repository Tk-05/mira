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

    @Test
    void fuzzyMatchFindsNonContiguousSubsequence(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                fn workspaceSymbolProvider() {
                    return 1;
                }
                fn unrelated() {
                    return 2;
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("wsp", tempDir, index, Map.of());

        assertEquals(1, results.size());
        assertEquals("workspaceSymbolProvider", results.get(0).getName());
    }

    @Test
    void fuzzyMatchRanksTighterMatchAboveScatteredOne(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                fn abXyz() {
                    return 1;
                }
                fn aXbYz() {
                    return 2;
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("ab", tempDir, index, Map.of());

        assertEquals(2, results.size());
        assertEquals("abXyz", results.get(0).getName());
    }

    @Test
    void queryThatDoesNotMatchAnySymbolReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), """
                fn foo() {
                    return 1;
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("zzz", tempDir, index, Map.of());

        assertEquals(0, results.size());
    }

    @Test
    void resultsAreCapped(@TempDir Path tempDir) throws IOException {
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            src.append("fn item").append(i).append("() { return 1; }\n");
        }
        Files.writeString(tempDir.resolve("a.mira"), src.toString());

        WorkspaceIndex index = new WorkspaceIndex();
        List<SymbolInformation> results = WorkspaceSymbolProvider.provide("item", tempDir, index, Map.of());

        assertTrue(results.size() <= 200);
    }

    @Test
    void symbolsAreCachedAcrossQueriesUntilFileChanges(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("a.mira");
        Files.writeString(file, "fn foo() { return 1; }\n");

        WorkspaceIndex index = new WorkspaceIndex();
        WorkspaceSymbolProvider.provide("foo", tempDir, index, Map.of());
        List<SymbolInformation> cachedFirst = index.getSymbols(file, Map.of());
        List<SymbolInformation> cachedSecond = index.getSymbols(file, Map.of());
        assertTrue(cachedFirst == cachedSecond);

        index.invalidate(file);
        List<SymbolInformation> afterInvalidate = index.getSymbols(file, Map.of());
        assertTrue(cachedFirst != afterInvalidate);
        assertEquals(1, afterInvalidate.size());
    }
}
