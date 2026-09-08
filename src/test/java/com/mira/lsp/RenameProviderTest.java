package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class RenameProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void renamesLocalVariableEverywhere() {
        String source = """
                fn add(a, b) {
                    var sum : a + b;
                    return sum;
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(1, 9);
        WorkspaceEdit edit = RenameProvider.rename(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of(), "total");
        assertNotNull(edit);
        List<TextEdit> edits = edit.getChanges().get("file:///test.mira");
        assertEquals(2, edits.size());
        assertTrue(edits.stream().allMatch(e -> e.getNewText().equals("total")));
    }

    @Test
    void prepareRenameAcceptsFieldAccess() {
        String source = """
                var obj : {
                    var count : 0;
                };
                fn use() {
                    return obj.count;
                }
                """;
        List<Node> ast = parse(source);
        // cursor on "count" in "obj.count"
        Position pos = new Position(4, 18);
        Range range = RenameProvider.prepareRename(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of());
        assertNotNull(range);
    }

    @Test
    void renamingFieldUpdatesAccessSitesButNotTheDeclaration() {
        // Known limitation: field references are found via a lexical .name scan
        // (no type inference), so declarations aren't included - only the
        // .name access site(s) are renamed. Documented here rather than
        // silently regressed if the underlying scan changes.
        String source = """
                var obj : {
                    var count : 0;
                };
                fn use() {
                    return obj.count;
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(4, 18);
        WorkspaceEdit edit = RenameProvider.rename(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of(), "total");
        assertNotNull(edit);
        List<TextEdit> edits = edit.getChanges().get("file:///test.mira");
        assertEquals(1, edits.size());
        assertEquals("total", edits.get(0).getNewText());
    }

    @Test
    void prepareRenameAcceptsFunctionName() {
        String source = """
                fn helper() {
                    return 1;
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(0, 4);
        Range range = RenameProvider.prepareRename(ast, source, pos, "file:///test.mira",
                null, new WorkspaceIndex(), null, Map.of());
        assertNotNull(range);
    }

    @Test
    void renamesCrossFileNamespaceCalls(@TempDir Path tempDir) throws IOException {
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
        WorkspaceEdit edit = RenameProvider.rename(libAst, Files.readString(libPath), pos, libUri,
                libPath, index, tempDir, Map.of(), "assist");

        assertNotNull(edit);
        assertEquals(2, edit.getChanges().size());
        assertTrue(edit.getChanges().containsKey(libUri));
        assertTrue(edit.getChanges().containsKey(mainPath.toUri().toString()));
    }

    @Test
    void renamesCrossFileDirectCallsForSelectiveImport(@TempDir Path tempDir) throws IOException {
        Path libPath = tempDir.resolve("lib.mira");
        Files.writeString(libPath, """
                pub fn helper() {
                    return 1;
                }
                """);
        Path mainPath = tempDir.resolve("main.mira");
        Files.writeString(mainPath, """
                import module "lib.mira" {helper};
                fn main() {
                    return helper();
                }
                """);

        List<Node> libAst = parse(Files.readString(libPath));
        WorkspaceIndex index = new WorkspaceIndex();
        Position pos = new Position(0, 8);
        String libUri = libPath.toUri().toString();
        WorkspaceEdit edit = RenameProvider.rename(libAst, Files.readString(libPath), pos, libUri,
                libPath, index, tempDir, Map.of(), "assist");

        assertNotNull(edit);
        assertEquals(2, edit.getChanges().size());
        assertTrue(edit.getChanges().containsKey(libUri));
        assertTrue(edit.getChanges().containsKey(mainPath.toUri().toString()));
    }

    private static String source() {
        return """
                fn add(a, b) {
                    var sum : a + b;
                    return sum;
                }
                """;
    }

    @Test
    void rejectsNameStartingWithDigit() {
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "123total"));
        assertTrue(ex.getMessage().contains("must start with a letter"));
    }

    @Test
    void rejectsNameWithInvalidCharacters() {
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "my var"));
        assertTrue(ex.getMessage().contains("only letters, digits, and '_'"));
    }

    @Test
    void rejectsBlankName() {
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), ""));
        assertTrue(ex.getMessage().contains("must not be empty"));
    }

    @Test
    void rejectsReservedKeyword() {
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "fn"));
        assertTrue(ex.getMessage().contains("reserved keyword"));
    }

    @Test
    void acceptsUnderscorePrefixedName() {
        WorkspaceEdit edit = RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "_total");
        assertNotNull(edit);
    }

    @Test
    void rejectsRenameThatCollidesWithParameterAlreadyInScope() {
        // "sum" -> "a" would collide with the existing parameter "a".
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "a"));
        assertTrue(ex.getMessage().contains("already declared in this scope"));
    }

    @Test
    void allowsRenameToItsOwnCurrentName() {
        WorkspaceEdit edit = RenameProvider.rename(parse(source()), source(), new Position(1, 9),
                "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "sum");
        assertNotNull(edit);
    }

    @Test
    void rejectsFieldRenameThatCollidesWithExistingSiblingField() {
        String source = """
                var obj : {
                    var count : 0;
                    var total : 0;
                };
                fn use() {
                    return obj.count;
                }
                """;
        List<Node> ast = parse(source);
        Position pos = new Position(5, 18); // "count" in "obj.count"
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(ast, source, pos, "file:///test.mira",
                        null, new WorkspaceIndex(), null, Map.of(), "total"));
        assertTrue(ex.getMessage().contains("already exists on this type"));
    }

    @Test
    void rejectsRenameWhereNoWordSitsUnderTheCursor() {
        RenameProvider.RenameRejectedException ex = assertThrows(RenameProvider.RenameRejectedException.class,
                () -> RenameProvider.rename(parse(source()), source(), new Position(0, 0),
                        "file:///test.mira", null, new WorkspaceIndex(), null, Map.of(), "renamed"));
        assertTrue(ex.getMessage().contains("Cannot find any references"));
    }
}
