package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class ReferenceProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void findsAllUsesOfLocalVariable() {
        String source = """
                fn add(a, b) {
                    var sum : $a + $b;
                    return $sum;
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(1, 9);
        List<Location> refs = ReferenceProvider.provide(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of(), true);
        assertEquals(2, refs.size());
    }

    @Test
    void findsFunctionCallSitesInSameFile() {
        String source = """
                fn helper() {
                    return 1;
                }
                fn main() {
                    return helper();
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(0, 4);
        List<Location> refs = ReferenceProvider.provide(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of(), true);
        assertEquals(2, refs.size());
    }

    @Test
    void findsCrossFileNamespaceCalls(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.helper();
                }
                """);

        List<Node> libAst = parse(Files.readString(libPath));
        WorkspaceIndex index = new WorkspaceIndex();
        Position pos = new Position(0, 8);
        String libUri = libPath.toUri().toString();
        List<Location> refs = ReferenceProvider.provide(libAst, Files.readString(libPath), pos, libUri,
                libPath, index, tempDir, Map.of(), true);

        assertEquals(2, refs.size());
        assertTrue(refs.stream().anyMatch(l -> l.getUri().equals(libUri)));
        assertTrue(refs.stream().anyMatch(l -> l.getUri().equals(mainPath.toUri().toString())));
    }
}
