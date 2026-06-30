package com.mira.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ProjectConfig(
        String name,
        String version,
        Path entry,
        String description,
        List<String> authors,
        BuildConfig build,
        TestConfig test,
        Map<String, Dependency> dependencies,
        Map<String, TaskConfig> tasks,
        Path projectRoot
        ) {

    public record BuildConfig(
            Path outputDir, BuildMode mode, BuildMode runMode,
            boolean main, String[] args, JarBundle jarBundle,
            List<String> preBuild, List<String> postBuild,
            List<String> preRun, List<String> postRun) {

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

    public record Dependency(Path path) {

    }

    public record TestConfig(String pattern, List<String> extra, List<String> preTest, List<String> postTest) {

    }

    @SuppressWarnings("unchecked")
    public static ProjectConfig fromMap(Map<String, Object> map, Path projectRoot) {
        checkUnknownKeys("(root)", map, Set.of("project", "build", "test", "dependencies", "tasks"));

        Map<String, Object> project = (Map<String, Object>) map.getOrDefault("project", Map.of());
        Map<String, Object> build = (Map<String, Object>) map.getOrDefault("build", Map.of());
        Map<String, Object> testRaw = (Map<String, Object>) map.get("test");
        Map<String, Object> deps = (Map<String, Object>) map.getOrDefault("dependencies", Map.of());

        checkUnknownKeys("[project]", project, Set.of("name", "version", "entry", "description", "authors"));
        checkUnknownKeys("[build]", build, Set.of("mode", "run-mode", "main", "output", "args", "jar-bundle",
                "pre-build", "post-build", "pre-run", "post-run"));

        String name = (String) project.getOrDefault("name", projectRoot.getFileName().toString());
        String version = (String) project.getOrDefault("version", "0.1.0");
        String entryStr = (String) project.get("entry");
        if (entryStr == null) {
            throw new BuildException("mira.toml: [project] entry is required");
        }
        Path entry = projectRoot.resolve(entryStr).normalize();
        String description = (String) project.getOrDefault("description", "");
        List<String> authors = (List<String>) project.getOrDefault("authors", List.of());

        String modeStr = (String) build.getOrDefault("mode", "interpret");
        BuildMode mode = parseMode(modeStr);

        String runModeStr = (String) build.get("run-mode");
        BuildMode runMode = runModeStr != null ? parseMode(runModeStr) : null;

        String jarBundleStr = (String) build.get("jar-bundle");
        if (mode == BuildMode.PACKAGE && jarBundleStr == null) {
            throw new BuildException(
                    "mira.toml: [build] jar-bundle is required when mode = \"package\" "
                    + "(set jar-bundle = \"slim\" or \"full\")");
        }
        if (mode != BuildMode.PACKAGE && jarBundleStr != null) {
            throw new BuildException(
                    "mira.toml: [build] jar-bundle is only valid when mode = \"package\"");
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

        TestConfig testConfig = null;
        if (testRaw != null) {
            checkUnknownKeys("[test]", testRaw, Set.of("pattern", "extra", "pre-test", "post-test"));
            String pattern = (String) testRaw.getOrDefault("pattern", "**/*_test.mira");
            List<String> extra = (List<String>) testRaw.getOrDefault("extra", List.of());
            testConfig = new TestConfig(pattern, new ArrayList<>(extra),
                    parseHookList(testRaw.get("pre-test")),
                    parseHookList(testRaw.get("post-test")));
        }

        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, Object> dep : deps.entrySet()) {
            if (dep.getValue() instanceof Map<?, ?> depMap) {
                String pathStr = (String) ((Map<?, ?>) depMap).get("path");
                if (pathStr == null) {
                    throw new BuildException("Dependency '" + dep.getKey() + "' must specify a 'path'");
                }
                Path depPath = projectRoot.resolve(pathStr).normalize();
                dependencies.put(dep.getKey(), new Dependency(depPath));
            }
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
                    checkUnknownKeys("[tasks." + taskName + "]",
                            (Map<String, Object>) taskMap, Set.of("cmd", "script", "description"));
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
                name, version, entry, description, authors,
                new BuildConfig(outputDir, mode, runMode, mainFn, argsArr, jarBundle,
                        preBuild, postBuild, preRun, postRun),
                testConfig,
                dependencies,
                tasks,
                projectRoot
        );
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
            case "compile" ->
                BuildMode.COMPILE;
            case "package" ->
                BuildMode.PACKAGE;
            default ->
                BuildMode.INTERPRET;
        };
    }

    private static JarBundle parseJarBundle(String s) {
        return switch (s) {
            case "slim" ->
                JarBundle.SLIM;
            case "full" ->
                JarBundle.FULL;
            default ->
                throw new BuildException(
                        "mira.toml: unknown jar-bundle '" + s + "'. Expected: slim, full");
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
