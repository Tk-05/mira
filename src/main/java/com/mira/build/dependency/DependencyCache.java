package com.mira.build.dependency;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Layout for the central, cross-project git-dependency cache under
 * {@code ~/.mira/packages}. One directory per (repo URL, resolved commit),
 * shared by every local Mira project so the same commit is never fetched twice.
 */
public final class DependencyCache {

    private DependencyCache() {
    }

    public static Path root() {
        return Paths.get(System.getProperty("user.home"), ".mira", "packages");
    }

    public static Path repoDir(String url) {
        return root().resolve(sanitize(url));
    }

    public static Path checkoutDir(String url, String commitSha) {
        return repoDir(url).resolve(commitSha);
    }

    private static String sanitize(String url) {
        String cleaned = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        // Strip any leading slashes left behind (e.g. "file:///C:/foo" -> "/C:/foo" -> "C:/foo"):
        // a leading slash makes java.nio.file.Path treat it as rooted on the current drive on
        // Windows, which silently discards the ~/.mira/packages prefix when resolved against it.
        cleaned = cleaned.replaceFirst("^/+", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._/-]", "_");
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        return cleaned;
    }
}
