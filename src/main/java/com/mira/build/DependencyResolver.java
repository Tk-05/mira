package com.mira.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DependencyResolver {

    public static List<Path> resolve(ProjectConfig config) {
        List<Path> roots = new ArrayList<>();
        for (Map.Entry<String, ProjectConfig.Dependency> entry : config.dependencies().entrySet()) {
            String name = entry.getKey();
            Path depRoot = entry.getValue().path();
            if (!Files.isDirectory(depRoot)) {
                throw new BuildException("Dependency '" + name + "': directory not found: " + depRoot);
            }
            if (!Files.exists(depRoot.resolve("mira.toml"))) {
                throw new BuildException("Dependency '" + name + "': no mira.toml in " + depRoot);
            }
            roots.add(depRoot);
        }
        return roots;
    }
}
