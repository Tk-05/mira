package com.mira.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.build.dependency.GitDependencyFetcher;
import com.mira.build.dependency.LocalRegistry;
import com.mira.build.dependency.Lockfile;
import com.mira.build.dependency.NativeArtifactFetcher;
import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;

public class DependencyResolver {

    public record Resolution(List<Path> sourceRoots, List<Path> nativeRoots) {

    }

    public static Resolution resolve(ProjectConfig config) {
        return resolve(config, false);
    }

    /**
     * @param forceUpdate when true, re-resolves "tag"/"branch"/"version" git
     * dependencies against the remote instead of reusing the mira.lock pin
     * (used by a future "mira update" command).
     */
    public static Resolution resolve(ProjectConfig config, boolean forceUpdate) {
        Path lockPath = config.projectRoot().resolve("mira.lock");
        Map<String, Lockfile.Entry> lock = Lockfile.read(lockPath);
        Map<String, Lockfile.Entry> updatedLock = new LinkedHashMap<>();
        List<Path> sourceRoots = new ArrayList<>();
        List<Path> nativeRoots = new ArrayList<>();
        Set<String> seenNativeShas = new LinkedHashSet<>();
        Set<String> visitedProjects = new LinkedHashSet<>();
        visitedProjects.add(canonicalKey(config.projectRoot()));

        resolveNativeTable(config.name(), config.nativeDependencies(), nativeRoots, seenNativeShas);

        for (Map.Entry<String, ProjectConfig.Dependency> entry : config.dependencies().entrySet()) {
            String name = entry.getKey();
            Path depRoot;
            switch (entry.getValue()) {
                case ProjectConfig.Dependency.PathDependency pathDep -> {
                    depRoot = pathDep.path();
                    if (!Files.isDirectory(depRoot)) {
                        throw new BuildException("Dependency '" + name + "': directory not found: " + depRoot);
                    }
                    if (!Files.exists(depRoot.resolve("mira.toml"))) {
                        throw new BuildException("Dependency '" + name + "': no mira.toml in " + depRoot);
                    }
                }
                case ProjectConfig.Dependency.GitDependency gitDep -> {
                    Lockfile.Entry existing = lock.get(name);
                    GitDependencyFetcher.Resolved resolved
                            = GitDependencyFetcher.resolve(name, gitDep, existing, forceUpdate);
                    depRoot = resolved.localPath();
                    updatedLock.put(name, new Lockfile.Entry(
                            name, gitDep.url(), resolved.resolvedRef(), resolved.commitSha()));
                }
                case ProjectConfig.Dependency.RegistryDependency regDep -> {
                    depRoot = LocalRegistry.installDir(name, regDep.version());
                    if (!Files.isDirectory(depRoot) || !Files.exists(depRoot.resolve("mira.toml"))) {
                        throw new BuildException("Dependency '" + name + "' version '" + regDep.version()
                                + "' is not installed locally (looked in " + depRoot + "). "
                                + "Run 'mira install' inside its project directory first.");
                    }
                }
            }
            if (Flags.verbose) {
                String kind = switch (entry.getValue()) {
                    case ProjectConfig.Dependency.PathDependency pd ->
                        "path";
                    case ProjectConfig.Dependency.GitDependency gd ->
                        "git";
                    case ProjectConfig.Dependency.RegistryDependency rd ->
                        "registry";
                };
                System.out.println(DiagnosticFormatter.formatInfo(
                        "dependency '" + name + "': " + kind + " -> " + depRoot));
            }
            sourceRoots.add(depRoot);

            // Native tables are picked up through the *entire* dependency graph, however deep — unlike
            // source dependencies (sourceRoots stays one level: no transitive [dependencies] graph is
            // built), there is no version-unification problem for [native] entries since they're
            // content-addressed by sha256, so recursing arbitrarily deep is safe.
            collectNativeTransitively(name, depRoot, nativeRoots, seenNativeShas, visitedProjects);
        }

        if (!updatedLock.isEmpty() || !lock.isEmpty()) {
            Lockfile.write(lockPath, updatedLock);
        }
        return new Resolution(sourceRoots, nativeRoots);
    }

    /**
     * Walks depRoot's own mira.toml, collecting its [native] table, then
     * recurses into each of ITS dependencies the same way. Cycle-safe via
     * visitedProjects. Best-effort: a nested dependency that can't be resolved
     * (missing directory, not installed, unreachable git remote) is silently
     * skipped rather than failing the whole build — it's unrelated to the
     * dependency actually being built, we're only searching for [native] tables
     * that might be further down the graph. Nested git dependencies found this
     * way are re-resolved fresh each time (no lockfile pin): a transitive
     * dependency's ref should be pinned by its own project's mira.lock, not by
     * whichever downstream project reaches it.
     */
    private static void collectNativeTransitively(String ownerName, Path depRoot, List<Path> nativeRoots,
            Set<String> seenShas, Set<String> visitedProjects) {
        Path depToml = depRoot.resolve("mira.toml");
        if (!Files.exists(depToml)) {
            return;
        }
        if (!visitedProjects.add(canonicalKey(depRoot))) {
            return;
        }

        ProjectConfig depConfig = ProjectLoader.load(depToml);
        resolveNativeTable(ownerName, depConfig.nativeDependencies(), nativeRoots, seenShas);

        for (Map.Entry<String, ProjectConfig.Dependency> nested : depConfig.dependencies().entrySet()) {
            Path nestedRoot;
            try {
                nestedRoot = resolveNestedDepRootBestEffort(nested.getKey(), nested.getValue());
            } catch (BuildException e) {
                continue;
            }
            collectNativeTransitively(ownerName + "." + nested.getKey(), nestedRoot, nativeRoots, seenShas,
                    visitedProjects);
        }
    }

    private static Path resolveNestedDepRootBestEffort(String name, ProjectConfig.Dependency dep) {
        return switch (dep) {
            case ProjectConfig.Dependency.PathDependency pathDep -> {
                if (!Files.isDirectory(pathDep.path())) {
                    throw new BuildException("Dependency '" + name + "': directory not found: " + pathDep.path());
                }
                yield pathDep.path();
            }
            case ProjectConfig.Dependency.GitDependency gitDep ->
                GitDependencyFetcher.resolve(name, gitDep, null, false).localPath();
            case ProjectConfig.Dependency.RegistryDependency regDep -> {
                Path depRoot = LocalRegistry.installDir(name, regDep.version());
                if (!Files.isDirectory(depRoot)) {
                    throw new BuildException("Dependency '" + name + "' version '" + regDep.version()
                            + "' is not installed locally");
                }
                yield depRoot;
            }
        };
    }

    private static void resolveNativeTable(String ownerName, Map<String, ProjectConfig.NativeDependency> natives,
            List<Path> nativeRoots, Set<String> seenShas) {
        for (Map.Entry<String, ProjectConfig.NativeDependency> e : natives.entrySet()) {
            ProjectConfig.NativeDependency nd = e.getValue();
            // Dedup only applies when there's an actual hash to compare — an unhashed (file://)
            // entry has no shared identity with any other unhashed entry, so every one must resolve.
            if (nd.sha256() != null && !seenShas.add(nd.sha256())) {
                continue;
            }
            NativeArtifactFetcher.Resolved resolved
                    = NativeArtifactFetcher.resolve(ownerName + "." + e.getKey(), nd);
            nativeRoots.add(resolved.jarPath().getParent());
        }
    }

    private static String canonicalKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
