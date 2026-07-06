package com.mira.build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.Flags;
import com.mira.runtime.FileRunner;
import com.mira.runtime.interpreter.ImportResolver;

public class BuildSystemIntegrationTest {

    @TempDir
    Path projectDir;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureStreams() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ImportResolver.reset();
        Flags.dependencyRoots = new java.util.ArrayList<>();
    }

    private String stdout() {
        return capturedOut.toString().trim();
    }

    @Test
    void isSubcommandRecognizesAllCommands() {
        for (String cmd : List.of("init", "build", "run", "test", "clean")) {
            assertTrue(BuildDispatcher.isSubcommand(cmd), cmd + " should be a subcommand");
        }
    }

    @Test
    void isSubcommandRejectsNonCommands() {
        for (String arg : List.of("file.mira", "-m", "--lsp", "-h", "")) {
            assertFalse(BuildDispatcher.isSubcommand(arg), arg + " should not be a subcommand");
        }
    }

    @Test
    void findsTomlInGivenDirectory() throws IOException {
        writeToml(projectDir, "my-proj", "src/main.mira", "interpret", false);
        assertTrue(ProjectLoader.find(projectDir).isPresent());
    }

    @Test
    void findsTomlInParentDirectory() throws IOException {
        writeToml(projectDir, "parent-proj", "src/main.mira", "interpret", false);
        Path sub = projectDir.resolve("src/subdir");
        Files.createDirectories(sub);
        assertTrue(ProjectLoader.find(sub).isPresent());
    }

    @Test
    void returnsEmptyWhenNoToml() {
        Path emptyDir = projectDir.resolve("empty");
        assertFalse(ProjectLoader.find(emptyDir).isPresent());
    }

    @Test
    void loadsProjectNameAndEntry() throws IOException {
        writeToml(projectDir, "awesome-app", "src/main.mira", "interpret", false);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        assertEquals("awesome-app", cfg.name());
        assertEquals(projectDir.resolve("src/main.mira").normalize(), cfg.entry());
    }

    @Test
    void initCreatesExpectedFiles() {
        Commands.init(new String[]{"init"}, projectDir);

        assertTrue(Files.exists(projectDir.resolve("mira.toml")));
        assertTrue(Files.exists(projectDir.resolve("src/main.mira")));
    }

    @Test
    void initUsesDirectoryNameAsDefault() {
        Commands.init(new String[]{"init"}, projectDir);

        String toml = readFile(projectDir.resolve("mira.toml"));
        assertTrue(toml.contains("name    = \"" + projectDir.getFileName() + "\""));
    }

    @Test
    void initRespectsNameFlag() {
        Commands.init(new String[]{"init", "--name", "my-custom-app"}, projectDir);

        String toml = readFile(projectDir.resolve("mira.toml"));
        assertTrue(toml.contains("name    = \"my-custom-app\""));
    }

    @Test
    void initGeneratesMainMiraWithModuleName() {
        Commands.init(new String[]{"init", "--name", "hello"}, projectDir);

        String main = readFile(projectDir.resolve("src/main.mira"));
        assertTrue(main.contains("module main;"));
        assertTrue(main.contains("fn main()"));
        assertTrue(main.contains("Hello from hello!"));
    }

    @Test
    void initSetsMainTrueInToml() {
        Commands.init(new String[]{"init"}, projectDir);

        String toml = readFile(projectDir.resolve("mira.toml"));
        assertTrue(toml.contains("main = true"));
    }

    @Test
    void initDoesNotOverwriteExistingToml() throws IOException {
        String original = "[project]\nname = \"original\"\nentry = \"x.mira\"\n";
        Files.writeString(projectDir.resolve("mira.toml"), original);

        Commands.init(new String[]{"init"}, projectDir);

        assertEquals(original, readFile(projectDir.resolve("mira.toml")));
        assertTrue(stdout().contains("already exists"));
    }

    @Test
    void initDoesNotOverwriteExistingMainMira() throws IOException {
        Files.createDirectories(projectDir.resolve("src"));
        String original = "module main;\nfn main() { print(\"keep me\"); }\n";
        Files.writeString(projectDir.resolve("src/main.mira"), original);

        Commands.init(new String[]{"init"}, projectDir);

        assertEquals(original, readFile(projectDir.resolve("src/main.mira")));
        assertTrue(stdout().contains("already exists"));
    }

    @Test
    void resolveReturnsEmptyListForNoDeps() throws IOException {
        writeToml(projectDir, "app", "main.mira", "interpret", false);
        ProjectConfig cfg = ProjectLoader.load(projectDir.resolve("mira.toml"));
        List<Path> roots = DependencyResolver.resolve(cfg);
        assertTrue(roots.isEmpty());
    }

    @Test
    void resolveErrorOnMissingDepDirectory() throws IOException {
        Path tomlPath = projectDir.resolve("mira.toml");
        Files.writeString(tomlPath,
                "[project]\nname=\"app\"\nentry=\"main.mira\"\n"
                + "[dependencies]\nlib = { path = \"../nonexistent\" }\n");
        ProjectConfig cfg = ProjectLoader.load(tomlPath);
        assertThrows(BuildException.class, () -> DependencyResolver.resolve(cfg));
    }

    @Test
    void resolveErrorOnDepWithoutToml() throws IOException {
        Path depDir = projectDir.resolve("dep");
        Files.createDirectories(depDir);

        Path tomlPath = projectDir.resolve("mira.toml");
        Files.writeString(tomlPath,
                "[project]\nname=\"app\"\nentry=\"main.mira\"\n"
                + "[dependencies]\ndep = { path = \"dep\" }\n");
        ProjectConfig cfg = ProjectLoader.load(tomlPath);
        assertThrows(BuildException.class, () -> DependencyResolver.resolve(cfg));
    }

    @Test
    void applyFlagsInterpretMode() throws IOException {
        Path entry = writeMinimalProject("test-app", "interpret", false);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(null);

        assertEquals(entry, Flags.inputPath.get());
        assertFalse(Flags.compile);
        assertFalse(Flags.packageJar);
        assertFalse(Flags.mainFunction);
    }

    @Test
    void applyFlagsCompileMode() throws IOException {
        writeMinimalProject("compile-app", "compile", false);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(null);

        assertTrue(Flags.compile);
        assertFalse(Flags.packageJar);
        assertNotNull(Flags.outputDir);
    }

    @Test
    void applyFlagsPackageMode() throws IOException {
        writeMinimalProject("pkg-app", "package", false, "full");
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(null);

        assertTrue(Flags.compile);
        assertTrue(Flags.packageJar);
        assertFalse(Flags.slimJar);
    }

    @Test
    void applyFlagsPackageModeSlim() throws IOException {
        writeMinimalProject("pkg-app-slim", "package", false, "slim");
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(null);

        assertTrue(Flags.compile);
        assertTrue(Flags.packageJar);
        assertTrue(Flags.slimJar);
    }

    @Test
    void packageModeWithoutJarBundleThrows() throws IOException {
        writeToml(projectDir, "no-bundle", "src/main.mira", "package", false);
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("src/main.mira"), "module main;\nfn main() { print(\"hi\"); }\n");

        BuildException ex = assertThrows(BuildException.class,
                () -> ProjectLoader.find(projectDir).orElseThrow());
        assertTrue(ex.getMessage().contains("jar-bundle"));
    }

    @Test
    void jarBundleWithNonPackageModeThrows() throws IOException {
        writeMinimalProject("interpret-with-bundle", "interpret", false, "slim");

        BuildException ex = assertThrows(BuildException.class,
                () -> ProjectLoader.find(projectDir).orElseThrow());
        assertTrue(ex.getMessage().contains("jar-bundle"));
    }

    @Test
    void applyFlagsModeOverrideWins() throws IOException {
        writeMinimalProject("override-app", "interpret", false);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(ProjectConfig.BuildMode.COMPILE);

        assertTrue(Flags.compile);
    }

    @Test
    void applyFlagsSetsMainFunctionFlag() throws IOException {
        writeMinimalProject("main-app", "interpret", true);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of());

        ctx.applyFlags(null);

        assertTrue(Flags.mainFunction);
    }

    @Test
    void applyFlagsPropagatesDependencyRoots() throws IOException {
        writeMinimalProject("app", "interpret", false);
        Path depRoot = projectDir.resolve("dep");
        Files.createDirectories(depRoot);
        ProjectConfig cfg = ProjectLoader.find(projectDir).orElseThrow();
        BuildContext ctx = new BuildContext(cfg, List.of(depRoot));

        ctx.applyFlags(null);

        assertTrue(Flags.dependencyRoots.contains(depRoot));
    }

    @Test
    void cleanRemovesOutputDirectory() throws IOException {
        writeToml(projectDir, "app", "main.mira", "compile", false);
        Path outDir = projectDir.resolve("out");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("App.class"), "dummy");

        Commands.clean(new String[]{"clean"}, projectDir);

        assertFalse(Files.exists(outDir));
        assertTrue(stdout().contains("Cleaned"));
    }

    @Test
    void cleanPrintsMessageWhenNothingToClean() throws IOException {
        writeToml(projectDir, "app", "main.mira", "interpret", false);
        Commands.clean(new String[]{"clean"}, projectDir);
        assertTrue(stdout().contains("Nothing to clean"));
    }

    @Test
    void runExecutesMainFunction() throws IOException {
        writeToml(projectDir, "hello", "src/main.mira", "interpret", true);
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("src/main.mira"),
                "module main;\nfn main() { print(\"run-ok\"); }\n");

        BuildContext ctx = Commands.requireContext(projectDir);
        ctx.applyFlags(ProjectConfig.BuildMode.INTERPRET);
        FileRunner.runFile(new AtomicBoolean(false));

        assertTrue(stdout().contains("run-ok"));
    }

    @Test
    void packageSlimModeExcludesToolchainAndAsm() throws Exception {
        writeMinimalProject("slim-pkg", "package", false, "slim");

        BuildContext ctx = Commands.requireContext(projectDir);
        ctx.applyFlags(ProjectConfig.BuildMode.PACKAGE, ProjectConfig.JarBundle.SLIM);
        boolean ok = FileRunner.runFile(new AtomicBoolean(false));
        assertTrue(ok);

        Path jarPath = projectDir.resolve("out/main.jar");
        assertTrue(Files.exists(jarPath));

        Set<String> entries = new HashSet<>();
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                entries.add(en.nextElement().getName());
            }
        }

        assertTrue(entries.stream().noneMatch(n -> n.startsWith("com/mira/lsp/")));
        assertTrue(entries.stream().noneMatch(n -> n.startsWith("com/mira/dap/")));
        assertTrue(entries.stream().noneMatch(n -> n.startsWith("com/mira/repl/")));
        assertTrue(entries.stream().noneMatch(n -> n.startsWith("com/mira/debugger/")));
        assertTrue(entries.stream().noneMatch(n -> n.startsWith("org/eclipse/lsp4j/")));
        assertTrue(entries.stream().noneMatch(n -> n.startsWith("org/objectweb/asm/")));

        assertTrue(entries.stream().anyMatch(n -> n.startsWith("com/mira/lib/std/")));
        assertTrue(entries.stream().anyMatch(n -> n.startsWith("com/mira/runtime/")));
    }

    @Test
    void packageSlimModeJarRunsCorrectly() throws Exception {
        writeToml(projectDir, "slim-run", "src/main.mira", "package", true, "slim");
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("src/main.mira"),
                "module main;\nfn main() { print(\"slim-ok\"); }\n");

        BuildContext ctx = Commands.requireContext(projectDir);
        ctx.applyFlags(ProjectConfig.BuildMode.PACKAGE, ProjectConfig.JarBundle.SLIM);
        boolean ok = FileRunner.runFile(new AtomicBoolean(false));
        assertTrue(ok);

        Path jarPath = projectDir.resolve("out/main.jar");
        assertTrue(Files.exists(jarPath));

        Process process = new ProcessBuilder("java", "-jar", jarPath.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "jar exited with non-zero status. Output: " + output);
        assertTrue(output.contains("slim-ok"), "Output: " + output);
    }

    @Test
    void packageSlimModeEvalStillWorks() throws Exception {
        writeToml(projectDir, "slim-eval", "src/main.mira", "package", true, "slim");
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("src/main.mira"),
                "module main;\nfn main() { print(eval(\"1 + 2\")); }\n");

        BuildContext ctx = Commands.requireContext(projectDir);
        ctx.applyFlags(ProjectConfig.BuildMode.PACKAGE, ProjectConfig.JarBundle.SLIM);
        boolean ok = FileRunner.runFile(new AtomicBoolean(false));
        assertTrue(ok);

        Path jarPath = projectDir.resolve("out/main.jar");

        Process process = new ProcessBuilder("java", "-jar", jarPath.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "jar exited with non-zero status. Output: " + output);
        assertTrue(output.contains("3"), "Output: " + output);
    }

    @Test
    void runWithoutTomlThrows() {
        assertThrows(BuildException.class,
                () -> Commands.requireContext(projectDir));
    }

    private void writeToml(Path dir, String name, String entry, String mode, boolean main)
            throws IOException {
        writeToml(dir, name, entry, mode, main, null);
    }

    private void writeToml(Path dir, String name, String entry, String mode, boolean main, String jarBundle)
            throws IOException {
        String content = "[project]\n"
                + "name = \"" + name + "\"\n"
                + "version = \"0.1.0\"\n"
                + "entry = \"" + entry + "\"\n"
                + "\n[build]\n"
                + "mode = \"" + mode + "\"\n"
                + "main = " + main + "\n"
                + (jarBundle != null ? "jar-bundle = \"" + jarBundle + "\"\n" : "");
        Files.writeString(dir.resolve("mira.toml"), content);
    }

    private Path writeMinimalProject(String name, String mode, boolean main) throws IOException {
        return writeMinimalProject(name, mode, main, null);
    }

    private Path writeMinimalProject(String name, String mode, boolean main, String jarBundle) throws IOException {
        writeToml(projectDir, name, "src/main.mira", mode, main, jarBundle);
        Path entry = projectDir.resolve("src/main.mira");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, "module main;\nfn main() { print(\"hi\"); }\n");
        return entry;
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
