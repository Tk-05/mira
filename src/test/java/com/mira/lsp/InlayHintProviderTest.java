package com.mira.lsp;

import java.util.List;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class InlayHintProviderTest {

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    private static final Range WHOLE_FILE = new Range(new Position(0, 0), new Position(9999, 0));

    @Test
    void hintsNumberLiteral() {
        List<InlayHint> hints = InlayHintProvider.provide(parse("var x : 5;\n"), "var x : 5;\n", WHOLE_FILE);
        assertEquals(1, hints.size());
        assertEquals(": Number", hints.get(0).getLabel().getLeft());
        assertEquals(new Position(0, 5), hints.get(0).getPosition());
    }

    @Test
    void hintsStringLiteral() {
        String source = "var s : \"hi\";\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(1, hints.size());
        assertEquals(": String", hints.get(0).getLabel().getLeft());
    }

    @Test
    void noHintForBarewordVariableReference() {
        // A plain identifier always parses as a variable reference (the
        // parser wraps it internally), never as a literal - "missing '$' is a
        // string" is a *runtime* fallback for when that reference doesn't
        // resolve, invisible at parse time, so this can't be inferred
        // statically without full scope resolution.
        String source = "var s : hello;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(0, hints.size());
    }

    @Test
    void hintsBoolLiteral() {
        String source = "var b : true;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Bool", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsNullLiteral() {
        String source = "var n : null;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Null", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsNegativeNumber() {
        String source = "var n : -5;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Number", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsInvertedBool() {
        String source = "var n : !true;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Bool", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsArrayExpression() {
        String source = "var a : [1, 2, 3];\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Array", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsListExpression() {
        String source = "var a : {1, 2, 3};\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": List", hints.get(0).getLabel().getLeft());
    }

    @Test
    void hintsObjectLiteral() {
        String source = """
                var o : {
                    var x : 1;
                };
                """;
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        // one for "o" (Object) and one for the nested field "x" (Number)
        assertEquals(2, hints.size());
        assertTrue(hints.stream().anyMatch(h -> ": Object".equals(h.getLabel().getLeft())));
        assertTrue(hints.stream().anyMatch(h -> ": Number".equals(h.getLabel().getLeft())));
    }

    @Test
    void hintsLambda() {
        String source = "var f : fn (x) { return x; };\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(": Fn", hints.get(0).getLabel().getLeft());
    }

    @Test
    void noHintWhenTypeAlreadyExplicit() {
        String source = "var x : Number : 5;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(0, hints.size());
    }

    @Test
    void noHintForVariableReference() {
        String source = """
                var a : 1;
                var b : a;
                """;
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        // "a" gets a hint (Number), "b" doesn't (never chases a reference)
        assertEquals(1, hints.size());
        assertEquals(": Number", hints.get(0).getLabel().getLeft());
    }

    @Test
    void noHintForFunctionCallResult() {
        String source = """
                fn make() {
                    return 1;
                }
                var x : make();
                """;
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(0, hints.size());
    }

    @Test
    void noHintForDeclarationWithoutInitializer() {
        String source = "var x;\n";
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, WHOLE_FILE);
        assertEquals(0, hints.size());
    }

    @Test
    void respectsRequestedRange() {
        String source = """
                var a : 1;
                var b : 2;
                var c : 3;
                """;
        Range onlyMiddleLine = new Range(new Position(1, 0), new Position(1, 99));
        List<InlayHint> hints = InlayHintProvider.provide(parse(source), source, onlyMiddleLine);
        assertEquals(1, hints.size());
        assertEquals(1, hints.get(0).getPosition().getLine());
    }
}
