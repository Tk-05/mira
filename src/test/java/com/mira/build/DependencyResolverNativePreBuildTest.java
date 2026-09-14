package com.mira.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A native-only package (no [project] entry, like the real mira-raylib) that
 * declares a [build] pre-build cmd task meant to actually produce its own
 * [native] jar — mirroring the exact "mvn package" pattern this feature was
 * built for, using a portable "echo > file" command instead so the test doesn't
 * depend on a real Maven build.
 */
public class DependencyResolverNativePreBuildTest {

    @TempDir
    Path tmp;

    private void writeFixtureToml(String preBuildLine) throws IOException {
        Files.createDirectories(tmp.resolve("target"));
        Files.writeString(tmp.resolve("mira.toml"), """
                [project]
                name = "fixture"
                version = "0.1.0"

                [build]
                %s

                [tasks.make-jar]
                cmd = "echo native-content > target/x.jar"

                [native]
                x = { url = "file:///target/x.jar" }
                """.formatted(preBuildLine));
    }

    @Test
    void preBuildCmdTaskRunsBeforeNativeTableIsResolved() throws IOException {
        writeFixtureToml("pre-build = \"make-jar\"");
        Path jarPath = tmp.resolve("target").resolve("x.jar");
        assertFalse(Files.exists(jarPath), "sanity check: fixture must not pre-exist the jar itself");

        ProjectConfig config = ProjectLoader.load(tmp.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(config);

        assertTrue(Files.exists(jarPath), "pre-build task should have created the native jar before resolution");
        assertEquals(1, resolution.nativeRoots().size());
        assertEquals(jarPath.getParent(), resolution.nativeRoots().get(0));
    }

    @Test
    void withoutPreBuildHookMissingNativeJarStillThrows() throws IOException {
        writeFixtureToml("");

        ProjectConfig config = ProjectLoader.load(tmp.resolve("mira.toml"));

        org.junit.jupiter.api.Assertions.assertThrows(BuildException.class, () -> DependencyResolver.resolve(config));
    }
}
