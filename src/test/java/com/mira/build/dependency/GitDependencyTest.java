package com.mira.build.dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.build.DependencyResolver;
import com.mira.build.ProjectConfig;
import com.mira.build.ProjectLoader;

/**
 * End-to-end coverage for git dependencies, using a local repo (via a file://
 * URL) as a stand-in for a real remote so tests don't need network access.
 */
public class GitDependencyTest {

    @TempDir
    Path tmp;

    private Path createUpstreamRepo() throws Exception {
        Path repoDir = Files.createDirectory(tmp.resolve("upstream"));
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            writeUpstreamVersion(repoDir, "1.0.0");
            git.add().addFilepattern(".").call();
            commit(git, "1.0.0");
            git.tag().setName("v1.0.0").call();

            writeUpstreamVersion(repoDir, "1.2.0");
            git.add().addFilepattern(".").call();
            commit(git, "1.2.0");
            git.tag().setName("v1.2.0").call();
        }
        return repoDir;
    }

    private void commit(Git git, String msg) throws Exception {
        git.commit().setMessage(msg).setSign(false).setAuthor("Test", "test@example.com")
                .setCommitter("Test", "test@example.com").call();
    }

    private void writeUpstreamVersion(Path repoDir, String version) throws Exception {
        Files.writeString(repoDir.resolve("mira.toml"),
                "[project]\nname = \"upstream\"\nversion = \"" + version + "\"\nentry = \"lib.mira\"\n");
        Files.writeString(repoDir.resolve("lib.mira"),
                "module upstream;\npub fn greet() { print(\"hi from " + version + "\"); }\n");
    }

    private Path writeConsumerProject(String depSpec) throws Exception {
        Path consumerDir = Files.createDirectory(tmp.resolve("consumer"));
        Files.writeString(consumerDir.resolve("mira.toml"),
                "[project]\nname = \"app\"\nentry = \"main.mira\"\n" + "[dependencies]\nupstream = " + depSpec + "\n");
        Files.createFile(consumerDir.resolve("main.mira"));
        return consumerDir;
    }

    @Test
    void resolvesVersionConstraintToHighestMatchingTag() throws Exception {
        Path upstream = createUpstreamRepo();
        String url = upstream.toUri().toString();
        Path consumerDir = writeConsumerProject("{ git = \"" + url + "\", version = \"^1.0\" }");

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        List<Path> roots = DependencyResolver.resolve(cfg).sourceRoots();

        assertEquals(1, roots.size());
        String resolvedToml = Files.readString(roots.get(0).resolve("mira.toml"));
        assertTrue(resolvedToml.contains("version = \"1.2.0\""), "expected the ^1.0 constraint to pick 1.2.0");
    }

    @Test
    void resolvesExplicitTagRegardlessOfLaterTags() throws Exception {
        Path upstream = createUpstreamRepo();
        String url = upstream.toUri().toString();
        Path consumerDir = writeConsumerProject("{ git = \"" + url + "\", tag = \"v1.0.0\" }");

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        List<Path> roots = DependencyResolver.resolve(cfg).sourceRoots();

        String resolvedToml = Files.readString(roots.get(0).resolve("mira.toml"));
        assertTrue(resolvedToml.contains("version = \"1.0.0\""));
    }

    @Test
    void secondResolveReusesLockedCommitWithoutTheRemote() throws Exception {
        Path upstream = createUpstreamRepo();
        String url = upstream.toUri().toString();
        Path consumerDir = writeConsumerProject("{ git = \"" + url + "\", version = \"^1.0\" }");

        ProjectConfig cfg = ProjectLoader.load(consumerDir.resolve("mira.toml"));
        List<Path> firstRoots = DependencyResolver.resolve(cfg).sourceRoots();
        assertTrue(Files.exists(consumerDir.resolve("mira.lock")), "mira.lock should be written after resolve");

        deleteRecursively(upstream);

        List<Path> secondRoots = DependencyResolver.resolve(cfg).sourceRoots();
        assertEquals(firstRoots, secondRoots);
        assertTrue(Files.exists(secondRoots.get(0).resolve("mira.toml")));
    }

    private void deleteRecursively(Path dir) throws Exception {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
        }
    }
}
