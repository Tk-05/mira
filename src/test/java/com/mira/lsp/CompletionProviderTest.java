package com.mira.lsp;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class CompletionProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void aliasedModuleImportUsesNamespacedLabel(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" as lib;
                """;
        Files.writeString(mainPath, source);

        List<CompletionItem> items = CompletionProvider.provide(parse(source), mainPath.toUri().toString());
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("lib.greet")));
    }

    @Test
    void selectiveModuleImportWithoutAliasUsesBareLabel(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet() {
                    return 1;
                }
                pub fn other() {
                    return 2;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" {greet};
                """;
        Files.writeString(mainPath, source);

        List<CompletionItem> items = CompletionProvider.provide(parse(source), mainPath.toUri().toString());

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("greet")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().startsWith("null.")));
        // only the selected function should be suggested, not every public function in
        // the module
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("other")));
    }

    @Test
    void suggestsDestructuredVariableNames() {
        String source = """
                var (a, b) : {1, 2};
                """;
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("a")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("b")));
    }

    @Test
    void suggestsVariableDeclaredInsideNestedIfBlock() {
        String source = """
                fn main() {
                    if (true) {
                        var nested : 1;
                    }
                }
                """;
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("nested")));
    }

    @Test
    void suggestsBuiltinTypeNames() {
        List<CompletionItem> items = CompletionProvider.provide(parse(""), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("Number")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("String")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("Any")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("Void")));
    }

    @Test
    void suggestsDeclaredTypeAliasAndEnumNames() {
        String source = """
                type UserId : Number;
                enum Color { RED, GREEN, BLUE }
                """;
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("UserId")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("Color")));
    }

    @Test
    void suggestsStructTemplateNameAsBareType() {
        // regression: a struct template variable is a valid nominal type
        // (e.g. usable as "-> point"), so its bare name - not just its
        // "point"/"point.field" member-access forms - must be suggested
        String source = """
                var point : struct {
                    var x;
                    var y;
                };
                """;
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("point")));
        // the field-access forms should still be offered too, just not exclusively
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("point.x")));
    }

    @Test
    void dotCompletionOnStructVarSuggestsBareMemberNames() {
        String source = """
                var Point : struct { var x; var y; fn dist() { return 0; } };
                fn main() {
                    var p : Point{};
                    return p.x;
                }
                """;
        Position pos = new Position(3, 14); // "x" in "return p.x;"
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira", source, pos, null);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("x")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("y")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("dist")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("p.x")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("Number")));
    }

    @Test
    void dotCompletionOnObjectVarSuggestsBareMemberNames() {
        String source = """
                var obj : { var a; fn method() { return 0; } };
                fn main() {
                    return obj.a;
                }
                """;
        Position pos = new Position(2, 15); // "a" in "return obj.a;"
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira", source, pos, null);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("a")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("method")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("obj.a")));
    }

    @Test
    void dotCompletionOnEnumSuggestsBareMemberNames() {
        String source = """
                enum Color { RED, GREEN, BLUE }
                fn main() {
                    return Color.RED;
                }
                """;
        Position pos = new Position(2, 18); // "RED" in "Color.RED"
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira", source, pos, null);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("RED")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("GREEN")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("BLUE")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("Color.RED")));
    }

    @Test
    void dotCompletionOnModuleAliasSuggestsBareFunctionNames(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.greet();
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 16); // "greet" in "lib.greet()"
        List<CompletionItem> items = CompletionProvider.provide(parse(source), mainPath.toUri().toString(), source, pos,
                mainPath);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("greet")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("lib.greet")));
    }

    @Test
    void dotCompletionOnNativeLibAliasSuggestsBareMemberNames(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("fixture.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/mira/interface.properties"));
            jos.write("GetWidth=->Number\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import native "fixture.jar" as ext;
                fn main() {
                    return ext.GetWidth();
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 16); // "GetWidth" in "ext.GetWidth()"
        List<CompletionItem> items = CompletionProvider.provide(parse(source), mainPath.toUri().toString(), source, pos,
                mainPath);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("GetWidth")));
        assertFalse(items.stream().anyMatch(i -> i.getLabel().equals("ext.GetWidth")));
    }

    @Test
    void noDotContextStillReturnsWholeDocumentFlatList() {
        String source = """
                fn main() {
                    return 1;
                }
                """;
        Position pos = new Position(1, 12); // inside "return 1;" - not a field access
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira", source, pos, null);

        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("Number")));
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("main")));
    }
}
