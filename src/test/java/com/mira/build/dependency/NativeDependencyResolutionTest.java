package com.mira.build.dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.build.DependencyResolver;
import com.mira.build.ProjectConfig;
import com.mira.build.ProjectLoader;

/**
 * Covers the arbitrary-depth transitive [native] pickup in DependencyResolver:
 * a resolved path/git/registry dependency's own [native] table is picked up for
 * the consumer, and so is that dependency's dependencies' [native] tables, and
 * so on — however deep the graph goes. Cycle-safe, and a nested dependency that
 * can't itself be resolved is skipped rather than failing the whole build (it's
 * unrelated to what's actually being built).
 */
public class NativeDependencyResolutionTest {

    @TempDir
    Path tmp;

    private String sha256Of(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private String writeNativeFixture(Path dir, String basename, byte[] content) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve(basename), content);
        return sha256Of(content);
    }

    private String escaped(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    @Test
    void ownNativeTableIsResolvedIntoNativeRoots() throws Exception {
        Path fixtureDir = tmp.resolve("fixture");
        String sha = writeNativeFixture(fixtureDir, "ext.jar", "own-native".getBytes());
        String url = fixtureDir.resolve("ext.jar").toUri().toString();

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[native]\next = { url = \"" + url + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.nativeRoots().size());
        assertTrue(Files.exists(resolution.nativeRoots().get(0).resolve("ext.jar")));
    }

    @Test
    void transitiveNativeFromPathDependencyIsResolvedWithoutRedeclaration() throws Exception {
        Path fixtureDir = tmp.resolve("fixture");
        String sha = writeNativeFixture(fixtureDir, "lib.jar", "transitive-native".getBytes());
        String url = fixtureDir.resolve("lib.jar").toUri().toString();

        Path libDir = Files.createDirectory(tmp.resolve("lib"));
        Files.writeString(libDir.resolve("mira.toml"),
                "[project]\nname = \"lib\"\nentry = \"lib.mira\"\n"
                + "[native]\nnative-lib = { url = \"" + url + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(libDir.resolve("lib.mira"));

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nlib = { path = \"" + escaped(libDir) + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.sourceRoots().size());
        assertEquals(1, resolution.nativeRoots().size());
        assertTrue(Files.exists(resolution.nativeRoots().get(0).resolve("lib.jar")));
    }

    @Test
    void recursesTwoLevelsDeep() throws Exception {
        Path fixtureDir = tmp.resolve("fixture");
        String sha = writeNativeFixture(fixtureDir, "deep.jar", "deep-native".getBytes());
        String url = fixtureDir.resolve("deep.jar").toUri().toString();

        Path libBDir = Files.createDirectory(tmp.resolve("libB"));
        Files.writeString(libBDir.resolve("mira.toml"),
                "[project]\nname = \"libB\"\nentry = \"b.mira\"\n"
                + "[native]\ndeep = { url = \"" + url + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(libBDir.resolve("b.mira"));

        Path libADir = Files.createDirectory(tmp.resolve("libA"));
        Files.writeString(libADir.resolve("mira.toml"),
                "[project]\nname = \"libA\"\nentry = \"a.mira\"\n"
                + "[dependencies]\nlibB = { path = \"" + escaped(libBDir) + "\" }\n");
        Files.createFile(libADir.resolve("a.mira"));

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nlibA = { path = \"" + escaped(libADir) + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.sourceRoots().size());
        assertEquals(1, resolution.nativeRoots().size(),
                "libB's [native] table is two levels deep and must still be picked up");
        assertTrue(Files.exists(resolution.nativeRoots().get(0).resolve("deep.jar")));
    }

    /**
     * Mirrors the real "Breakout depends on Engine depends on Raylib" scenario,
     * via mira-install-style deps.
     */
    @Test
    void breakoutEngineRaylibChainViaRegistryDependencies() throws Exception {
        Path fixtureDir = tmp.resolve("fixture");
        String sha = writeNativeFixture(fixtureDir, "raylib.jar", "raylib-native".getBytes());
        String url = fixtureDir.resolve("raylib.jar").toUri().toString();

        Path raylibInstall = LocalRegistry.installDir("raylib", "0.1.0");
        Files.createDirectories(raylibInstall);
        Files.writeString(raylibInstall.resolve("mira.toml"),
                "[project]\nname = \"raylib\"\nversion = \"0.1.0\"\n"
                + "[native]\nraylib = { url = \"" + url + "\", sha256 = \"" + sha + "\" }\n");

        Path engineInstall = LocalRegistry.installDir("engine", "1.0.0");
        Files.createDirectories(engineInstall);
        Files.writeString(engineInstall.resolve("mira.toml"),
                "[project]\nname = \"engine\"\nversion = \"1.0.0\"\nentry = \"engine.mira\"\n"
                + "[dependencies]\nraylib = { version = \"0.1.0\" }\n");
        Files.writeString(engineInstall.resolve("engine.mira"), "module engine;\n");

        Path breakoutDir = Files.createDirectory(tmp.resolve("breakout"));
        Files.writeString(breakoutDir.resolve("mira.toml"),
                "[project]\nname = \"breakout\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nengine = { version = \"1.0.0\" }\n");
        Files.createFile(breakoutDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(breakoutDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.sourceRoots().size());
        assertEquals(1, resolution.nativeRoots().size(),
                "Breakout must find raylib.jar even though it's declared two levels down, in Raylib's own mira.toml");
        assertTrue(Files.exists(resolution.nativeRoots().get(0).resolve("raylib.jar")));
    }

    @Test
    void cycleInTransitiveGraphDoesNotInfiniteLoop() throws Exception {
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

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\na = { path = \"" + escaped(aDir) + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));

        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.sourceRoots().size());
        assertTrue(resolution.nativeRoots().isEmpty());
    }

    @Test
    void twoUnhashedNativeDependenciesBothResolveWithoutFalseDedup() throws Exception {
        Path fixtureDirA = Files.createDirectory(tmp.resolve("fixtureA"));
        Files.writeString(fixtureDirA.resolve("a.jar"), "content-a");
        Path fixtureDirB = Files.createDirectory(tmp.resolve("fixtureB"));
        Files.writeString(fixtureDirB.resolve("b.jar"), "content-b");

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[native]\n"
                + "extA = { url = \"" + fixtureDirA.resolve("a.jar").toUri() + "\" }\n"
                + "extB = { url = \"" + fixtureDirB.resolve("b.jar").toUri() + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(2, resolution.nativeRoots().size(),
                "two distinct unhashed native deps must not be treated as duplicates of each other");
    }

    @Test
    void unresolvableNestedDependencyIsSkippedNotFatal() throws Exception {
        Path fixtureDir = tmp.resolve("fixture");
        String sha = writeNativeFixture(fixtureDir, "found.jar", "found-native".getBytes());
        String url = fixtureDir.resolve("found.jar").toUri().toString();

        Path libDir = Files.createDirectory(tmp.resolve("lib"));
        Files.writeString(libDir.resolve("mira.toml"),
                "[project]\nname = \"lib\"\nentry = \"lib.mira\"\n"
                + "[dependencies]\n"
                + "unrelated-broken = { path = \"../does-not-exist\" }\n"
                + "[native]\nfound = { url = \"" + url + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(libDir.resolve("lib.mira"));

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\nlib = { path = \"" + escaped(libDir) + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(1, resolution.sourceRoots().size());
        assertEquals(1, resolution.nativeRoots().size(),
                "lib's own [native] table must still resolve even though one of its OTHER, unrelated "
                + "dependencies can't be found");
    }

    @Test
    void duplicateNativeShaAcrossDependenciesResolvesOnce() throws Exception {
        byte[] content = "shared-native".getBytes();
        String sha = sha256Of(content);

        Path fixtureA = tmp.resolve("fixtureA");
        Files.createDirectories(fixtureA);
        Files.write(fixtureA.resolve("shared.jar"), content);
        Path fixtureB = tmp.resolve("fixtureB");
        Files.createDirectories(fixtureB);
        Files.write(fixtureB.resolve("shared.jar"), content);

        Path libADir = Files.createDirectory(tmp.resolve("libA"));
        Files.writeString(libADir.resolve("mira.toml"),
                "[project]\nname = \"libA\"\nentry = \"a.mira\"\n"
                + "[native]\nshared = { url = \"" + fixtureA.resolve("shared.jar").toUri()
                + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(libADir.resolve("a.mira"));

        Path libBDir = Files.createDirectory(tmp.resolve("libB"));
        Files.writeString(libBDir.resolve("mira.toml"),
                "[project]\nname = \"libB\"\nentry = \"b.mira\"\n"
                + "[native]\nshared = { url = \"" + fixtureB.resolve("shared.jar").toUri()
                + "\", sha256 = \"" + sha + "\" }\n");
        Files.createFile(libBDir.resolve("b.mira"));

        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n"
                + "[dependencies]\n"
                + "libA = { path = \"" + escaped(libADir) + "\" }\n"
                + "libB = { path = \"" + escaped(libBDir) + "\" }\n");
        Files.createFile(consumerDir.resolve("main.mira"));

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        DependencyResolver.Resolution resolution = DependencyResolver.resolve(cfg);

        assertEquals(2, resolution.sourceRoots().size());
        assertEquals(1, resolution.nativeRoots().size(), "same sha256 across two deps should resolve once");
    }
}
