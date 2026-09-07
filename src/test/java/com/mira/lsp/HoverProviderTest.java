package com.mira.lsp;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class HoverProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    private static String text(Hover hover) {
        return hover.getContents().getRight().getValue();
    }

    @Test
    void hoversLocalVariableInsideFunctionBody() {
        String source = """
                fn main() {
                    var x : 5;
                    return x;
                }
                """;
        // cursor on "x" in "return x;"
        Position pos = new Position(2, 12);
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var x"));
    }

    @Test
    void hoversVariableInsideNestedIfBlock() {
        String source = """
                fn main() {
                    if (true) {
                        var y : 1;
                        return y;
                    }
                }
                """;
        // cursor on "y" in "return y;"
        Position pos = new Position(3, 16);
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var y"));
    }

    @Test
    void hoversDestructuredVariable() {
        String source = """
                var (a, b) : {1, 2};
                fn main() {
                    return b;
                }
                """;
        Position pos = new Position(2, 12); // "b" in "return b;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var b"));
    }

    @Test
    void hoversShadowedPlainVariableInsideNestedElseBlockToInnerDeclaration() {
        String source = """
                const i : 10;
                if (scan() == ray) {
                    fractals.run();
                } else {
                    var i : 0;
                    do {
                        i++;
                    } while (i < 5);
                }
                """;
        Position pos = new Position(6, 9); // "i" in "i++;" inside the do-while
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var i"));
    }

    @Test
    void hoversShadowedFieldFromInnerDeclarationNotOuter() {
        String source = """
                var obj : { const count : "outer-wrong"; };

                fn helper() {
                    var obj : { var count : 1; };
                    return obj.count;
                }
                """;
        Position pos = new Position(4, 17); // "count" in "return obj.count;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var count"));
    }

    @Test
    void hoverOnUndeclaredNameReturnsNull() {
        String source = """
                fn main() {
                    return doesNotExist;
                }
                """;
        Position pos = new Position(1, 15);
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNull(hover);
    }

    @Test
    void hoversTypedVariableShowsDeclaredType() {
        String source = """
                fn main() {
                    var x : Number : 5;
                    return x;
                }
                """;
        // cursor on "x" in "return x;"
        Position pos = new Position(2, 12);
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var x : Number"));
    }

    @Test
    void hoversTypedFunctionShowsParamAndReturnTypes() {
        String source = """
                fn add(a : Number, b : Number) -> Number {
                    return eval(a + b);
                }
                add(1, 2);
                """;
        Position pos = new Position(3, 1); // "add" in "add(1, 2);"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("a : Number"));
        assertTrue(text(hover).contains("-> Number"));
    }

    @Test
    void hoversStructFieldShowsDeclaredType() {
        String source = """
                var Point : struct { var x : Number : 0; };

                fn helper() {
                    var p : Point{};
                    return p.x;
                }
                """;
        Position pos = new Position(4, 14); // "x" in "return p.x;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var x : Number"));
    }

    @Test
    void hoversParameterInsideItsOwnFunctionBody() {
        String source = """
                fn add(a : Number, b : Number) {
                    return a + b;
                }
                """;
        Position pos = new Position(1, 12); // "a" in "return a + b;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("a : Number"));
    }

    @Test
    void hoversEnumValue() {
        String source = """
                enum Color { RED, GREEN, BLUE }
                fn main() {
                    return Color.RED;
                }
                """;
        Position pos = new Position(2, 18); // "RED" in "Color.RED"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("Color.RED"));
    }

    @Test
    void hoversBareEnumTypeName() {
        String source = """
                enum Color { RED, GREEN, BLUE }
                fn main() {
                    var c : Color : Color.RED;
                }
                """;
        Position pos = new Position(2, 13); // "Color" in "var c : Color : ..."
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("enum Color"));
        assertTrue(text(hover).contains("RED"));
    }

    @Test
    void hoversTypeAliasName() {
        String source = """
                type Id : Number;
                fn main() {
                    var x : Id : 1;
                }
                """;
        Position pos = new Position(2, 13); // "Id" in "var x : Id : 1;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("type Id"));
    }

    @Test
    void hoversNullSafeFieldAccess() {
        String source = """
                var Point : struct { var x : Number : 0; };

                fn helper(p : Point?) {
                    return p?.x;
                }
                """;
        Position pos = new Position(3, 15); // "x" in "return p?.x;"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNotNull(hover);
        assertTrue(text(hover).contains("var x : Number"));
    }

    @Test
    void hoverOnUnresolvedFieldAccessReturnsNull() {
        String source = """
                var obj : { var known : 1; };
                fn main() {
                    return obj.doesNotExist;
                }
                """;
        Position pos = new Position(2, 16); // "doesNotExist" in "obj.doesNotExist"
        Hover hover = HoverProvider.provide(parse(source), source, pos);
        assertNull(hover);
    }

    @Test
    void hoversFunctionFromNamespacedModuleImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet(name) {
                    return name;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.greet("x");
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 17); // "greet" in "lib.greet(...)"
        Hover hover = HoverProvider.provide(parse(source), source, pos, mainPath, null, Map.of());
        assertNotNull(hover);
        assertTrue(text(hover).contains("fn greet"));
    }

    @Test
    void hoversFunctionFromBareSelectiveModuleImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet(name) {
                    return name;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" {greet};
                fn main() {
                    return greet("x");
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 12); // "greet" in "greet("x")"
        Hover hover = HoverProvider.provide(parse(source), source, pos, mainPath, null, Map.of());
        assertNotNull(hover);
        assertTrue(text(hover).contains("fn greet"));
    }

    @Test
    void hoversNativeImportAliasAtItsOwnDeclaration(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("fixture.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/mira/interface.properties"));
            jos.write("GetWidth=->Number\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import native "fixture.jar" as ray;
                fn main() {
                    return ray.GetWidth();
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(0, 32); // "ray" in "... as ray;"
        Hover hover = HoverProvider.provide(parse(source), source, pos, mainPath, null, Map.of());
        assertNotNull(hover);
        assertTrue(text(hover).contains("import native"));
        assertTrue(text(hover).contains("native library"));
        assertTrue(text(hover).contains("1 member"));
    }

    @Test
    void hoversNativeImportAliasWhenJarNotFound() {
        String source = """
                import native "does-not-exist.jar" as ray;
                """;
        Position pos = new Position(0, 40); // "ray" in "... as ray;"
        Hover hover = HoverProvider.provide(parse(source), source, pos,
                java.nio.file.Paths.get("main.mira"), null, Map.of());
        assertNotNull(hover);
        assertTrue(text(hover).contains("jar not found"));
    }

    @Test
    void hoversModuleImportAliasAtItsOwnDeclaration(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet(name) {
                    return name;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.greet("x");
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(0, 29); // "lib" in "... as lib;"
        Hover hover = HoverProvider.provide(parse(source), source, pos, mainPath, null, Map.of());
        assertNotNull(hover);
        assertTrue(text(hover).contains("import module"));
        assertTrue(text(hover).contains("*module*"));
    }
}
