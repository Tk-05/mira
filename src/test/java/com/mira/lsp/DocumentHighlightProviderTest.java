package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DocumentHighlightProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void highlightsAllUsesOfLocalVariable() {
        String source = """
                fn add(a, b) {
                    var sum : a + b;
                    return sum;
                }
                """;
        Position pos = new Position(1, 9); // "sum" in "var sum : a + b;"
        List<DocumentHighlight> highlights = DocumentHighlightProvider.provide(parse(source), source, pos,
                "file:///test.mira");
        assertEquals(2, highlights.size());
    }

    @Test
    void highlightsFieldAccessOccurrencesOnly() {
        String source = """
                var obj : {
                    var size : 0;
                };
                fn use() {
                    // resize the .size cache
                    var msg : "file.size";
                    return obj.size;
                }
                """;
        Position pos = new Position(6, 17); // "size" in "obj.size" (real field access)
        List<DocumentHighlight> highlights = DocumentHighlightProvider.provide(parse(source), source, pos,
                "file:///test.mira");
        assertEquals(1, highlights.size());
        assertEquals(6, highlights.get(0).getRange().getStart().getLine());
    }

    @Test
    void doesNotReachAcrossFiles(@TempDir Path tempDir) throws IOException {
        // Document highlight is scoped to the current file - a cross-file
        // symbol with the same name in another module must not appear, even
        // though ReferenceProvider (which this delegates to) supports
        // cross-file search when given a workspace root.
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        String mainSource = """
                import module "lib.mira" as lib;
                fn main() {
                    return lib.helper();
                }
                """;
        Files.writeString(mainPath, mainSource);

        Position pos = new Position(2, 15); // "helper" in "lib.helper()"
        List<DocumentHighlight> highlights = DocumentHighlightProvider.provide(parse(mainSource), mainSource, pos,
                mainPath.toUri().toString());
        assertTrue(highlights.size() <= 1);
    }
}
