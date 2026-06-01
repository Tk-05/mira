package com.mira.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.mira.Flags;
import com.mira.Main;

public class Commands {

    public static void init(String[] args) {
        init(args, Paths.get("").toAbsolutePath());
    }

    static void init(String[] args, Path workDir) {
        String name = null;
        for (int i = 1; i < args.length; i++) {
            if ("--name".equals(args[i]) && i + 1 < args.length) {
                name = args[++i];
            }
        }

        Path cwd = workDir.toAbsolutePath();
        if (name == null) {
            name = cwd.getFileName().toString();
        }

        Path tomlPath = cwd.resolve("mira.toml");
        Path mainPath = cwd.resolve("src/main.mira");

        if (Files.exists(tomlPath)) {
            System.out.println("mira.toml already exists, skipping.");
        } else {
            String toml = "[project]\n"
                    + "name    = \"" + name + "\"\n"
                    + "version = \"0.1.0\"\n"
                    + "entry   = \"src/main.mira\"\n"
                    + "\n"
                    + "[build]\n"
                    + "mode = \"interpret\"\n"
                    + "main = true\n"
                    + "\n"
                    + "[test]\n"
                    + "pattern = \"**/*_test.mira\"\n";
            try {
                Files.writeString(tomlPath, toml);
                System.out.println("Created mira.toml");
            } catch (IOException e) {
                throw new BuildException("Cannot write mira.toml: " + e.getMessage());
            }
        }

        if (Files.exists(mainPath)) {
            System.out.println("src/main.mira already exists, skipping.");
        } else {
            String mainMira = "module main;\n"
                    + "\n"
                    + "fn main() {\n"
                    + "    print(\"Hello from " + name + "!\\n\");\n"
                    + "}\n";
            try {
                Files.createDirectories(mainPath.getParent());
                Files.writeString(mainPath, mainMira);
                System.out.println("Created src/main.mira");
            } catch (IOException e) {
                throw new BuildException("Cannot write src/main.mira: " + e.getMessage());
            }
        }

        System.out.println("\nProject '" + name + "' initialized. Run 'mira run' to start.");
    }

    public static void build(String[] args) {
        ProjectConfig.BuildMode modeOverride = null;
        boolean watch = false;
        for (int i = 1; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                modeOverride = parseBuildMode(args[++i]);
            } else if ("--watch".equals(args[i])) {
                watch = true;
            }
        }
        BuildContext ctx = requireContext();
        BuildRunner.runBuild(ctx, modeOverride, watch);
    }

    public static void run(String[] args) {
        String[] programArgs = null;
        for (int i = 1; i < args.length; i++) {
            if ("--".equals(args[i])) {
                programArgs = Arrays.copyOfRange(args, i + 1, args.length);
                break;
            }
        }
        BuildContext ctx = requireContext();
        ctx.applyFlags(ProjectConfig.BuildMode.INTERPRET);
        if (programArgs != null && programArgs.length > 0) {
            Flags.args = programArgs;
        }
        Main.runFile(new AtomicBoolean(false));
    }

    public static void test(String[] args) {
        BuildContext ctx = requireContext();
        BuildRunner.runTest(ctx);
    }

    public static void clean(String[] args) {
        clean(args, Paths.get("").toAbsolutePath());
    }

    static void clean(String[] args, Path startDir) {
        BuildContext ctx = requireContext(startDir);
        Path outputDir = ctx.config().build().outputDir();
        if (!Files.exists(outputDir)) {
            System.out.println("Nothing to clean (output directory does not exist).");
            return;
        }
        try {
            deleteRecursively(outputDir);
            System.out.println("Cleaned: " + outputDir);
        } catch (IOException e) {
            throw new BuildException("Cannot clean output directory: " + e.getMessage());
        }
    }

    private static BuildContext requireContext() {
        return requireContext(Paths.get("").toAbsolutePath());
    }

    static BuildContext requireContext(Path startDir) {
        ProjectConfig config = ProjectLoader.find(startDir)
                .orElseThrow(() -> new BuildException(
                """
                        No mira.toml found in current directory or any parent.
                        Run 'mira init' to create a new project."""));
        List<Path> depRoots = DependencyResolver.resolve(config);
        return new BuildContext(config, depRoots);
    }

    private static ProjectConfig.BuildMode parseBuildMode(String s) {
        return switch (s) {
            case "compile" ->
                ProjectConfig.BuildMode.COMPILE;
            case "package" ->
                ProjectConfig.BuildMode.PACKAGE;
            case "interpret" ->
                ProjectConfig.BuildMode.INTERPRET;
            default ->
                throw new BuildException(
                        "Unknown build mode: '" + s + "'. Expected: interpret, compile, package");
        };
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
