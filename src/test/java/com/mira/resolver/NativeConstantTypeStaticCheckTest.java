package com.mira.resolver;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.cli.Flags;
import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

/**
 * Covers the classloading-free manifest path (NativeInterfaceManifest /
 * NativeLibLocator) StaticCheck uses to type a native namespace's constants -
 * needs a real jar with a META-INF/mira/interface.properties entry on disk, so
 * it can't use StaticCheckTest's plain in-memory errorsFor().
 */
public class NativeConstantTypeStaticCheckTest {

    private static Path sourceDir;

    @BeforeAll
    static void buildFixtureJar() throws Exception {
        sourceDir = Files.createTempDirectory("mira-native-const-test");
        Path jarPath = sourceDir.resolve("fixture.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/mira/interface.properties"));
            jos.write("KEY_LEFT=->Number\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.walk(sourceDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    @BeforeEach
    void resetNativeRoots() {
        Flags.nativeRoots = new ArrayList<>();
    }

    private List<MiraError> check(String source) throws Exception {
        Path sourceFile = sourceDir.resolve("test.mira");
        Files.writeString(sourceFile, source);
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        try {
            new StaticCheck(Set.of(), sourceFile).check(ast);
            return List.of();
        } catch (MultipleStaticCheckErrors ex) {
            return ex.getErrors();
        }
    }

    @Test
    void nativeConstantTypeMismatchIsE324() throws Exception {
        List<MiraError> errors = check(
                "module M; import native \"fixture.jar\" as ext; var x : String : ext.KEY_LEFT;");
        assertTrue(errors.stream().anyMatch(e -> "E324".equals(e.getErrorCode())));
    }

    @Test
    void nativeConstantMatchingTypeIsClean() throws Exception {
        List<MiraError> errors = check(
                "module M; import native \"fixture.jar\" as ext; var x : Number : ext.KEY_LEFT; println(x);");
        assertTrue(errors.isEmpty());
    }
}
