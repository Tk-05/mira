package com.mira.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public class ProjectLoader {

    public static Optional<ProjectConfig> find() {
        return find(Paths.get("").toAbsolutePath());
    }

    public static Optional<ProjectConfig> find(Path startDir) {
        Path dir = startDir.toAbsolutePath().normalize();
        while (dir != null) {
            Path toml = dir.resolve("mira.toml");
            if (Files.exists(toml)) {
                return Optional.of(load(toml));
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    public static ProjectConfig load(Path tomlPath) {
        try {
            String content = Files.readString(tomlPath);
            Map<String, Object> map = TomlParser.parse(content);
            Path root = tomlPath.toAbsolutePath().normalize().getParent();
            return ProjectConfig.fromMap(map, root);
        } catch (IOException e) {
            throw new BuildException("Cannot read " + tomlPath + ": " + e.getMessage());
        }
    }
}
