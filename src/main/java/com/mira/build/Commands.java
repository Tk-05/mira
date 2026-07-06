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
import com.mira.error.DiagnosticFormatter;
import com.mira.runtime.FileRunner;

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
        boolean watch = Arrays.asList(args).contains("--watch");
        BuildOverrides overrides = parseOverrides(args, 1);
        BuildContext ctx = requireContext();
        BuildRunner.runBuild(ctx, overrides.mode(), overrides.jarBundle(), watch);
    }

    public static void run(String[] args) {
        String[] programArgs = null;
        String projectDir = null;
        for (int i = 1; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                projectDir = args[++i];
            } else if ("--no-warn".equals(args[i])) {
                Flags.suppressWarnings = true;
            } else if ("--profile".equals(args[i])) {
                Flags.profile = true;
            } else if ("--".equals(args[i])) {
                programArgs = Arrays.copyOfRange(args, i + 1, args.length);
                break;
            }
        }
        BuildOverrides overrides = parseOverrides(args, 1);

        BuildContext ctx = projectDir != null
                ? requireContext(Paths.get(projectDir).toAbsolutePath().normalize())
                : requireContext();
        ProjectConfig.BuildMode effectiveMode = overrides.mode() != null
                ? overrides.mode()
                : ctx.config().build().effectiveRunMode();

        ctx.applyFlags(effectiveMode, overrides.jarBundle());

        if (effectiveMode == ProjectConfig.BuildMode.COMPILE
                || effectiveMode == ProjectConfig.BuildMode.PACKAGE) {
            Flags.compileAndRun = true;
            Flags.packageJar = false;
        }

        if (programArgs != null && programArgs.length > 0) {
            Flags.args = programArgs;
        }
        BuildRunner.runHook(ctx, ctx.config().build().preRun());
        boolean ok = FileRunner.runFile(new AtomicBoolean(false));
        if (!ok) {
            System.err.println(DiagnosticFormatter.formatFail("run failed"));
            System.exit(1);
        }
        BuildRunner.runHook(ctx, ctx.config().build().postRun());
    }

    public static void test(String[] args) {
        BuildContext ctx = requireContext();
        BuildRunner.runTest(ctx);
    }

    public static void release(String[] args) {
        BuildOverrides overrides = parseOverrides(args, 1);
        BuildContext ctx = requireContext();
        release(ctx, overrides);
    }

    private static void release(BuildContext ctx, BuildOverrides overrides) {
        BuildRunner.runBuild(ctx, overrides.mode(), overrides.jarBundle(), false);
        if (ctx.config().test() != null) {
            BuildRunner.runTest(ctx);
        } else {
            System.out.println(DiagnosticFormatter.formatInfo(
                    "no [test] section defined — skipping tests"));
        }
    }

    public static void task(String[] args) {
        BuildContext ctx = requireContext();
        if (args.length < 2) {
            TaskRunner.listTasks(ctx.config());
            return;
        }
        if (Arrays.asList(args).contains("--profile")) {
            Flags.profile = true;
        }
        TaskRunner.runTask(ctx, args[1]);
    }

    public static void clean(String[] args) {
        clean(args, Paths.get("").toAbsolutePath());
    }

    static void clean(String[] args, Path startDir) {
        boolean buildAfter = args.length >= 2 && "build".equals(args[1]);
        boolean releaseAfter = args.length >= 2 && "release".equals(args[1]);
        BuildContext ctx = requireContext(startDir);
        Path outputDir = ctx.config().build().outputDir();
        if (Files.exists(outputDir)) {
            try {
                deleteRecursively(outputDir);
                System.out.println("Cleaned: " + outputDir);
            } catch (IOException e) {
                throw new BuildException("Cannot clean output directory: " + e.getMessage());
            }
        } else if (!buildAfter && !releaseAfter) {
            System.out.println("Nothing to clean (output directory does not exist).");
        }
        if (buildAfter) {
            BuildOverrides overrides = parseOverrides(args, 2);
            BuildRunner.runBuild(ctx, overrides.mode(), overrides.jarBundle(), false);
        } else if (releaseAfter) {
            BuildOverrides overrides = parseOverrides(args, 2);
            release(ctx, overrides);
        }
    }

    private record BuildOverrides(ProjectConfig.BuildMode mode, ProjectConfig.JarBundle jarBundle) {

    }

    private static BuildOverrides parseOverrides(String[] args, int from) {
        ProjectConfig.BuildMode mode = null;
        ProjectConfig.JarBundle jarBundle = null;
        for (int i = from; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                mode = parseBuildMode(args[++i]);
            } else if ("--slim".equals(args[i])) {
                jarBundle = ProjectConfig.JarBundle.SLIM;
            } else if ("--full".equals(args[i])) {
                jarBundle = ProjectConfig.JarBundle.FULL;
            }
        }
        return new BuildOverrides(mode, jarBundle);
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
