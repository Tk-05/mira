package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CodeLens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CodeLensProviderTest {

    @Test
    void showsZeroReferencesForUncalledFunction(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn unused() {
                    return 1;
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        assertEquals(1, lenses.size());
        assertEquals("0 references", lenses.get(0).getCommand().getTitle());
    }

    @Test
    void countsCallSitesAsReferences(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    helper();
                    return helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        // one lens for "helper" (2 references), one for "main" (0 references)
        assertEquals(2, lenses.size());
        assertTrue(lenses.stream().anyMatch(l -> "2 references".equals(l.getCommand().getTitle())));
        assertTrue(lenses.stream().anyMatch(l -> "0 references".equals(l.getCommand().getTitle())));
    }

    @Test
    void referenceLensIsClickableAndShowsLocations(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        CodeLens helperLens = lenses.stream().filter(l -> "1 reference".equals(l.getCommand().getTitle())).findFirst()
                .orElseThrow();
        assertEquals("mira.showReferences", helperLens.getCommand().getCommand());
        assertEquals(3, helperLens.getCommand().getArguments().size());
    }

    @Test
    void skipsStructAndObjectLiteralMethods(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                var obj : {
                    fn method() {
                        return 1;
                    }
                };
                var Point : struct {
                    fn dist() {
                        return 0;
                    }
                };
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        assertEquals(0, lenses.size(), "struct/object methods must not get a reference-count lens");
    }

    @Test
    void addsRunTestsLensForEachTestCall(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                test("first", fn () {
                    assert(true);
                });
                test("second", fn () {
                    assert(true);
                });
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        assertEquals(2, lenses.size());
        for (CodeLens lens : lenses) {
            assertEquals("▶ Run Tests", lens.getCommand().getTitle());
            assertEquals("mira.runTests", lens.getCommand().getCommand());
            assertEquals(List.of(uri), lens.getCommand().getArguments());
        }
    }

    @Test
    void nestedFunctionsStillGetReferenceLens(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.mira");
        String source = """
                fn outer() {
                    fn inner() {
                        return 1;
                    }
                    return inner();
                }
                """;
        Files.writeString(file, source);
        WorkspaceIndex index = new WorkspaceIndex();
        String uri = file.toUri().toString();

        List<CodeLens> lenses = CodeLensProvider.provide(index.getAst(file, Map.of()), source, uri, file, index,
                tempDir, Map.of());

        assertEquals(2, lenses.size());
        assertTrue(lenses.stream().anyMatch(l -> "1 reference".equals(l.getCommand().getTitle())));
    }
}
