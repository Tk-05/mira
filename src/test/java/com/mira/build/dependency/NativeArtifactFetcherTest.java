package com.mira.build.dependency;

import java.net.InetSocketAddress;
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
import com.sun.net.httpserver.HttpServer;

/**
 * Uses file:// URLs against a small local fixture file (not a real jar's worth
 * of bytes — content doesn't matter for these tests, only that it hashes and
 * round-trips correctly). projectRoot is passed as {@code tmp} throughout,
 * except in the tests specifically exercising relative-file-url resolution,
 * where it's the thing under test.
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

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("dummy",
                new ProjectConfig.NativeDependency(source.toUri().toString(), sha), tmp);

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

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve("dummy",
                new ProjectConfig.NativeDependency(source.toUri().toString(), wrongSha), tmp));
        assertFalse(Files.exists(NativeArtifactFetcher.artifactDir(wrongSha)));
    }

    @Test
    void secondResolveIsCacheHitAndSurvivesSourceDeletion() throws Exception {
        byte[] content = "fixture-jar-bytes-2".getBytes();
        Path source = tmp.resolve("dummy2.jar");
        Files.write(source, content);
        String sha = sha256Of(content);
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), sha);

        NativeArtifactFetcher.Resolved first = NativeArtifactFetcher.resolve("dummy2", dep, tmp);
        Files.delete(source);

        NativeArtifactFetcher.Resolved second = NativeArtifactFetcher.resolve("dummy2", dep, tmp);
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

        NativeArtifactFetcher.Resolved resolvedA = NativeArtifactFetcher.resolve("a",
                new ProjectConfig.NativeDependency(sourceA.toUri().toString(), sha), tmp);
        NativeArtifactFetcher.Resolved resolvedB = NativeArtifactFetcher.resolve("b",
                new ProjectConfig.NativeDependency(sourceB.toUri().toString(), sha), tmp);

        assertEquals(resolvedA.jarPath(), resolvedB.jarPath());
    }

    @Test
    void expectedPathMatchesResolvedPathWithoutFetching() throws Exception {
        byte[] content = "expected-path-check".getBytes();
        String sha = sha256Of(content);
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(
                "https://example.com/some/dir/mylib.jar", sha);

        assertEquals(NativeArtifactFetcher.artifactDir(sha).resolve("mylib.jar"),
                NativeArtifactFetcher.expectedPath(dep, tmp));
    }

    @Test
    void fileUrlWithoutHashResolvesDirectlyWithoutCopying() throws Exception {
        Path source = tmp.resolve("unverified.jar");
        Files.writeString(source, "live-content-v1");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("unverified", dep, tmp);

        assertEquals(source, resolved.jarPath());
        assertFalse(resolved.jarPath().startsWith(NativeArtifactFetcher.root()),
                "an unverified file:// dependency must not be copied into the content-addressed cache");
    }

    @Test
    void fileUrlWithoutHashReflectsLiveChangesOnEachResolve() throws Exception {
        Path source = tmp.resolve("live.jar");
        Files.writeString(source, "version-1");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        NativeArtifactFetcher.resolve("live", dep, tmp);
        Files.writeString(source, "version-2");
        NativeArtifactFetcher.Resolved second = NativeArtifactFetcher.resolve("live", dep, tmp);

        assertEquals("version-2", Files.readString(second.jarPath()));
    }

    @Test
    void httpUrlWithoutHashDownloadsAndCachesByUrlNotContent() throws Exception {
        byte[] content = "http-fixture-bytes".getBytes();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lib.jar", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/lib.jar";
            ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(url, null);

            NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("httpdep", dep, tmp);

            assertEquals("http-fixture-bytes", Files.readString(resolved.jarPath()));
            assertEquals(NativeArtifactFetcher.expectedPath(dep, tmp), resolved.jarPath());
            // cache key is derived from the URL string itself, not the downloaded content
            String urlSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes()));
            assertEquals(NativeArtifactFetcher.artifactDir(urlSha).resolve("lib.jar"), resolved.jarPath());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void secondHttpResolveWithoutHashIsCacheHitAndDoesNotNeedTheServer() throws Exception {
        byte[] content = "cached-http-bytes".getBytes();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lib2.jar", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/lib2.jar";
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(url, null);
        NativeArtifactFetcher.Resolved first = NativeArtifactFetcher.resolve("httpdep2", dep, tmp);
        server.stop(0);

        NativeArtifactFetcher.Resolved second = NativeArtifactFetcher.resolve("httpdep2", dep, tmp);

        assertEquals(first.jarPath(), second.jarPath());
        assertEquals("cached-http-bytes", Files.readString(second.jarPath()));
    }

    @Test
    void nonExistentFileUrlWithoutHashThrows() {
        Path missing = tmp.resolve("does-not-exist.jar");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(missing.toUri().toString(), null);

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve("dep", dep, tmp));
    }

    @Test
    void expectedPathForUnhashedFileUrlIsTheSourceItself() throws Exception {
        Path source = tmp.resolve("expected.jar");
        Files.writeString(source, "x");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);

        assertEquals(source, NativeArtifactFetcher.expectedPath(dep, tmp));
    }

    /**
     * The mira-raylib convention: a native jar's own mira.toml points at its own
     * build output with a driveless {@code file:///target/x.jar} URL, meant to be
     * read relative to wherever that project ends up (e.g. after a git-dependency
     * clone) rather than as a literal OS-root path.
     */
    @Test
    void relativeFileUrlResolvesAgainstProjectRoot() throws Exception {
        Path projectRoot = tmp.resolve("cloned-dep");
        Files.createDirectories(projectRoot.resolve("target"));
        Files.writeString(projectRoot.resolve("target").resolve("raylib.jar"), "relative-native-jar");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency("file:///target/raylib.jar", null);

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("raylib", dep, projectRoot);

        assertEquals(projectRoot.resolve("target").resolve("raylib.jar"), resolved.jarPath());
        assertEquals("relative-native-jar", Files.readString(resolved.jarPath()));
        assertEquals(resolved.jarPath(), NativeArtifactFetcher.expectedPath(dep, projectRoot));
    }

    @Test
    void relativeFileUrlWithoutProjectRootThrows() {
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency("file:///target/raylib.jar", null);

        assertThrows(BuildException.class, () -> NativeArtifactFetcher.resolve("raylib", dep, null));
    }

    @Test
    void absoluteFileUrlIgnoresProjectRoot() throws Exception {
        Path source = tmp.resolve("absolute.jar");
        Files.writeString(source, "abs-content");
        ProjectConfig.NativeDependency dep = new ProjectConfig.NativeDependency(source.toUri().toString(), null);
        Path unrelatedRoot = tmp.resolve("unrelated");
        Files.createDirectories(unrelatedRoot);

        NativeArtifactFetcher.Resolved resolved = NativeArtifactFetcher.resolve("abs", dep, unrelatedRoot);

        assertEquals(source, resolved.jarPath());
    }
}
