package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Diagnostic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiagnosticCollectorTest {

    @Test
    void unusedImportAliasWarningRangeCoversTheWholeLine(@TempDir Path tempDir) throws IOException {
        Path mainPath = tempDir.resolve("main.mira");
        String source = "import native \"fixture.jar\" as ray;\n";
        Files.writeString(mainPath, source);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(source, mainPath, Map.of());
        Diagnostic unusedImport = diagnostics.stream()
                .filter(d -> d.getMessage().contains("'ray' is imported but never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unused 'ray' import diagnostic, got: " + diagnostics));

        int lineLength = "import native \"fixture.jar\" as ray;".length();
        assertEquals(0, unusedImport.getRange().getStart().getLine());
        assertEquals(0, unusedImport.getRange().getStart().getCharacter());
        assertEquals(0, unusedImport.getRange().getEnd().getLine());
        assertEquals(lineLength, unusedImport.getRange().getEnd().getCharacter());
    }

    @Test
    void functionUsedOnlyViaSelectiveImportIsNotFlaggedAsUnused(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        String libSource = """
                pub fn greet() {
                    return 1;
                }
                """;
        Files.writeString(libPath, libSource);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" {greet};
                fn main() {
                    return greet();
                }
                """);

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(libSource, libPath, Map.of());

        assertFalse(diagnostics.stream().anyMatch(d -> d.getMessage().contains("never called")));
    }

    @Test
    void workspaceIndexOverloadReusesCachedSiblingAstInsteadOfRereadingDisk(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        String libSource = """
                pub fn greet() {
                    return 1;
                }
                """;
        Files.writeString(libPath, libSource);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.greet();
                }
                """);

        WorkspaceIndex index = new WorkspaceIndex();
        // Prime the cache for main.mira, then change the file on disk without
        // invalidating the index - if collect() used the cache (as intended),
        // it still sees the old (caching-call) content and greet() is still
        // considered used; if it re-read from disk it would see the edited
        // version below where the call site is gone.
        index.getAst(mainPath, Map.of());
        Files.writeString(mainPath, "import module \"lib.mira\" as lib;\n");

        List<Diagnostic> diagnostics = DiagnosticCollector.collect(libSource, libPath, Map.of(), index, tempDir);

        assertFalse(diagnostics.stream().anyMatch(d -> d.getMessage().contains("never called")));
    }
}
