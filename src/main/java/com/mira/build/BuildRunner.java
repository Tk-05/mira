package com.mira.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mira.Flags;
import com.mira.Main;
import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.HotReloader;
import com.mira.testing.TestRunner;

public class BuildRunner {

    public static void runBuild(BuildContext ctx, ProjectConfig.BuildMode modeOverride, boolean watch) {
        ctx.applyFlags(modeOverride);
        if (watch) {
            new HotReloader(Flags.inputPath.get()).run();
            return;
        }
        if (Flags.compile && Flags.outputDir != null) {
            BuildCache cache = BuildCache.load(Flags.outputDir);
            if (cache.isUpToDate(Flags.inputPath.get())) {
                System.out.println(DiagnosticFormatter.formatInfo("nothing to compile (up to date)"));
                return;
            }
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
        boolean anyFailed = false;

        for (Path testFile : testFiles) {
            System.out.println("\n--- " + projectRoot.relativize(testFile) + " ---");
            Flags.inputPath.set(testFile);
            try {
                String source = Files.readString(testFile);
                Flags.fileName = testFile.getFileName().toString();
                Flags.sourceLines = source.split("\n", -1);
                List<Token> tokens = new Tokenizer().tokenize(source, false);
                List<Node> asts = new Parser().parseTokens(tokens);
                boolean failed = TestRunner.runPrePassCollecting(asts, Flags.args);
                if (failed) {
                    anyFailed = true;
                }
            } catch (Exception e) {
                System.err.println(DiagnosticFormatter.format(e));
                anyFailed = true;
            }
        }

        System.out.println(DiagnosticFormatter.formatInfo(
                "all tests finished in " + (System.currentTimeMillis() - totalStart) + " ms"));
        runHook(ctx, config.test().postTest());
        if (anyFailed) {
            System.exit(1);
        }
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
        Pattern compiled = Pattern.compile(globToRegex(pattern));
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String rel = root.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                        return compiled.matcher(rel).matches();
                    })
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static String globToRegex(String glob) {
        glob = glob.replace('\\', '/');
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                i += 2;
                if (i < glob.length() && glob.charAt(i) == '/') {
                    i++;
                    sb.append("(.*/)?");
                } else {
                    sb.append(".*");
                }
            } else if (c == '*') {
                sb.append("[^/]*");
                i++;
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else if (".()[]{}+^$|\\".indexOf(c) >= 0) {
                sb.append('\\').append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
