package com.mira.build;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mira.Flags;
import com.mira.Main;
import com.mira.error.DiagnosticFormatter;
import com.mira.runtime.HotReloader;

public class BuildRunner {

    public static void runBuild(BuildContext ctx, ProjectConfig.BuildMode modeOverride, boolean watch) {
        ctx.applyFlags(modeOverride);
        if (watch) {
            new HotReloader(Flags.inputPath.get()).run();
            return;
        }
        runHook(ctx, ctx.config().build().preBuild());
        long start = System.currentTimeMillis();
        Main.runFile(new AtomicBoolean(false));
        if (Flags.compile) {
            System.out.println(DiagnosticFormatter.formatInfo(
                    "finished in " + (System.currentTimeMillis() - start) + " ms"));
        }
        runHook(ctx, ctx.config().build().postBuild());
    }

    public static void runTest(BuildContext ctx) {
        ProjectConfig config = ctx.config();
        Path projectRoot = config.projectRoot();

        if (config.test() == null) {
            throw new BuildException(
                    "no [test] section defined in mira.toml\n"
                    + "Add a [test] section to configure test discovery, e.g.:\n"
                    + "\n"
                    + "  [test]\n"
                    + "  pattern = \"**/*_test.mira\"");
        }

        ctx.applyFlags(ProjectConfig.BuildMode.INTERPRET);
        Flags.testMode = true;
        Flags.compile = false;
        Flags.mainFunction = false;

        List<Path> testFiles = findTestFiles(projectRoot, config.test().pattern());
        for (String extra : config.test().extra()) {
            Path extraPath = projectRoot.resolve(extra).normalize();
            if (Files.exists(extraPath) && !testFiles.contains(extraPath)) {
                testFiles.add(extraPath);
            }
        }

        if (testFiles.isEmpty()) {
            System.out.println(DiagnosticFormatter.formatInfo(
                    "no test files found matching: " + config.test().pattern()));
            return;
        }

        runHook(ctx, config.test().preTest());
        System.out.println(DiagnosticFormatter.formatInfo("running tests for " + config.name() + "..."));
        long totalStart = System.currentTimeMillis();
        for (Path testFile : testFiles) {
            System.out.println("\n--- " + projectRoot.relativize(testFile) + " ---");
            Flags.inputPath.set(testFile);
            Flags.testsDone = false;
            long fileStart = System.currentTimeMillis();
            Main.runFile(new AtomicBoolean(false));
            System.out.println(DiagnosticFormatter.formatInfo(
                    projectRoot.relativize(testFile) + " finished in "
                    + (System.currentTimeMillis() - fileStart) + " ms"));
        }
        System.out.println(DiagnosticFormatter.formatInfo(
                "all tests finished in " + (System.currentTimeMillis() - totalStart) + " ms"));
        runHook(ctx, config.test().postTest());
    }

    static void runHook(BuildContext ctx, List<String> taskNames) {
        if (taskNames == null) {
            return;
        }
        for (String name : taskNames) {
            if (name != null && !name.isBlank()) {
                TaskRunner.runTask(ctx, name);
            }
        }
    }

    private static List<Path> findTestFiles(Path root, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(root.relativize(p)))
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
