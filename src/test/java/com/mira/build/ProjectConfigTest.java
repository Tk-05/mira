package com.mira.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(root.resolve("build").normalize(), cfg.build().outputDir());
        assertArrayEquals(new String[]{"--verbose"}, cfg.build().args());

        assertEquals("tests/**/*.mira", cfg.test().pattern());
        assertEquals(List.of("extra.mira"), cfg.test().extra());

        assertTrue(cfg.dependencies().containsKey("lib"));
        assertEquals(root.resolve("../lib").normalize(),
                ((ProjectConfig.Dependency.PathDependency) cfg.dependencies().get("lib")).path());
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
        assertEquals(root.resolve("out").normalize(), cfg.build().outputDir());
        assertEquals(0, cfg.build().args().length);
        assertNull(cfg.test());
        assertTrue(cfg.dependencies().isEmpty());
        assertFalse(cfg.build().strictTypes());
    }

    @Test
    void strictTypesParsedFromBuildSection() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "src/main.mira"),
                "build", section("strict-types", Boolean.TRUE)
        );

        ProjectConfig cfg = ProjectConfig.fromMap(map, root);

        assertTrue(cfg.build().strictTypes());
    }

    @Test
    void buildModePackage() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package", "jar-bundle", "full")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.PACKAGE, cfg.build().mode());
        assertEquals(ProjectConfig.JarBundle.FULL, cfg.build().jarBundle());
    }

    @Test
    void buildModePackageSlimBundle() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package", "jar-bundle", "slim")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.JarBundle.SLIM, cfg.build().jarBundle());
    }

    @Test
    void buildModePackageWithoutJarBundleThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package")
        );
        BuildException ex = assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
        assertTrue(ex.getMessage().contains("jar-bundle"));
    }

    @Test
    void jarBundleWithoutPackageModeThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "interpret", "jar-bundle", "slim")
        );
        BuildException ex = assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
        assertTrue(ex.getMessage().contains("jar-bundle"));
    }

    @Test
    void unknownJarBundleThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package", "jar-bundle", "bogus")
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
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
    void runModeExplicitlySet() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "compile", "run-mode", "interpret")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.COMPILE, cfg.build().mode());
        assertEquals(ProjectConfig.BuildMode.INTERPRET, cfg.build().effectiveRunMode());
    }

    @Test
    void runModeFallsBackToBuildMode() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "compile")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.COMPILE, cfg.build().effectiveRunMode());
    }

    @Test
    void runModeIndependentOfBuildMode() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "package", "run-mode", "compile", "jar-bundle", "full")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(ProjectConfig.BuildMode.PACKAGE, cfg.build().mode());
        assertEquals(ProjectConfig.BuildMode.COMPILE, cfg.build().effectiveRunMode());
    }

    @Test
    void testSectionNullWhenNotInToml() {
        Map<String, Object> map = Map.of("project", section("entry", "main.mira"));
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertNull(cfg.test());
    }

    @Test
    void testSectionPresentWhenDefined() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "test", section("pattern", "tests/**/*.mira", "extra", List.of())
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertNotNull(cfg.test());
        assertEquals("tests/**/*.mira", cfg.test().pattern());
    }

    @Test
    void entryIsNullWhenAbsent() {
        Map<String, Object> map = Map.of("project", section("name", "app"));
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertNull(cfg.entry());
    }

    @Test
    void dependencyWithoutPathThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "dependencies", Map.of("bad-dep", section("git", "https://example.com"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void nativeSectionEmptyWhenAbsent() {
        Map<String, Object> map = Map.of("project", section("entry", "main.mira"));
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertTrue(cfg.nativeDependencies().isEmpty());
    }

    @Test
    void nativeDependencyParsed() {
        String sha = "a".repeat(64);
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of(
                        "raylib", section("url", "https://example.com/raylib.jar", "sha256", sha)
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        ProjectConfig.NativeDependency dep = cfg.nativeDependencies().get("raylib");
        assertNotNull(dep);
        assertEquals("https://example.com/raylib.jar", dep.url());
        assertEquals(sha, dep.sha256());
    }

    @Test
    void nativeDependencySha256IsLowercased() {
        String sha = "A".repeat(64);
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of(
                        "raylib", section("url", "https://example.com/raylib.jar", "sha256", sha)
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(sha.toLowerCase(java.util.Locale.ROOT), cfg.nativeDependencies().get("raylib").sha256());
    }

    @Test
    void nativeDependencyMissingUrlThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of("raylib", section("sha256", "a".repeat(64)))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void nativeDependencyMissingSha256Throws() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of("raylib", section("url", "https://example.com/raylib.jar"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void nativeDependencyFileUrlWithoutSha256IsAllowed() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of("raylib", section("url", "file:///C:/raylib.jar"))
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        ProjectConfig.NativeDependency dep = cfg.nativeDependencies().get("raylib");
        assertNotNull(dep);
        assertNull(dep.sha256());
    }

    @Test
    void nativeDependencyFileUrlWithSha256StillValidatesFormat() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of("raylib", section("url", "file:///C:/raylib.jar", "sha256", "not-a-hash"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void nativeDependencyInvalidSha256Throws() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of(
                        "raylib", section("url", "https://example.com/raylib.jar", "sha256", "not-a-hash")
                )
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void nativeDependencyUnknownKeyThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "native", Map.of(
                        "raylib", section("url", "https://example.com/raylib.jar",
                                "sha256", "a".repeat(64), "bogus", "x")
                )
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void noTasksWhenSectionAbsent() {
        Map<String, Object> map = Map.of("project", section("entry", "main.mira"));
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertTrue(cfg.tasks().isEmpty());
    }

    @Test
    void taskShorthandParsedAsCmd() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section("clean", "rm -rf out/")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        TaskConfig task = cfg.tasks().get("clean");
        assertNotNull(task);
        assertEquals("rm -rf out/", task.cmd());
        assertNull(task.script());
        assertTrue(task.isCmd());
    }

    @Test
    void taskShorthandMiraExtensionParsedAsScript() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section("demo", "scripts/demo.mira")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        TaskConfig task = cfg.tasks().get("demo");
        assertNotNull(task);
        assertNull(task.cmd());
        assertEquals("scripts/demo.mira", task.script());
        assertFalse(task.isCmd());
    }

    @Test
    void taskWithInlineTable() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section(
                        "codegen", section("script", "scripts/gen.mira", "description", "Generate code")
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        TaskConfig task = cfg.tasks().get("codegen");
        assertNotNull(task);
        assertNull(task.cmd());
        assertEquals("scripts/gen.mira", task.script());
        assertEquals("Generate code", task.description());
        assertFalse(task.isCmd());
    }

    @Test
    void taskWithBothCmdAndScriptThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section("bad", section("cmd", "echo hi", "script", "x.mira"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void taskWithNeitherCmdNorScriptThrows() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section("bad", section("description", "missing action"))
        );
        assertThrows(BuildException.class, () -> ProjectConfig.fromMap(map, root));
    }

    @Test
    void multipleTasksParsed() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "tasks", section(
                        "clean", "rm -rf out/",
                        "build", section("cmd", "gradle build", "description", "Build native")
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(2, cfg.tasks().size());
        assertTrue(cfg.tasks().containsKey("clean"));
        assertTrue(cfg.tasks().containsKey("build"));
    }

    @Test
    void buildHooksSingleString() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "interpret", "pre-build", "codegen", "post-build", "notify")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(List.of("codegen"), cfg.build().preBuild());
        assertEquals(List.of("notify"), cfg.build().postBuild());
    }

    @Test
    void buildHooksArray() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section(
                        "pre-build", List.of("codegen", "lint"),
                        "post-build", List.of("notify", "upload")
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(List.of("codegen", "lint"), cfg.build().preBuild());
        assertEquals(List.of("notify", "upload"), cfg.build().postBuild());
    }

    @Test
    void runHooksParsed() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("pre-run", "prepare", "post-run", "cleanup")
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertEquals(List.of("prepare"), cfg.build().preRun());
        assertEquals(List.of("cleanup"), cfg.build().postRun());
    }

    @Test
    void testHooksParsed() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "test", section(
                        "pattern", "**/*_test.mira", "extra", List.of(),
                        "pre-test", "seed-db", "post-test", "teardown"
                )
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertNotNull(cfg.test());
        assertEquals(List.of("seed-db"), cfg.test().preTest());
        assertEquals(List.of("teardown"), cfg.test().postTest());
    }

    @Test
    void hooksEmptyWhenNotSet() {
        Map<String, Object> map = Map.of(
                "project", section("entry", "main.mira"),
                "build", section("mode", "interpret"),
                "test", section("pattern", "**/*_test.mira", "extra", List.of())
        );
        ProjectConfig cfg = ProjectConfig.fromMap(map, root);
        assertTrue(cfg.build().preBuild().isEmpty());
        assertTrue(cfg.build().postBuild().isEmpty());
        assertTrue(cfg.build().preRun().isEmpty());
        assertTrue(cfg.build().postRun().isEmpty());
        assertTrue(cfg.test().preTest().isEmpty());
        assertTrue(cfg.test().postTest().isEmpty());
    }
}
