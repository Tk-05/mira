package com.mira.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class DocumentSymbolProviderTest {

    private static List<DocumentSymbol> symbolsOf(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        return DocumentSymbolProvider.provide(ast, source);
    }

    @Test
    void functionAndVar() {
        String source = """
                fn add(a, b) {
                    return $a + $b;
                }
                var x : 5;
                """;
        List<DocumentSymbol> symbols = symbolsOf(source);
        assertEquals(2, symbols.size());
        assertEquals("add", symbols.get(0).getName());
        assertEquals(SymbolKind.Function, symbols.get(0).getKind());
        assertEquals("x", symbols.get(1).getName());
        assertEquals(SymbolKind.Variable, symbols.get(1).getKind());
    }

    @Test
    void destructuredNamesAppearInOutline() {
        String source = """
                var (a, b) : {1, 2};
                """;
        List<DocumentSymbol> symbols = symbolsOf(source);
        assertEquals(2, symbols.size());
        assertEquals("a", symbols.get(0).getName());
        assertEquals("b", symbols.get(1).getName());
        assertEquals(SymbolKind.Variable, symbols.get(0).getKind());
    }

    @Test
    void enumWithMembers() {
        String source = """
                enum Color {
                    RED,
                    GREEN,
                    BLUE
                }
                """;
        List<DocumentSymbol> symbols = symbolsOf(source);
        assertEquals(1, symbols.size());
        DocumentSymbol color = symbols.get(0);
        assertEquals("Color", color.getName());
        assertEquals(SymbolKind.Enum, color.getKind());
        assertEquals(3, color.getChildren().size());
        assertTrue(color.getChildren().stream().anyMatch(c -> c.getName().equals("RED")));
    }

    @Test
    void objectFieldsAndMethods() {
        String source = """
                var obj : {
                    var count : 0;
                    fn increment() {
                        return $count;
                    }
                };
                """;
        List<DocumentSymbol> symbols = symbolsOf(source);
        assertEquals(1, symbols.size());
        DocumentSymbol obj = symbols.get(0);
        assertEquals("obj", obj.getName());
        assertEquals(SymbolKind.Object, obj.getKind());
        assertEquals(2, obj.getChildren().size());
        assertEquals("count", obj.getChildren().get(0).getName());
        assertEquals("increment", obj.getChildren().get(1).getName());
    }

    @Test
    void nestedVarInsideFunctionBecomesChild() {
        String source = """
                fn run() {
                    var i : 0;
                    if (true) {
                        var j : 1;
                    }
                }
                """;
        List<DocumentSymbol> symbols = symbolsOf(source);
        assertEquals(1, symbols.size());
        DocumentSymbol fn = symbols.get(0);
        assertEquals(2, fn.getChildren().size());
        assertEquals("i", fn.getChildren().get(0).getName());
        assertEquals("j", fn.getChildren().get(1).getName());
    }
}
