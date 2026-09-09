package com.mira.build.dependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

import com.mira.build.BuildException;
import com.mira.build.ProjectConfig.Dependency.GitDependency;
import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;

/**
 * Fetches a git dependency into the shared local cache
 * ({@link DependencyCache}), resolving "tag" / "branch" / "rev" / "version"
 * from mira.toml into an exact commit.
 *
 * Known limitations (MVP scope): only URLs JGit's built-in transports support
 * out of the box (http/https, and file:// for local test fixtures) are fetched;
 * SSH remotes need an extra JGit SSH transport dependency that isn't wired up
 * yet. Directory moves into the cache are best-effort race-safe (last writer
 * for a given commit wins, content is identical either way since the cache key
 * is the commit sha), not protected by a cross-process file lock.
 */
public final class GitDependencyFetcher {

    private GitDependencyFetcher() {
    }

    public record Resolved(Path localPath, String commitSha, String resolvedRef) {

    }

    public static Resolved resolve(String depName, GitDependency dep, Lockfile.Entry lockEntry, boolean forceUpdate) {
        String url = dep.url();

        if (!forceUpdate && lockEntry != null && lockEntry.url().equals(url)) {
            Path cached = DependencyCache.checkoutDir(url, lockEntry.commit());
            if (isValidCheckout(cached)) {
                if (Flags.verbose) {
                    System.out.println(DiagnosticFormatter
                            .formatInfo(depName + ": using cached checkout (" + lockEntry.commit() + ")"));
                }
                return new Resolved(cached, lockEntry.commit(), lockEntry.resolved());
            }
            if (Flags.verbose) {
                System.out.println(DiagnosticFormatter
                        .formatInfo(depName + ": fetching " + url + " (commit " + lockEntry.commit() + ")..."));
            }
            Path finalDir = cloneAtRev(depName, url, lockEntry.commit());
            return new Resolved(finalDir, finalDir.getFileName().toString(), lockEntry.resolved());
        }

        if (dep.rev() != null) {
            if (Flags.verbose) {
                System.out.println(
                        DiagnosticFormatter.formatInfo(depName + ": fetching " + url + " (rev " + dep.rev() + ")..."));
            }
            Path finalDir = cloneAtRev(depName, url, dep.rev());
            return new Resolved(finalDir, finalDir.getFileName().toString(), dep.rev());
        }

        String refName = dep.tag() != null
                ? dep.tag()
                : dep.branch() != null ? dep.branch() : resolveVersionTag(depName, url, dep.version());

        if (Flags.verbose) {
            System.out.println(
                    DiagnosticFormatter.formatInfo(depName + ": fetching " + url + " (ref '" + refName + "')..."));
        }

        Path tempDir = createTempCloneDir(depName);
        String sha;
        try (Git git = Git.cloneRepository().setURI(url).setDirectory(tempDir.toFile()).setBranch(refName).setDepth(1)
                .call()) {
            // Resolve HEAD (and close the JGit handles via try-with-resources) before
            // touching the
            // cache: on Windows, moving/deleting the checkout while JGit still has files
            // open fails.
            sha = git.getRepository().resolve("HEAD").getName();
        } catch (GitAPIException | IOException e) {
            deleteQuietly(tempDir);
            throw new BuildException("Dependency '" + depName + "': failed to fetch " + url + " (ref '" + refName
                    + "'): " + e.getMessage());
        }
        try {
            requireManifest(depName, tempDir);
            Path finalDir = DependencyCache.checkoutDir(url, sha);
            moveIntoCache(tempDir, finalDir);
            return new Resolved(finalDir, sha, refName);
        } catch (IOException e) {
            deleteQuietly(tempDir);
            throw new BuildException("Dependency '" + depName + "': failed to fetch " + url + " (ref '" + refName
                    + "'): " + e.getMessage());
        }
    }

    private static String resolveVersionTag(String depName, String url, String constraint) {
        Collection<Ref> refs;
        try {
            refs = Git.lsRemoteRepository().setRemote(url).setTags(true).setHeads(false).call();
        } catch (GitAPIException e) {
            throw new BuildException(
                    "Dependency '" + depName + "': failed to list tags for " + url + ": " + e.getMessage());
        }

        SemVer best = null;
        String bestTag = null;
        for (Ref ref : refs) {
            String name = ref.getName();
            if (!name.startsWith("refs/tags/") || name.endsWith("^{}")) {
                continue;
            }
            String tagName = name.substring("refs/tags/".length());
            Optional<SemVer> parsed = SemVer.parse(tagName);
            if (parsed.isEmpty() || !SemVer.satisfies(parsed.get(), constraint)) {
                continue;
            }
            if (best == null || parsed.get().compareTo(best) > 0) {
                best = parsed.get();
                bestTag = tagName;
            }
        }
        if (bestTag == null) {
            throw new BuildException("Dependency '" + depName + "': no tag on " + url + " matches version constraint '"
                    + constraint + "'");
        }
        return bestTag;
    }

    private static Path cloneAtRev(String depName, String url, String rev) {
        Path tempDir = createTempCloneDir(depName);
        String sha;
        try (Git git = Git.cloneRepository().setURI(url).setDirectory(tempDir.toFile()).call()) {
            git.checkout().setName(rev).call();
            sha = git.getRepository().resolve("HEAD").getName();
        } catch (GitAPIException | IOException e) {
            deleteQuietly(tempDir);
            throw new BuildException("Dependency '" + depName + "': failed to fetch " + url + " at rev '" + rev + "': "
                    + e.getMessage());
        }
        try {
            requireManifest(depName, tempDir);
            Path finalDir = DependencyCache.checkoutDir(url, sha);
            moveIntoCache(tempDir, finalDir);
            return finalDir;
        } catch (IOException e) {
            deleteQuietly(tempDir);
            throw new BuildException("Dependency '" + depName + "': failed to fetch " + url + " at rev '" + rev + "': "
                    + e.getMessage());
        }
    }

    private static boolean isValidCheckout(Path dir) {
        return Files.isDirectory(dir) && Files.exists(dir.resolve("mira.toml"));
    }

    private static void requireManifest(String depName, Path dir) {
        if (!Files.exists(dir.resolve("mira.toml"))) {
            deleteQuietly(dir);
            throw new BuildException("Dependency '" + depName + "': repository has no mira.toml at its root");
        }
    }

    private static Path createTempCloneDir(String depName) {
        try {
            Path base = DependencyCache.root().resolve("_tmp");
            Files.createDirectories(base);
            return Files.createTempDirectory(base, depName + "-");
        } catch (IOException e) {
            throw new BuildException(
                    "Dependency '" + depName + "': failed to create temp directory: " + e.getMessage());
        }
    }

    private static void moveIntoCache(Path tempDir, Path finalDir) throws IOException {
        if (Files.exists(finalDir)) {
            deleteQuietly(tempDir);
            return;
        }
        Files.createDirectories(finalDir.getParent());
        try {
            Files.move(tempDir, finalDir);
        } catch (IOException e) {
            if (Files.exists(finalDir)) {
                deleteQuietly(tempDir);
            } else {
                throw e;
            }
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
