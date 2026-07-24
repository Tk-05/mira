package com.mira.integration.interpreter.statement;

import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.cli.Flags;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.runtime.RuntimeError.NativeLibNoImplementationError;
import com.mira.error.runtime.RuntimeError.NativeLibNotFoundError;
import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractNativeImportTests;
import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.ImportResolver;

public class NativeImportTest extends AbstractNativeImportTests {

    public static class GreetLib implements Lib {

        @Override
        public void loadLib(Environment env) {
            env.define("greet", new NativeFunction(1, args -> "hello " + args.get(0)));
            env.define("add", new NativeFunction(2, args -> {
                double a = Double.parseDouble(String.valueOf(args.get(0)));
                double b = Double.parseDouble(String.valueOf(args.get(1)));
                return a + b;
            }));
        }
    }

    private static Path greetJar;
    private static Path emptyJar;

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeAll
    static void buildFixtureJars() throws Exception {
        greetJar = buildJarWithLib(GreetLib.class);
        emptyJar = buildEmptyJar();
    }

    @AfterAll
    static void cleanupFixtureJars() throws Exception {
        ImportResolver.reset();
        if (greetJar != null) {
            Files.deleteIfExists(greetJar);
        }
        if (emptyJar != null) {
            Files.deleteIfExists(emptyJar);
        }
    }

    @BeforeEach
    void setup() {
        backend.reset();
        ImportResolver.reset();
        Flags.inputPath.set(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        Flags.nativeRoots = new java.util.ArrayList<>();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void missingJarThrowsNativeLibNotFoundError() {
        assertThrows(NativeLibNotFoundError.class, ()
                -> backend.runAndGetValue("import native \"/nonexistent/path/that/does/not/exist.jar\" as ext;"));
    }

    @Test
    void jarWithoutServicesFileThrowsNativeLibNoImplementationError() {
        assertThrows(NativeLibNoImplementationError.class, ()
                -> backend.runAndGetValue("import native \"" + escaped(emptyJar) + "\" as ext;"));
    }

    @Test
    void nativeImportWithoutAliasThrowsParserError() {
        assertThrows(MultipleParserErrors.class, ()
                -> backend.runAndGetValue("import native \"/some/lib.jar\";"));
    }

    @Test
    void validJarLoadsNamespaceAndStringFunctionIsCallable() {
        Object result = backend.runAndGetValue("import native \"" + escaped(greetJar) + "\" as ext; ext.greet(\"world\");");
        assertEquals("hello world", result);
    }

    @Test
    void validJarLoadsNamespaceAndNumericFunctionIsCallable() {
        Object result = backend.runAndGetValue("import native \"" + escaped(greetJar) + "\" as ext; ext.add(3, 4);");
        assertEquals(7.0, result);
    }

    @Test
    void nativeFunctionResultChangesWithDifferentArguments() {
        Object result = backend.runAndGetValue("import native \"" + escaped(greetJar) + "\" as ext; ext.greet(\"mira\");");
        assertEquals("hello mira", result);
    }

    @Test
    void nativeLibIsAccessibleOnlyViaAlias() {
        String path = escaped(greetJar);
        assertThrows(RuntimeException.class, ()
                -> backend.runAndGetValue("import native \"" + path + "\" as ext; greet(\"world\");"));
    }

    @Test
    void duplicateNativeImportWithSameAliasIsIdempotent() {
        String path = escaped(greetJar);
        assertThrows(ObjectAlreadyDefinedInScope.class, () -> backend.runAndGetValue("""
                import native "%s" as ext;
                import native "%s" as ext;
                ext.greet("world");
                """.formatted(path, path)));
    }

    @Test
    void sameJarCanBeImportedUnderDifferentAliases() {
        String path = escaped(greetJar);
        Object result = backend.runAndGetValue("""
                import native "%s" as a;
                import native "%s" as b;
                a.greet("x") + b.greet("y");
                """.formatted(path, path));
        assertEquals("hello xhello y", result);
    }

    @Test
    void bareBasenameResolvedViaFlagsNativeRoots() {
        Flags.nativeRoots = java.util.List.of(greetJar.getParent());
        String basename = greetJar.getFileName().toString();

        Object result = backend.runAndGetValue(
                "import native \"" + basename + "\" as ext; ext.greet(\"world\");");

        assertEquals("hello world", result);
    }

    @Test
    void literalPathStillWinsWhenNativeRootsHasUnrelatedEntry() throws Exception {
        Path unrelatedDir = Files.createTempDirectory("mira-unrelated-native-root");
        Flags.nativeRoots = java.util.List.of(unrelatedDir);

        Object result = backend.runAndGetValue(
                "import native \"" + escaped(greetJar) + "\" as ext; ext.greet(\"world\");");

        assertEquals("hello world", result);
        Files.deleteIfExists(unrelatedDir);
    }

    @Test
    void missingBasenameFallsThroughToLiteralPathAndStillThrowsNotFoundError() throws Exception {
        Path unrelatedDir = Files.createTempDirectory("mira-unrelated-native-root-2");
        Flags.nativeRoots = java.util.List.of(unrelatedDir);

        assertThrows(NativeLibNotFoundError.class, ()
                -> backend.runAndGetValue("import native \"/nonexistent/path/that/does/not/exist.jar\" as ext;"));
        Files.deleteIfExists(unrelatedDir);
    }

    private static Path buildJarWithLib(Class<? extends Lib> libClass) throws Exception {
        String binaryName = libClass.getName();
        String classResourcePath = binaryName.replace('.', '/') + ".class";
        URL classUrl = NativeImportTest.class.getClassLoader().getResource(classResourcePath);
        byte[] classBytes = Files.readAllBytes(Paths.get(classUrl.toURI()));
        Path jar = Files.createTempFile("mira-greet-fixture", ".jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/services/com.mira.lib.Lib"));
            jos.write(binaryName.getBytes());
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(classResourcePath));
            jos.write(classBytes);
            jos.closeEntry();
        }
        return jar;
    }

    private static Path buildEmptyJar() throws Exception {
        Path jar = Files.createTempFile("mira-empty-fixture", ".jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
        }
        return jar;
    }

    private static String escaped(Path p) {
        return p.toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
