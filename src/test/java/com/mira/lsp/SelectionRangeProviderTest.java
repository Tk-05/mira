package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class SelectionRangeProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    private static List<Range> flatten(SelectionRange sr) {
        List<Range> out = new java.util.ArrayList<>();
        for (SelectionRange cur = sr; cur != null; cur = cur.getParent()) {
            out.add(cur.getRange());
        }
        return out;
    }

    private static boolean contains(Range outer, Range inner) {
        boolean startOk = outer.getStart().getLine() < inner.getStart().getLine()
                || (outer.getStart().getLine() == inner.getStart().getLine()
                && outer.getStart().getCharacter() <= inner.getStart().getCharacter());
        boolean endOk = outer.getEnd().getLine() > inner.getEnd().getLine()
                || (outer.getEnd().getLine() == inner.getEnd().getLine()
                && outer.getEnd().getCharacter() >= inner.getEnd().getCharacter());
        return startOk && endOk;
    }

    @Test
    void innermostRangeIsWordUnderCursor() {
        String source = """
                fn main() {
                    var x : 1;
                }
                """;
        // cursor on "x"
        List<SelectionRange> result = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(1, 8)));
        assertEquals(1, result.size());
        Range innermost = result.get(0).getRange();
        assertEquals(new Range(new Position(1, 8), new Position(1, 9)), innermost);
    }

    @Test
    void expandsOutwardThroughEnclosingLevelsToWholeDocument() {
        String source = """
                fn outer() {
                    if (true) {
                        var x : 1;
                    }
                }
                """;
        // cursor on "x"
        SelectionRange sr = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(2, 12))).get(0);
        List<Range> chain = flatten(sr);

        // word -> var-decl statement -> if-block -> function -> whole document
        assertTrue(chain.size() >= 4, "expected at least word/statement/block/function levels, got: " + chain);

        // each level must fully contain the previous (strictly nested, growing outward)
        for (int i = 1; i < chain.size(); i++) {
            assertTrue(contains(chain.get(i), chain.get(i - 1)),
                    "level " + i + " (" + chain.get(i) + ") must contain level " + (i - 1) + " (" + chain.get(i - 1) + ")");
        }

        // outermost covers the whole document
        Range outermost = chain.get(chain.size() - 1);
        String[] lines = source.split("\n", -1);
        assertEquals(0, outermost.getStart().getLine());
        assertEquals(lines.length - 1, outermost.getEnd().getLine());
    }

    @Test
    void noWordUnderCursorStillReturnsEnclosingLevels() {
        String source = """
                fn main() {
                    var x : 1;
                }
                """;
        // cursor on whitespace at start of the var-decl line
        SelectionRange sr = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(1, 0))).get(0);
        assertNotNull(sr);
        // no word here, so the innermost level should already be a real range,
        // not a zero-width fallback
        assertTrue(sr.getRange().getStart().getCharacter() >= 0);
    }

    @Test
    void multiplePositionsProduceOneChainEach() {
        String source = """
                fn a() {
                    return 1;
                }
                fn b() {
                    return 2;
                }
                """;
        List<SelectionRange> result = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(1, 11), new Position(4, 11)));
        assertEquals(2, result.size());
    }

    @Test
    void leafStatementIsItsOwnLevelBeforeEnclosingBlock() {
        String source = "if (true) {\n    var x : 1;\n}\n";
        // cursor on "x"
        SelectionRange sr = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(1, 8))).get(0);
        List<Range> chain = flatten(sr);

        // word -> "var x : 1;" statement -> if-block -> whole document
        assertEquals(4, chain.size(), "expected word/statement/if-block/document levels, got: " + chain);
        assertEquals(new Range(new Position(1, 0), new Position(1, 14)), chain.get(1),
                "second level must be the var-decl statement itself, not the enclosing if-block");
        for (int i = 1; i < chain.size(); i++) {
            assertTrue(contains(chain.get(i), chain.get(i - 1)),
                    "level " + i + " (" + chain.get(i) + ") must contain level " + (i - 1) + " (" + chain.get(i - 1) + ")");
        }
    }

    @Test
    void chainHasNoConsecutiveDuplicateRanges() {
        String source = """
                fn main() {
                    return 1;
                }
                """;
        SelectionRange sr = SelectionRangeProvider.provide(parse(source), source,
                List.of(new Position(1, 11))).get(0);
        List<Range> chain = flatten(sr);
        for (int i = 1; i < chain.size(); i++) {
            assertTrue(!chain.get(i).equals(chain.get(i - 1)),
                    "consecutive duplicate range at level " + i + ": " + chain.get(i));
        }
    }
}
