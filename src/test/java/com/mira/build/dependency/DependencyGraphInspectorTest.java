package com.mira.build.dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.build.DependencyResolver;
import com.mira.build.ProjectConfig;
import com.mira.build.ProjectLoader;
import com.mira.build.dependency.DependencyGraphInspector.DepNode;

/**
 * Covers the read-only, arbitrary-depth display walk used by "mira deps" —
 * distinct from DependencyResolver's actual one-level-deep [native]
 * transitivity: here, any available dependency's own dependencies are shown
 * too, no matter how deep, with cycle detection.
 */
public class DependencyGraphInspectorTest {

    @TempDir
    Path tmp;

    private String escaped(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    @Test
    void pathDependencyAvailableAndRecursesIntoItsOwnDependencies() throws Exception {
        Path grandchildDir = Files.createDirectory(tmp.resolve("grandchild"));
        Files.writeString(grandchildDir.resolve("mira.toml"),
                "[project]\nname = \"grandchild\"\nentry = \"g.mira\"\n");
        Files.createFile(grandchildDir.resolve("g.mira"));

        Path childDir = Files.createDirectory(tmp.resolve("child"));
        Files.writeString(childDir.resolve("mira.toml"),
                "[project]\nname = \"child\"\nentry = \"c.mira\"\n"
                + "[dependencies]\ngrandchild = { path = \"" + escaped(grandchildDir) + "\" }\n");
        Files.createFile(childDir.resolve("c.mira"));

        Path rootDir = Files.createDirectory(tmp.resolve("root"));
        Files.writeString(rootDir.resolve("mira.toml"),
                "[project]\nname = \"root\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nchild = { path = \"" + escaped(childDir) + "\" }\n");
        Files.createFile(rootDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(rootDir.resolve("mira.toml"));
        DepNode tree = DependencyGraphInspector.buildTree(cfg);

        assertEquals(1, tree.children().size());
        DepNode child = tree.children().get(0);
        assertTrue(child.available());
        assertEquals(1, child.children().size());
        DepNode grandchild = child.children().get(0);
        assertTrue(grandchild.available());
        assertEquals("grandchild", grandchild.name());
    }

    @Test
    void missingPathDependencyIsLeafMarkedMissing() throws Exception {
        Path rootDir = Files.createDirectory(tmp.resolve("root"));
        Files.writeString(rootDir.resolve("mira.toml"),
                "[project]\nname = \"root\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nghost = { path = \"../does-not-exist\" }\n");
        Files.createFile(rootDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(rootDir.resolve("mira.toml"));
        DepNode tree = DependencyGraphInspector.buildTree(cfg);

        assertEquals(1, tree.children().size());
        DepNode ghost = tree.children().get(0);
        assertFalse(ghost.available());
        assertTrue(ghost.children().isEmpty());
    }

    @Test
    void cycleIsDetectedInsteadOfInfiniteRecursion() throws Exception {
        Path aDir = Files.createDirectory(tmp.resolve("a"));
        Path bDir = Files.createDirectory(tmp.resolve("b"));
        Files.writeString(aDir.resolve("mira.toml"),
                "[project]\nname = \"a\"\nentry = \"a.mira\"\n"
                + "[dependencies]\nb = { path = \"" + escaped(bDir) + "\" }\n");
        Files.createFile(aDir.resolve("a.mira"));
        Files.writeString(bDir.resolve("mira.toml"),
                "[project]\nname = \"b\"\nentry = \"b.mira\"\n"
                + "[dependencies]\na = { path = \"" + escaped(aDir) + "\" }\n");
        Files.createFile(bDir.resolve("b.mira"));

        ProjectConfig cfg = ProjectLoader.load(aDir.resolve("mira.toml"));
        DepNode tree = DependencyGraphInspector.buildTree(cfg);

        DepNode b = tree.children().get(0);
        DepNode cyclicA = b.children().get(0);
        assertEquals("a", cyclicA.name());
        assertTrue(cyclicA.available());
        assertTrue(cyclicA.children().isEmpty());
        assertEquals("(cycle)", cyclicA.detail());
    }

    @Test
    void gitDependencyMissingWhenNeverResolved() throws Exception {
        Path rootDir = Files.createDirectory(tmp.resolve("root"));
        Files.writeString(rootDir.resolve("mira.toml"),
                "[project]\nname = \"root\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nlib = { git = \"https://example.com/lib.git\", tag = \"v1.0.0\" }\n");
        Files.createFile(rootDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(rootDir.resolve("mira.toml"));
        DepNode tree = DependencyGraphInspector.buildTree(cfg);

        DepNode lib = tree.children().get(0);
        assertFalse(lib.available());
        assertEquals("git", lib.kind());
    }

    @Test
    void nativeDependencyReflectsCacheStateBeforeAndAfterFetch() throws Exception {
        // Content must be unique per test run: the native artifact cache lives under the real
        // ~/.mira/packages (by design, shared across projects/processes), so a fixed byte string
        // would already be cached — and thus "available" — after the first time this test runs.
        byte[] content = ("native-fixture-" + java.util.UUID.randomUUID()).getBytes();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        Path fixtureDir = Files.createDirectory(tmp.resolve("fixture"));
        Files.write(fixtureDir.resolve("ext.jar"), content);

        Path rootDir = Files.createDirectory(tmp.resolve("root"));
        Files.writeString(rootDir.resolve("mira.toml"),
                "[project]\nname = \"root\"\nentry = \"main.mira\"\n"
                + "[native]\next = { url = \"" + fixtureDir.resolve("ext.jar").toUri()
                + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(rootDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(rootDir.resolve("mira.toml"));

        DepNode beforeFetch = DependencyGraphInspector.buildTree(cfg).children().get(0);
        assertFalse(beforeFetch.available());

        DependencyResolver.resolve(cfg);

        DepNode afterFetch = DependencyGraphInspector.buildTree(cfg).children().get(0);
        assertTrue(afterFetch.available());
    }
}
