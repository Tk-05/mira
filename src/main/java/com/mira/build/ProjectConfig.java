package com.mira.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectConfig(
        String name,
        String version,
        Path entry,
        String description,
        List<String> authors,
        BuildConfig build,
        TestConfig test,
        Map<String, Dependency> dependencies,
        Path projectRoot
        ) {

    public record BuildConfig(Path outputDir, BuildMode mode, BuildMode runMode, boolean main, boolean lint, String[] args) {

        public BuildMode effectiveRunMode() {
            return runMode != null ? runMode : mode;
        }
    }

    public enum BuildMode {
        INTERPRET, COMPILE, PACKAGE
    }

    public record Dependency(Path path) {

    }

    public record TestConfig(String pattern, List<String> extra) {

    }

    @SuppressWarnings("unchecked")
    public static ProjectConfig fromMap(Map<String, Object> map, Path projectRoot) {
        Map<String, Object> project = (Map<String, Object>) map.getOrDefault("project", Map.of());
        Map<String, Object> build = (Map<String, Object>) map.getOrDefault("build", Map.of());
        Map<String, Object> testRaw = (Map<String, Object>) map.get("test");
        Map<String, Object> deps = (Map<String, Object>) map.getOrDefault("dependencies", Map.of());

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

        boolean mainFn = toBoolean(build.getOrDefault("main", false));
        boolean lint = toBoolean(build.getOrDefault("lint", false));
        String outputStr = (String) build.getOrDefault("output", "out");
        Path outputDir = projectRoot.resolve(outputStr).normalize();
        List<String> argsList = (List<String>) build.getOrDefault("args", List.of());
        String[] argsArr = argsList.toArray(new String[0]);

        TestConfig testConfig = null;
        if (testRaw != null) {
            String pattern = (String) testRaw.getOrDefault("pattern", "**/*_test.mira");
            List<String> extra = (List<String>) testRaw.getOrDefault("extra", List.of());
            testConfig = new TestConfig(pattern, new ArrayList<>(extra));
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

        return new ProjectConfig(
                name, version, entry, description, authors,
                new BuildConfig(outputDir, mode, runMode, mainFn, lint, argsArr),
                testConfig,
                dependencies,
                projectRoot
        );
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

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
