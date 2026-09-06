package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DefinitionProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void resolvesBareCallFromSelectiveModuleImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn greet() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String source = """
                import module "lib.mira" {greet};
                fn main() {
                    return greet();
                }
                """;
        Files.writeString(mainPath, source);

        Position pos = new Position(2, 12); // "greet" in "return greet();"
        Location loc = DefinitionProvider.provide(parse(source), source, mainPath.toUri().toString(), pos);

        assertNotNull(loc);
        assertEquals(libPath.toUri().toString(), loc.getUri());
    }

    @Test
    void resolvesShadowedFieldToInnerDeclarationNotOuter() {
        String source = """
                var obj : { var count : "outer-wrong"; };

                fn helper() {
                    var obj : { var count : 1; };
                    return obj.count;
                }
                """;
        Position pos = new Position(4, 17); // "count" in "return obj.count;"
        Location loc = DefinitionProvider.provide(parse(source), source, "file:///test.mira", pos);

        assertNotNull(loc);
        assertEquals(3, loc.getRange().getStart().getLine());
    }

    @Test
    void resolvesShadowedPlainVariableInsideNestedElseBlockToInnerDeclaration() {
        String source = """
                var i : 10;
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
        Location loc = DefinitionProvider.provide(parse(source), source, "file:///test.mira", pos);

        assertNotNull(loc);
        assertEquals(4, loc.getRange().getStart().getLine());
    }

    @Test
    void resolvesDestructuredVariableUse() {
        String source = """
                var (a, b) : {1, 2};
                fn main() {
                    return b;
                }
                """;
        Position pos = new Position(2, 12); // "b" in "return b;"
        Location loc = DefinitionProvider.provide(parse(source), source, "file:///test.mira", pos);

        assertNotNull(loc);
        assertEquals(0, loc.getRange().getStart().getLine());
    }
}
