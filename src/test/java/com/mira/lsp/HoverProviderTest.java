package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

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
}
