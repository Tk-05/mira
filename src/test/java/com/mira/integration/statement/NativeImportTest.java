package com.mira.integration.statement;

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

import com.mira.Flags;
import com.mira.error.parser.ParserError.LexemeMismatchError;
import com.mira.error.runtime.RuntimeError.NativeLibNoImplementationError;
import com.mira.error.runtime.RuntimeError.NativeLibNotFoundError;
import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.integration.InterpreterTestBase;

public class NativeImportTest extends InterpreterTestBase {

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
    void setUp() {
        skipCompilerTest = true;
        ImportResolver.reset();
        Flags.inputPath.set(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
    }

    @Test
    void missingJarThrowsNativeLibNotFoundError() {
        assertThrows(NativeLibNotFoundError.class, ()
                -> run("import native \"/nonexistent/path/that/does/not/exist.jar\" as ext;"));
    }

    @Test
    void jarWithoutServicesFileThrowsNativeLibNoImplementationError() {
        assertThrows(NativeLibNoImplementationError.class, ()
                -> run("import native \"" + escaped(emptyJar) + "\" as ext;"));
    }

    @Test
    void nativeImportWithoutAliasThrowsParserError() {
        assertThrows(LexemeMismatchError.class, ()
                -> run("import native \"/some/lib.jar\";"));
    }

    @Test
    void validJarLoadsNamespaceAndStringFunctionIsCallable() {
        Object result = run("import native \"" + escaped(greetJar) + "\" as ext; ext.greet(\"world\");");
        assertEquals("hello world", result);
    }

    @Test
    void validJarLoadsNamespaceAndNumericFunctionIsCallable() {
        Object result = run("import native \"" + escaped(greetJar) + "\" as ext; ext.add(3, 4);");
        assertEquals(7.0, result);
    }

    @Test
    void nativeFunctionResultChangesWithDifferentArguments() {
        Object result = run("import native \"" + escaped(greetJar) + "\" as ext; ext.greet(\"mira\");");
        assertEquals("hello mira", result);
    }

    @Test
    void nativeLibIsAccessibleOnlyViaAlias() {
        String path = escaped(greetJar);
        assertThrows(RuntimeException.class, ()
                -> run("import native \"" + path + "\" as ext; greet(\"world\");"));
    }

    @Test
    void duplicateNativeImportWithSameAliasIsIdempotent() {
        String path = escaped(greetJar);
        assertThrows(ObjectAlreadyDefinedInScope.class,() -> run("""
                import native "%s" as ext;
                import native "%s" as ext;
                ext.greet("world");
                """.formatted(path, path)));
    }

    @Test
    void sameJarCanBeImportedUnderDifferentAliases() {
        String path = escaped(greetJar);
        Object result = run("""
                import native "%s" as a;
                import native "%s" as b;
                a.greet("x") + b.greet("y");
                """.formatted(path, path));
        assertEquals("hello xhello y", result);
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
