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
import com.mira.runtime.HotReloader;

public class BuildRunner {

    public static void runBuild(BuildContext ctx, ProjectConfig.BuildMode modeOverride, boolean watch) {
        ctx.applyFlags(modeOverride);
        if (watch) {
            new HotReloader(Flags.inputPath.get()).run();
            return;
        }
        Main.runFile(new AtomicBoolean(false));
    }

    public static void runTest(BuildContext ctx) {
        ProjectConfig config = ctx.config();
        Path projectRoot = config.projectRoot();

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
            System.out.println("No test files found matching: " + config.test().pattern());
            return;
        }

        System.out.println("Running tests for " + config.name() + "...");
        for (Path testFile : testFiles) {
            System.out.println("\n--- " + projectRoot.relativize(testFile) + " ---");
            Flags.inputPath.set(testFile);
            Flags.testsDone = false;
            Main.runFile(new AtomicBoolean(false));
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
