package com.mira.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage for "mira install" + resolving a dependency by bare
 * {@code version = "..."} (no path, no git) against the local install cache.
 */
public class LocalRegistryTest {

    @TempDir
    Path tmp;

    private Path writeLibraryProject() throws Exception {
        Path libDir = Files.createDirectory(tmp.resolve("mylib"));
        Files.writeString(libDir.resolve("mira.toml"),
                "[project]\nname = \"mylib\"\nversion = \"1.0.0\"\nentry = \"lib.mira\"\n");
        Files.writeString(libDir.resolve("lib.mira"), "module mylib;\npub fn greet() { print(\"hi\"); }\n");
        return libDir;
    }

    private Path writeConsumerProject(String depSpec) throws Exception {
        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nmylib = " + depSpec + "\n");
        Files.createFile(consumerDir.resolve("main.mira"));
        return consumerDir;
    }

    @Test
    void installThenResolveByVersionAlone() throws Exception {
        Path libDir = writeLibraryProject();
        Commands.install(new String[]{"install"}, libDir);

        Path consumerDir = writeConsumerProject("{ version = \"1.0.0\" }");
        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        List<Path> roots = DependencyResolver.resolve(cfg).sourceRoots();

        assertEquals(1, roots.size());
        assertTrue(Files.exists(roots.get(0).resolve("lib.mira")));
        assertFalse(Files.exists(roots.get(0).resolve("mira.lock")), "install should not copy mira.lock");
    }

    @Test
    void reinstallOverwritesPreviousContentForSameVersion() throws Exception {
        Path libDir = writeLibraryProject();
        Commands.install(new String[]{"install"}, libDir);

        Files.writeString(libDir.resolve("lib.mira"), "module mylib;\npub fn greet() { print(\"updated\"); }\n");
        Commands.install(new String[]{"install"}, libDir);

        Path consumerDir = writeConsumerProject("{ version = \"1.0.0\" }");
        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        List<Path> roots = DependencyResolver.resolve(cfg).sourceRoots();

        assertTrue(Files.readString(roots.get(0).resolve("lib.mira")).contains("updated"));
    }

    @Test
    void resolveFailsWithHelpfulErrorWhenNotInstalled() throws Exception {
        Path consumerDir = writeConsumerProject("{ version = \"9.9.9\" }");
        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));

        BuildException ex = assertThrows(BuildException.class, () -> DependencyResolver.resolve(cfg));
        assertTrue(ex.getMessage().contains("mira install"));
    }
}
