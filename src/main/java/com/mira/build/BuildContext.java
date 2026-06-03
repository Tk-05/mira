package com.mira.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mira.Flags;

public class BuildContext {

    private final ProjectConfig config;
    private final List<Path> depRoots;

    public BuildContext(ProjectConfig config, List<Path> depRoots) {
        this.config = config;
        this.depRoots = depRoots;
    }

    public ProjectConfig config() {
        return config;
    }

    public List<Path> depRoots() {
        return depRoots;
    }

    public void applyFlags(ProjectConfig.BuildMode modeOverride) {
        ProjectConfig.BuildConfig bc = config.build();
        ProjectConfig.BuildMode mode = modeOverride != null ? modeOverride : bc.mode();

        Flags.inputPath.set(config.entry());
        Flags.mainFunction = bc.main();
        Flags.args = bc.args().length > 0 ? bc.args() : null;
        Flags.dependencyRoots = new ArrayList<>(depRoots);

        Flags.testMode = false;
        Flags.hotReload = false;
        Flags.dumpTokens = false;
        Flags.printAsts = false;
        Flags.debug = false;
        Flags.compileAndRun = false;

        Flags.compile = mode == ProjectConfig.BuildMode.COMPILE || mode == ProjectConfig.BuildMode.PACKAGE;
        Flags.packageJar = mode == ProjectConfig.BuildMode.PACKAGE;
        Flags.outputDir = Flags.compile ? bc.outputDir() : null;
    }
}
