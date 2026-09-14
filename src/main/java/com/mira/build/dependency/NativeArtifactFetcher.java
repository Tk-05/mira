package com.mira.build.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * }} in mira.toml) into a shared cache. Unlike {@link GitDependencyFetcher},
 * there is no lockfile pin - when sha256 is declared, it already fully
 * determines the cache path, so a cache hit is verification by construction and
 * there is nothing "mutable" to re-resolve (no forceUpdate parameter).
 *
 * sha256 is optional (see {@link ProjectConfig.NativeDependency}): a
 * {@code file://} URL always resolves straight to the source file, live, with
 * no cache and nothing to verify (see {@link #resolveUnverifiedFileUrl}); an
 * http(s):// URL without sha256 is still cached, just keyed by the URL itself
 * rather than by content, and never checked against anything (see
 * {@link #resolveUnverifiedRemoteUrl}) - trading integrity verification for not
 * having to pre-compute a hash just to declare the dependency.
 */
public final class NativeArtifactFetcher {

    private NativeArtifactFetcher() {
    }

    public record Resolved(Path jarPath) {

    }

    public static Path root() {
        return DependencyCache.root().resolve("native");
    }

    public static Path artifactDir(String key) {
        return root().resolve(key.toLowerCase(Locale.ROOT));
    }

    /**
     * The path a resolve() call would produce, without fetching anything — used
     * by "mira deps".
     */
    public static Path expectedPath(ProjectConfig.NativeDependency dep) {
        if (dep.sha256() != null) {
            return artifactDir(dep.sha256()).resolve(basenameFromUrl(dep.url()));
        }
        if (isFileUrl(dep.url())) {
            return Paths.get(URI.create(dep.url()));
        }
        return artifactDir(urlCacheKey(dep.url())).resolve(basenameFromUrl(dep.url()));
    }

    public static Resolved resolve(String depName, ProjectConfig.NativeDependency dep) {
        if (dep.sha256() != null) {
            return resolveVerified(depName, dep.url(), dep.sha256().toLowerCase(Locale.ROOT));
        }
        if (isFileUrl(dep.url())) {
            return resolveUnverifiedFileUrl(depName, dep.url());
        }
        return resolveUnverifiedRemoteUrl(depName, dep.url());
    }

    private static Resolved resolveVerified(String depName, String url, String sha256) {
        Path destJar = artifactDir(sha256).resolve(basenameFromUrl(url));
        if (Files.exists(destJar)) {
            return new Resolved(destJar);
        }

        Path tempFile = download(depName, url);
        try {
            String actual = sha256Hex(tempFile);
            if (!actual.equalsIgnoreCase(sha256)) {
                deleteQuietly(tempFile);
                throw new BuildException("Native dependency '" + depName + "': sha256 mismatch for " + url
                        + " (expected " + sha256 + ", got " + actual + ")");
            }
            moveIntoCache(tempFile, destJar);
            return new Resolved(destJar);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new BuildException(
                    "Native dependency '" + depName + "': failed to fetch " + url + ": " + e.getMessage());
        }
    }

    private static Resolved resolveUnverifiedRemoteUrl(String depName, String url) {
        Path destJar = artifactDir(urlCacheKey(url)).resolve(basenameFromUrl(url));
        if (Files.exists(destJar)) {
            return new Resolved(destJar);
        }

        Path tempFile = download(depName, url);
        try {
            moveIntoCache(tempFile, destJar);
            return new Resolved(destJar);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new BuildException(
                    "Native dependency '" + depName + "': failed to fetch " + url + ": " + e.getMessage());
        }
    }

    private static Resolved resolveUnverifiedFileUrl(String depName, String url) {
        Path source = Paths.get(URI.create(url));
        if (!Files.exists(source)) {
            throw new BuildException("Native dependency '" + depName + "': file not found: " + source);
        }
        return new Resolved(source);
    }

    private static boolean isFileUrl(String url) {
        return "file".equalsIgnoreCase(URI.create(url).getScheme());
    }

    private static String urlCacheKey(String url) {
        return sha256Hex(url.getBytes(StandardCharsets.UTF_8));
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
        try (InputStream in = Files.newInputStream(file); DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestIn.read(buffer) != -1) {
                // streamed through the digest; contents are discarded
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
