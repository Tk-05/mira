package com.mira.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public record ProjectConfig(String name, String version, Path entry, String description, List<String> authors,
        BuildConfig build, TestConfig test, Map<String, Dependency> dependencies,
        Map<String, NativeDependency> nativeDependencies, Map<String, TaskConfig> tasks, Path projectRoot) {

    public record BuildConfig(Path outputDir, BuildMode mode, BuildMode runMode, boolean main, String[] args,
            JarBundle jarBundle, List<String> preBuild, List<String> postBuild, List<String> preRun,
            List<String> postRun, boolean strictTypes) {

        public BuildMode effectiveRunMode() {
            return runMode != null ? runMode : mode;
        }
    }

    public enum BuildMode {
        INTERPRET, COMPILE, PACKAGE
    }

    public enum JarBundle {
        SLIM, FULL
    }

    public sealed interface Dependency
            permits Dependency.PathDependency, Dependency.GitDependency, Dependency.RegistryDependency {

        record PathDependency(Path path) implements Dependency {

        }

        /**
         * Exactly one of tag/branch/rev/version is set, chosen by whichever field was
         * present in mira.toml. version is a semver constraint (e.g. "^1.2.0") matched
         * against the repo's tags at resolve time.
         */
        record GitDependency(String url, String tag, String branch, String rev, String version) implements Dependency {

        }

        /**
         * Resolved from the local install cache (~/.mira/packages/local/&lt;dependency
         * name&gt;/&lt;version&gt;) populated by running "mira install" inside the
         * dependency's own project directory — the local equivalent of Maven's "mvn
         * install" into ~/.m2/repository. No path or git URL needed.
         */
        record RegistryDependency(String version) implements Dependency {

        }
    }

    public record TestConfig(String pattern, List<String> extra, List<String> preTest, List<String> postTest) {

    }

    /**
     * A [native.name] entry: a JVM jar (implementing com.mira.lib.Lib), fetched
     * from `url` and verified against `sha256`, resolvable at runtime via a bare
     * `import native "<basename of url>"`. Content-addressed by sha256 — unlike git
     * dependencies, nothing here is a mutable ref, so no lockfile pin is needed.
     *
     * sha256 may be null only when url is a file:// URL: there's no integrity
     * concern fetching a file already on the local machine, and skipping the hash
     * means a local build (e.g. of extern/raylib) is picked up live on every
     * resolve instead of being cached/pinned to whatever content existed the first
     * time it was resolved. http(s):// URLs always require sha256.
     */
    public record NativeDependency(String url, String sha256) {

    }

    @SuppressWarnings("unchecked")
    public static ProjectConfig fromMap(Map<String, Object> map, Path projectRoot) {
        checkUnknownKeys("(root)", map, Set.of("project", "build", "test", "dependencies", "native", "tasks"));

        Map<String, Object> project = (Map<String, Object>) map.getOrDefault("project", Map.of());
        Map<String, Object> build = (Map<String, Object>) map.getOrDefault("build", Map.of());
        Map<String, Object> testRaw = (Map<String, Object>) map.get("test");
        Map<String, Object> deps = (Map<String, Object>) map.getOrDefault("dependencies", Map.of());

        checkUnknownKeys("[project]", project, Set.of("name", "version", "entry", "description", "authors"));
        checkUnknownKeys("[build]", build, Set.of("mode", "run-mode", "main", "output", "args", "jar-bundle",
                "pre-build", "post-build", "pre-run", "post-run", "strict-types"));

        String name = (String) project.getOrDefault("name", projectRoot.getFileName().toString());
        String version = (String) project.getOrDefault("version", "0.1.0");
        String entryStr = (String) project.get("entry");
        // entry is optional at the manifest level (e.g. a native-only package like
        // extern/raylib
        // has no entry point of its own) but is required to actually build/run/test a
        // project —
        // see BuildContext.applyFlags, which is where that's enforced.
        Path entry = entryStr != null ? projectRoot.resolve(entryStr).normalize() : null;
        String description = (String) project.getOrDefault("description", "");
        List<String> authors = (List<String>) project.getOrDefault("authors", List.of());

        String modeStr = (String) build.getOrDefault("mode", "interpret");
        BuildMode mode = parseMode(modeStr);

        String runModeStr = (String) build.get("run-mode");
        BuildMode runMode = runModeStr != null ? parseMode(runModeStr) : null;

        String jarBundleStr = (String) build.get("jar-bundle");
        if (mode == BuildMode.PACKAGE && jarBundleStr == null) {
            throw new BuildException("mira.toml: [build] jar-bundle is required when mode = \"package\" "
                    + "(set jar-bundle = \"slim\" or \"full\")");
        }
        if (mode != BuildMode.PACKAGE && jarBundleStr != null) {
            throw new BuildException("mira.toml: [build] jar-bundle is only valid when mode = \"package\"");
        }
        JarBundle jarBundle = jarBundleStr != null ? parseJarBundle(jarBundleStr) : null;

        boolean mainFn = toBoolean(build.getOrDefault("main", false));
        String outputStr = (String) build.getOrDefault("output", "out");
        Path outputDir = projectRoot.resolve(outputStr).normalize();
        List<String> argsList = (List<String>) build.getOrDefault("args", List.of());
        String[] argsArr = argsList.toArray(new String[0]);

        List<String> preBuild = parseHookList(build.get("pre-build"));
        List<String> postBuild = parseHookList(build.get("post-build"));
        List<String> preRun = parseHookList(build.get("pre-run"));
        List<String> postRun = parseHookList(build.get("post-run"));
        boolean strictTypes = toBoolean(build.getOrDefault("strict-types", false));

        TestConfig testConfig = null;
        if (testRaw != null) {
            checkUnknownKeys("[test]", testRaw, Set.of("pattern", "extra", "pre-test", "post-test"));
            String pattern = (String) testRaw.getOrDefault("pattern", "**/*_test.mira");
            List<String> extra = (List<String>) testRaw.getOrDefault("extra", List.of());
            testConfig = new TestConfig(pattern, new ArrayList<>(extra), parseHookList(testRaw.get("pre-test")),
                    parseHookList(testRaw.get("post-test")));
        }

        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, Object> dep : deps.entrySet()) {
            String depName = dep.getKey();
            if (!(dep.getValue() instanceof Map<?, ?> depMapRaw)) {
                throw new BuildException(
                        "Dependency '" + depName + "' must be a table, e.g. { path = \"...\" } or { git = \"...\" }");
            }

            var depMap = (Map<String, Object>) depMapRaw;
            checkUnknownKeys("[dependencies." + depName + "]", depMap,
                    Set.of("path", "git", "tag", "branch", "rev", "version"));

            String pathStr = (String) depMap.get("path");
            String gitUrl = (String) depMap.get("git");
            long sourceKinds = Stream.of(pathStr, gitUrl).filter(java.util.Objects::nonNull).count();
            if (sourceKinds > 1) {
                throw new BuildException("Dependency '" + depName + "' must specify only one of 'path' or 'git'");
            }
            if (pathStr != null) {
                Path depPath = projectRoot.resolve(pathStr).normalize();
                dependencies.put(depName, new Dependency.PathDependency(depPath));
            } else if (gitUrl != null) {
                String tag = (String) depMap.get("tag");
                String branch = (String) depMap.get("branch");
                String rev = (String) depMap.get("rev");
                String versionConstraint = (String) depMap.get("version");
                long pins = Stream.of(tag, branch, rev, versionConstraint).filter(java.util.Objects::nonNull).count();
                if (pins != 1) {
                    throw new BuildException("Dependency '" + depName
                            + "': specify exactly one of 'tag', 'branch', 'rev', or 'version'");
                }
                dependencies.put(depName, new Dependency.GitDependency(gitUrl, tag, branch, rev, versionConstraint));
            } else if (depMap.get("version") != null) {
                // No 'path' or 'git': resolved from the local install cache by name + version,
                // like a Maven coordinate lookup against ~/.m2/repository. See "mira install".
                dependencies.put(depName, new Dependency.RegistryDependency((String) depMap.get("version")));
            } else {
                throw new BuildException("Dependency '" + depName
                        + "' must specify 'path', 'git', or a bare 'version' (resolved via 'mira install')");
            }
        }

        Map<String, Object> nativeRaw = (Map<String, Object>) map.getOrDefault("native", Map.of());
        Map<String, NativeDependency> nativeDependencies = new LinkedHashMap<>();
        for (Map.Entry<String, Object> nd : nativeRaw.entrySet()) {
            String ndName = nd.getKey();
            if (!(nd.getValue() instanceof Map<?, ?> ndMapRaw)) {
                throw new BuildException(
                        "Native dependency '" + ndName + "' must be a table, e.g. { url = \"...\", sha256 = \"...\" }");
            }

            var ndMap = (Map<String, Object>) ndMapRaw;
            checkUnknownKeys("[native." + ndName + "]", ndMap, Set.of("url", "sha256"));
            String ndUrl = (String) ndMap.get("url");
            String sha256 = (String) ndMap.get("sha256");
            if (ndUrl == null) {
                throw new BuildException("Native dependency '" + ndName + "' must specify a 'url'");
            }
            boolean isFileUrl = "file".equalsIgnoreCase(java.net.URI.create(ndUrl).getScheme());
            if (sha256 == null && !isFileUrl) {
                throw new BuildException(
                        "Native dependency '" + ndName + "': 'sha256' is required unless 'url' is a file:// URL");
            }
            if (sha256 != null && !sha256.matches("(?i)[0-9a-f]{64}")) {
                throw new BuildException("Native dependency '" + ndName
                        + "': sha256 must be a 64-character hex string, got '" + sha256 + "'");
            }
            nativeDependencies.put(ndName,
                    new NativeDependency(ndUrl, sha256 != null ? sha256.toLowerCase(java.util.Locale.ROOT) : null));
        }

        Map<String, Object> tasksRaw = (Map<String, Object>) map.getOrDefault("tasks", Map.of());
        Map<String, TaskConfig> tasks = new LinkedHashMap<>();
        for (Map.Entry<String, Object> t : tasksRaw.entrySet()) {
            String taskName = t.getKey();
            switch (t.getValue()) {
                case String shorthand -> {
                    if (shorthand.endsWith(".mira")) {
                        tasks.put(taskName, new TaskConfig(taskName, null, shorthand, null));
                    } else {
                        tasks.put(taskName, new TaskConfig(taskName, shorthand, null, null));
                    }
                }
                case Map<?, ?> taskMap -> {
                    checkUnknownKeys("[tasks." + taskName + "]", (Map<String, Object>) taskMap,
                            Set.of("cmd", "script", "description"));
                    String cmd = (String) ((Map<?, ?>) taskMap).get("cmd");
                    String script = (String) ((Map<?, ?>) taskMap).get("script");
                    String taskDesc = (String) ((Map<?, ?>) taskMap).get("description");
                    tasks.put(taskName, new TaskConfig(taskName, cmd, script, taskDesc));
                }
                default -> {
                }
            }
        }

        return new ProjectConfig(
                name, version, entry, description, authors, new BuildConfig(outputDir, mode, runMode, mainFn, argsArr,
                        jarBundle, preBuild, postBuild, preRun, postRun, strictTypes),
                testConfig, dependencies, nativeDependencies, tasks, projectRoot);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseHookList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List) {
            return List.copyOf((List<String>) value);
        }
        if (value instanceof String s) {
            return s.isBlank() ? List.of() : List.of(s);
        }
        return List.of();
    }

    private static BuildMode parseMode(String s) {
        return switch (s) {
            case "compile" -> BuildMode.COMPILE;
            case "package" -> BuildMode.PACKAGE;
            default -> BuildMode.INTERPRET;
        };
    }

    private static JarBundle parseJarBundle(String s) {
        return switch (s) {
            case "slim" -> JarBundle.SLIM;
            case "full" -> JarBundle.FULL;
            default -> throw new BuildException("mira.toml: unknown jar-bundle '" + s + "'. Expected: slim, full");
        };
    }

    private static void checkUnknownKeys(String section, Map<String, Object> map, Set<String> known) {
        for (String key : map.keySet()) {
            if (!known.contains(key)) {
                throw new BuildException("mira.toml: unknown field '" + key + "' in " + section);
            }
        }
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
