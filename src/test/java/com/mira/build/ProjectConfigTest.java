package com.mira.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProjectConfigTest {

    @TempDir
    Path root;

    private Map<String, Object> section(Object... pairs) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    void allFieldsPresent() {
        Map<String, Object> map = Map.of(
                "project", section(
                        "name", "demo",
                        "version", "2.0.0",
                        "entry", "src/main.mira",
                        "description", "A demo",
                        "authors", List.of("Alice")
                ),
                "build", section(
                        "mode", "compile",
                        "main", Boolean.TRUE,
                        "lint", Boolean.TRUE,
                        "output", "build",
                        "args", List.of("--verbose")
                ),
                "test", section(
                        "pattern", "tests/**/*.mira",
                        "extra", List.of("extra.mira")
                ),
                "dependencies", Map.of(
                        "lib", section("path", "../lib")
                )
        );

        ProjectConfig cfg = ProjectConfig.fromMap(map, root);

        assertEquals("demo", cfg.name());
        assertEquals("2.0.0", cfg.version());
        assertEquals(root.resolve("src/main.mira").normalize(), cfg.entry());
        assertEquals("A demo", cfg.description());
        assertEquals(List.of("Alice"), cfg.authors());

        assertEquals(ProjectConfig.BuildMode.COMPILE, cfg.build().mode());
        assertTrue(cfg.build().main());
        assertTrue(cfg.build().lint());
        assertEquals(root.resolve("build").normalize(), cfg.build().outputDir());
        assertArrayEquals(new String[]{"--verbose"}, cfg.build().args());

        assertEquals("tests/**/*.mira", cfg.test().pattern());
        assertEquals(List.of("extra.mira"), cfg.test().extra());

        assertTrue(cfg.dependencies().containsKey("lib"));
        assertEquals(root.resolve("../lib").normalize(), cfg.dependencies().get("lib").path());
    }

    @Test
    void defaultsApplied() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "src/main.mira")
        );

        ProjectConfig cfg = ProjectConfig.fromMap(map, root);

        assertEquals(root.getFileName().toString(), cfg.name());
        assertEquals("0.1.0", cfg.version());
        assertEquals("", cfg.description());
        assertTrue(cfg.authors().isEmpty());
        assertEquals(ProjectConfig.BuildMode.INTERPRET, cfg.build().mode());
        assertFalse(cfg.build().main());
        assertFalse(cfg.build().lint());
        assertEquals(root.resolve("out").normalize(), cfg.build().outputDir());
        assertEquals(0, cfg.build().args().length);
        assertEquals("**/*_test.mira", cfg.test().pattern());
        assertTrue(cfg.dependencies().isEmpty());
    }

    @Test
    void buildModePackage() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.PACKAGE, cfg.build().mode());
    }

    @Test
    void buildModeInterpretIsDefault() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "interpret")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.INTERPRET, cfg.build().mode());
    }

    @Test
    void unknownBuildModeFallsBackToInterpret() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "unknown")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.INTERPRET, cfg.build().mode());
    }

    @Test
    void entryIsResolvedRelativeToRoot() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "src/app/main.mira")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(root.resolve("src/app/main.mira").normalize(), cfg.entry());
    }

    @Test
    void projectRootIsStored() {
        Map<String, Object> map = Map.of("project", section("entry", "main.mira"));
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(root, cfg.projectRoot());
    }

    @Test
    void missingEntryThrows() {
        Map<String, Object> map = Map.of("project", section("name", "app"));
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void dependencyWithoutPathThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "dependencies", Map.of("bad-dep", section("git", "https://example.com"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }
}
