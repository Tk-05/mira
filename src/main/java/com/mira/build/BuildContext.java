package com.mira.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mira.cli.Flags;

public class BuildContext {

    private final ProjectConfig config;
    private final List<Path> depRoots;
    private final List<Path> nativeRoots;

    public BuildContext(ProjectConfig config, List<Path> depRoots) {
        this(config, depRoots, List.of());
    }

    public BuildContext(ProjectConfig config, List<Path> depRoots, List<Path> nativeRoots) {
        this.config = config;
        this.depRoots = depRoots;
        this.nativeRoots = nativeRoots;
    }

    public ProjectConfig config() {
        return config;
    }

    public List<Path> depRoots() {
        return depRoots;
    }

    public List<Path> nativeRoots() {
        return nativeRoots;
    }

    public void applyFlags(ProjectConfig.BuildMode modeOverride) {
        applyFlags(modeOverride, null);
    }

    public void applyFlags(ProjectConfig.BuildMode modeOverride, ProjectConfig.JarBundle jarBundleOverride) {
        ProjectConfig.BuildConfig bc = config.build();
        ProjectConfig.BuildMode mode = modeOverride != null ? modeOverride : bc.mode();

        if (config.entry() == null) {
            throw new BuildException(
                    "mira.toml: [project] entry is required to build, run, or test this project "
                    + "(this package has no entry point of its own — e.g. a native-only package like extern/raylib)");
        }
        if (!Files.exists(config.entry())) {
            throw new BuildException("mira.toml: [project] entry file does not exist: " + config.entry());
        }
        Flags.inputPath.set(config.entry());
        Flags.mainFunction = bc.main();
        Flags.args = bc.args().length > 0 ? bc.args() : null;
        Flags.dependencyRoots = new ArrayList<>(depRoots);
        Flags.nativeRoots = new ArrayList<>(nativeRoots);
        Flags.strictTypes = Flags.strictTypes || bc.strictTypes();

        Flags.testMode = false;
        Flags.hotReload = false;
        Flags.dumpTokens = false;
        Flags.printAsts = false;
        Flags.debug = false;
        Flags.compileAndRun = false;

        Flags.compile = mode == ProjectConfig.BuildMode.COMPILE || mode == ProjectConfig.BuildMode.PACKAGE;
        Flags.packageJar = mode == ProjectConfig.BuildMode.PACKAGE;
        Flags.outputDir = Flags.compile ? bc.outputDir() : null;

        if (Flags.packageJar) {
            ProjectConfig.JarBundle jarBundle = jarBundleOverride != null ? jarBundleOverride : bc.jarBundle();
            if (jarBundle == null) {
                throw new BuildException(
                        "Build mode 'package' requires jar-bundle to be set "
                        + "(mira.toml [build] jar-bundle = \"slim\" or \"full\", or pass --slim/--full)");
            }
            Flags.slimJar = jarBundle == ProjectConfig.JarBundle.SLIM;
        } else {
            Flags.slimJar = false;
        }
    }
}
