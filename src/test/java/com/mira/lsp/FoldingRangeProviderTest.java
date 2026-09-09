package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.FoldingRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class FoldingRangeProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void foldsFunctionBody() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                """;
        List<FoldingRange> ranges = FoldingRangeProvider.provide(parse(source));
        assertEquals(1, ranges.size());
        assertEquals(0, ranges.get(0).getStartLine());
        assertEquals(2, ranges.get(0).getEndLine());
    }

    @Test
    void foldsNestedConstructsIndependently() {
        String source = """
                fn main() {
                    if (true) {
                        println("yes");
                    }
                }
                """;
        List<FoldingRange> ranges = FoldingRangeProvider.provide(parse(source));
        // one region for the function, one for the if
        assertEquals(2, ranges.size());
        assertTrue(ranges.stream().anyMatch(r -> r.getStartLine() == 0 && r.getEndLine() == 4));
        assertTrue(ranges.stream().anyMatch(r -> r.getStartLine() == 1 && r.getEndLine() == 3));
    }

    @Test
    void foldsStructLiteralAndItsMethods() {
        String source = """
                var Point : struct {
                    var x;
                    fn dist() {
                        return x;
                    }
                };
                """;
        List<FoldingRange> ranges = FoldingRangeProvider.provide(parse(source));
        // one region for the struct literal, one for its method body
        assertEquals(2, ranges.size());
    }

    @Test
    void singleLineConstructsProduceNoFoldingRange() {
        String source = "fn f() { return 1; }\n";
        List<FoldingRange> ranges = FoldingRangeProvider.provide(parse(source));
        assertEquals(0, ranges.size());
    }
}
