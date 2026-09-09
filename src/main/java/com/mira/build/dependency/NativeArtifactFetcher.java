package com.mira.build.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import com.mira.build.BuildException;
import com.mira.build.ProjectConfig;

/**
 * Fetches a native JVM jar (declared via {@code [native.name] = { url, sha256
 * }} in mira.toml) into a shared, content-addressed cache. Unlike
 * {@link GitDependencyFetcher}, there is no lockfile pin: the declared sha256
 * already fully determines the cache path, so a cache hit is verification by
 * construction and there is nothing "mutable" to re-resolve (no forceUpdate
 * parameter).
 *
 * Exception: a {@code file://} URL may omit sha256 entirely, since there's no
 * integrity concern fetching a file already on the local machine — see
 * {@link #resolveUnverifiedFileUrl}.
 */
public final class NativeArtifactFetcher {

    private NativeArtifactFetcher() {
    }

    public record Resolved(Path jarPath) {

    }

    public static Path root() {
        return DependencyCache.root().resolve("native");
    }

    public static Path artifactDir(String sha256) {
        return root().resolve(sha256.toLowerCase(Locale.ROOT));
    }

    /**
     * The path a resolve() call would produce, without fetching anything — used by
     * "mira deps".
     */
    public static Path expectedPath(ProjectConfig.NativeDependency dep) {
        if (dep.sha256() == null) {
            return localFileUrlToPath("(unknown)", dep.url());
        }
        return artifactDir(dep.sha256()).resolve(basenameFromUrl(dep.url()));
    }

    public static Resolved resolve(String depName, ProjectConfig.NativeDependency dep) {
        if (dep.sha256() == null) {
            return resolveUnverifiedFileUrl(depName, dep.url());
        }

        String sha256 = dep.sha256().toLowerCase(Locale.ROOT);
        Path destJar = artifactDir(sha256).resolve(basenameFromUrl(dep.url()));

        if (Files.exists(destJar)) {
            return new Resolved(destJar);
        }

        Path tempFile = download(depName, dep.url());
        try {
            String actual = sha256Hex(tempFile);
            if (!actual.equalsIgnoreCase(sha256)) {
                deleteQuietly(tempFile);
                throw new BuildException("Native dependency '" + depName + "': sha256 mismatch for " + dep.url()
                        + " (expected " + sha256 + ", got " + actual + ")");
            }
            moveIntoCache(tempFile, destJar);
            return new Resolved(destJar);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new BuildException(
                    "Native dependency '" + depName + "': failed to fetch " + dep.url() + ": " + e.getMessage());
        }
    }

    /**
     * No sha256 means url must be file:// (enforced at mira.toml parse time,
     * re-checked here for callers that build a NativeDependency directly). Resolves
     * straight to the source file — no copy, no cache — so every "mira build" picks
     * up whatever is on disk right now, e.g. after a fresh `mvn package` of
     * extern/raylib. There's nothing to verify: it's already a local file.
     */
    private static Resolved resolveUnverifiedFileUrl(String depName, String url) {
        Path source = localFileUrlToPath(depName, url);
        if (!Files.exists(source)) {
            throw new BuildException("Native dependency '" + depName + "': file not found: " + source);
        }
        return new Resolved(source);
    }

    private static Path localFileUrlToPath(String depName, String url) {
        URI uri = URI.create(url);
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            throw new BuildException("Native dependency '" + depName
                    + "': sha256 is required unless url is a file:// URL (got " + url + ")");
        }
        return Paths.get(uri);
    }

    private static String basenameFromUrl(String url) {
        String path = URI.create(url).getPath();
        String basename = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        if (basename.isBlank()) {
            throw new BuildException("Native dependency url has no file name: " + url);
        }
        return basename;
    }

    private static Path download(String depName, String url) {
        URI uri = URI.create(url);
        Path tempFile = createTempStagingFile(depName);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                Files.copy(Paths.get(uri), tempFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new BuildException(
                        "Native dependency '" + depName + "': failed to read " + url + ": " + e.getMessage());
            }
            return tempFile;
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() / 100 != 2) {
                throw new BuildException(
                        "Native dependency '" + depName + "': GET " + url + " returned HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new BuildException(
                    "Native dependency '" + depName + "': failed to download " + url + ": " + e.getMessage());
        }
        return tempFile;
    }

    private static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        try (InputStream in = Files.newInputStream(file);
                DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestIn.read(buffer) != -1) {
                // streamed through the digest; contents are discarded
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path createTempStagingFile(String depName) {
        try {
            Path base = DependencyCache.root().resolve("_tmp");
            Files.createDirectories(base);
            return Files.createTempFile(base, depName + "-", ".jar");
        } catch (IOException e) {
            throw new BuildException(
                    "Native dependency '" + depName + "': failed to create temp file: " + e.getMessage());
        }
    }

    private static void moveIntoCache(Path tempFile, Path destJar) throws IOException {
        if (Files.exists(destJar)) {
            deleteQuietly(tempFile);
            return;
        }
        Files.createDirectories(destJar.getParent());
        try {
            Files.move(tempFile, destJar);
        } catch (IOException e) {
            if (Files.exists(destJar)) {
                deleteQuietly(tempFile);
            } else {
                throw e;
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
