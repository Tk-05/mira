package com.mira.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
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
        // only the selected function should be suggested, not every public function in the module
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
        // "$point"/"$point.field" member-access forms - must be suggested
        String source = """
                var point : struct {
                    var x;
                    var y;
                };
                """;
        List<CompletionItem> items = CompletionProvider.provide(parse(source), "file:///test.mira");
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("point")));
        // the field-access forms should still be offered too, just not exclusively
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("$point.x")));
    }
}
