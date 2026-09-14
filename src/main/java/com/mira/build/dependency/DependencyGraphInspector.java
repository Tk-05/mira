package com.mira.build.dependency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.build.ProjectConfig;
import com.mira.build.ProjectLoader;

/**
 * Builds a read-only, purely diagnostic view of the dependency graph as
 * declared across mira.toml files, reporting for each node whether it's
 * currently available locally (lockfile/cache hit) without fetching or
 * resolving anything. Used by "mira deps".
 *
 * Unlike {@link com.mira.build.DependencyResolver}, which only follows [native]
 * tables one level deep, this walk recurses arbitrarily — as long as a
 * dependency is available (its mira.toml can be read locally), its own declared
 * dependencies are shown too.
 */
public final class DependencyGraphInspector {

    private DependencyGraphInspector() {
    }

    public record DepNode(String name, String kind, String spec, boolean available, String detail,
            List<DepNode> children) {

    }

    public static DepNode buildTree(ProjectConfig config) {
        Set<String> visiting = new LinkedHashSet<>();
        visiting.add(canonicalKey(config.projectRoot()));
        List<DepNode> children = new ArrayList<>();
        appendChildren(config, children, visiting);
        return new DepNode(config.name(), "root", config.version(), true, null, children);
    }

    private static void appendChildren(ProjectConfig config, List<DepNode> out, Set<String> visiting) {
        Map<String, Lockfile.Entry> lock = Lockfile.read(config.projectRoot().resolve("mira.lock"));
        for (Map.Entry<String, ProjectConfig.Dependency> e : config.dependencies().entrySet()) {
            out.add(buildSourceNode(e.getKey(), e.getValue(), lock, visiting));
        }
        for (Map.Entry<String, ProjectConfig.NativeDependency> e : config.nativeDependencies().entrySet()) {
            out.add(buildNativeNode(e.getKey(), e.getValue(), config.projectRoot()));
        }
    }

    private static DepNode buildSourceNode(String name, ProjectConfig.Dependency dep, Map<String, Lockfile.Entry> lock,
            Set<String> visiting) {
        return switch (dep) {
            case ProjectConfig.Dependency.PathDependency pathDep ->
                sourceNode(name, "path", pathDep.path().toString(), pathDep.path(), null, visiting);
            case ProjectConfig.Dependency.GitDependency gitDep -> {
                String spec = gitDep.url() + " @ " + gitRefDescription(gitDep);
                Lockfile.Entry locked = lock.get(name);
                if (locked == null) {
                    yield new DepNode(name, "git", spec, false, "not yet resolved — run 'mira build'", List.of());
                }
                Path root = DependencyCache.checkoutDir(gitDep.url(), locked.commit());
                yield sourceNode(name, "git", spec, root, "locked: " + shortHash(locked.commit()), visiting);
            }
            case ProjectConfig.Dependency.RegistryDependency regDep -> sourceNode(name, "version", regDep.version(),
                    LocalRegistry.installDir(name, regDep.version()), null, visiting);
        };
    }

    private static DepNode sourceNode(String name, String kind, String spec, Path root, String detailIfAvailable,
            Set<String> visiting) {
        Path manifest = root.resolve("mira.toml");
        if (!Files.isDirectory(root) || !Files.exists(manifest)) {
            String reason = Files.isDirectory(root) ? "no mira.toml" : "not found locally";
            return new DepNode(name, kind, spec, false, "missing — " + reason, List.of());
        }

        String canonical = canonicalKey(root);
        if (!visiting.add(canonical)) {
            return new DepNode(name, kind, spec, true, "(cycle)", List.of());
        }
        try {
            ProjectConfig childConfig = ProjectLoader.load(manifest);
            List<DepNode> children = new ArrayList<>();
            appendChildren(childConfig, children, visiting);
            return new DepNode(name, kind, spec, true, detailIfAvailable, children);
        } finally {
            visiting.remove(canonical);
        }
    }

    private static DepNode buildNativeNode(String name, ProjectConfig.NativeDependency dep, Path projectRoot) {
        Path expected = NativeArtifactFetcher.expectedPath(dep, projectRoot);
        boolean available = Files.exists(expected);
        String spec = dep.url()
                + (dep.sha256() != null ? " (sha256 " + shortHash(dep.sha256()) + ")" : " (unverified)");
        return new DepNode(name, "native", spec, available, available ? null : "missing — run 'mira build'", List.of());
    }

    private static String gitRefDescription(ProjectConfig.Dependency.GitDependency dep) {
        if (dep.tag() != null) {
            return "tag " + dep.tag();
        }
        if (dep.branch() != null) {
            return "branch " + dep.branch();
        }
        if (dep.rev() != null) {
            return "rev " + dep.rev();
        }
        return "version " + dep.version();
    }

    private static String canonicalKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String shortHash(String hash) {
        return hash.length() > 10 ? hash.substring(0, 10) : hash;
    }
}
