package com.mira.build.dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.build.BuildException;
import com.mira.build.ProjectConfig;

/**
 * Uses file:// URLs against a small local fixture file (not a real jar's worth
 * of bytes — content doesn't matter for these tests, only that it hashes and
 * round-trips correctly).
 */
public class NativeArtifactFetcherTest {

    @TempDir
    Path tmp;

    private String sha256Of(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @Test
    void downloadsVerifiesAndCachesByFileUrl() throws Exception {
        byte[] content = "fixture-jar-bytes".getBytes();
        Path source = tmp.resolve("dummy.jar");
        Files.write(source, content);
        String sha = sha256Of(content);

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve(
                "dummy", new ProjectConfig.NativeDependency(source.toUri().toString(), sha));

        assertEquals(NativeArtifactFetcher.artifactDir(sha).resolve("dummy.jar"), resolved.jarPath());
        assertTrue(Files.exists(resolved.jarPath()));
        assertEquals("fixture-jar-bytes", Files.readString(resolved.jarPath()));
    }

    @Test
    void rejectsMismatchedSha256AndLeavesNoCacheEntry() throws Exception {
        byte[] content = "fixture-jar-bytes".getBytes();
        Path source = tmp.resolve("dummy.jar");
        Files.write(source, content);
        String wrongSha = "0".repeat(64);

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve(
                "dummy", new ProjectConfig.NativeDependency(source.toUri().toString(), wrongSha)));
        assertFalse(Files.exists(NativeArtifactFetcher.artifactDir(wrongSha)));
    }

    @Test
    void secondResolveIsCacheHitAndSurvivesSourceDeletion() throws Exception {
        byte[] content = "fixture-jar-bytes-2".getBytes();
        Path source = tmp.resolve("dummy2.jar");
        Files.write(source, content);
        String sha = sha256Of(content);
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), sha);

        NativeArtifactFetcher.Resolved first = NativeArtifactFetcher.resolve("dummy2", dep);
        Files.delete(source);

        NativeArtifactFetcher.Resolved second = NativeArtifactFetcher.resolve("dummy2", dep);
        assertEquals(first.jarPath(), second.jarPath());
        assertTrue(Files.exists(second.jarPath()));
    }

    @Test
    void twoUrlsWithSameShaShareOneCacheEntry() throws Exception {
        byte[] content = "shared-content".getBytes();
        String sha = sha256Of(content);

        Path sourceA = tmp.resolve("a").resolve("lib.jar");
        Files.createDirectories(sourceA.getParent());
        Files.write(sourceA, content);
        Path sourceB = tmp.resolve("b").resolve("lib.jar");
        Files.createDirectories(sourceB.getParent());
        Files.write(sourceB, content);

        NativeArtifactFetcher.Resolved resolvedA = NativeArtifactFetcher.resolve(
                "a", new ProjectConfig.NativeDependency(sourceA.toUri().toString(), sha));
        NativeArtifactFetcher.Resolved resolvedB = NativeArtifactFetcher.resolve(
                "b", new ProjectConfig.NativeDependency(sourceB.toUri().toString(), sha));

        assertEquals(resolvedA.jarPath(), resolvedB.jarPath());
    }

    @Test
    void expectedPathMatchesResolvedPathWithoutFetching() throws Exception {
        byte[] content = "expected-path-check".getBytes();
        String sha = sha256Of(content);
        ProjectConfig.NativeDependency dep
                = new ProjectConfig.NativeDependency("https://example.com/some/dir/mylib.jar", sha);

        assertEquals(NativeArtifactFetcher.artifactDir(sha).resolve("mylib.jar"),
                NativeArtifactFetcher.expectedPath(dep));
    }

    @Test
    void fileUrlWithoutHashResolvesDirectlyWithoutCopying() throws Exception {
        Path source = tmp.resolve("unverified.jar");
        Files.writeString(source, "live-content-v1");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("unverified", dep);

        assertEquals(source, resolved.jarPath());
        assertFalse(resolved.jarPath().startsWith(NativeArtifactFetcher.root()),
                "an unverified file:// dependency must not be copied into the content-addressed cache");
    }

    @Test
    void fileUrlWithoutHashReflectsLiveChangesOnEachResolve() throws Exception {
        Path source = tmp.resolve("live.jar");
        Files.writeString(source, "version-1");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        NativeArtifactFetcher.resolve("live", dep);
        Files.writeString(source, "version-2");
        NativeArtifactFetcher.Resolved second = NativeArtifactFetcher.resolve("live", dep);

        assertEquals("version-2", Files.readString(second.jarPath()));
    }

    @Test
    void httpUrlWithoutHashThrows() {
        ProjectConfig.NativeDependency dep
                = new ProjectConfig.NativeDependency("https://example.com/raylib.jar", null);

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve("dep", dep));
    }

    @Test
    void nonExistentFileUrlWithoutHashThrows() {
        Path missing = tmp.resolve("does-not-exist.jar");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(missing.toUri().toString(), null);

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve("dep", dep));
    }

    @Test
    void expectedPathForUnhashedFileUrlIsTheSourceItself() throws Exception {
        Path source = tmp.resolve("expected.jar");
        Files.writeString(source, "x");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        assertEquals(source, NativeArtifactFetcher.expectedPath(dep));
    }
}
