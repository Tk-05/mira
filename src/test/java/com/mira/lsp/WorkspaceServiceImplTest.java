package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WorkspaceServiceImplTest {

    @Test
    void didChangeWatchedFilesInvalidatesFileListOnNewMiraFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mira"), "module a;\n");

        WorkspaceIndex index = new WorkspaceIndex();
        WorkspaceServiceImpl service = new WorkspaceServiceImpl(index, new DocumentService(null, index));
        service.setWorkspaceRoot(tempDir);

        assertEquals(1, index.allMiraFiles(tempDir).size(), "cache should be primed with just a.mira");

        Path newFile = tempDir.resolve("b.mira");
        Files.writeString(newFile, "module b;\n");
        FileEvent created = new FileEvent(newFile.toUri().toString(), FileChangeType.Created);
        service.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(created)));

        List<Path> files = index.allMiraFiles(tempDir);
        assertEquals(2, files.size(), "stale cached file list must be invalidated after a Created event");
        assertTrue(files.stream().anyMatch(p -> p.equals(newFile)));
    }

    @Test
    void didChangeWatchedFilesInvalidatesAstCacheOnChangedFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("a.mira");
        Files.writeString(file, "module a;\nfn old() { return 1; }\n");

        WorkspaceIndex index = new WorkspaceIndex();
        index.getAst(file, Map.of());
        assertTrue(index.getSource(file, Map.of()).contains("old"));

        Files.writeString(file, "module a;\nfn fresh() { return 2; }\n");
        WorkspaceServiceImpl service = new WorkspaceServiceImpl(index, new DocumentService(null, index));
        FileEvent changed = new FileEvent(file.toUri().toString(), FileChangeType.Changed);
        service.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(changed)));

        assertTrue(index.getSource(file, Map.of()).contains("fresh"),
                "stale cached AST/source must be invalidated after a Changed event");
    }
}
