package com.mira.format;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;

public class AstFormatterTest {

    private static String fmt(String source) {
        return AstFormatter.format(source);
    }

    @Test
    void varDecl() {
        assertEquals("var x : 5;\n", fmt("var x : 5;"));
    }

    @Test
    void constDecl() {
        assertEquals("const PI : 3;\n", fmt("const PI : 3;"));
    }

    @Test
    void postfixIncrementStaysPostfix() {
        assertEquals("x++;\n", fmt("x++;"));
    }

    @Test
    void prefixIncrementStaysPrefix() {
        assertEquals("++x;\n", fmt("++x;"));
    }

    @Test
    void prefixDecrementStaysPrefix() {
        assertEquals("--x;\n", fmt("--x;"));
    }

    @Test
    void functionDecl() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void ifElse() {
        String source = """
                fn test() {
                    if (true) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void whileLoop() {
        String source = """
                fn test() {
                    var i : 0;
                    while (i < 10) {
                        i++;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void forLoop() {
        String source = """
                fn test() {
                    for (var i : 0; i < 10; i++) {
                        var x : i;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void foreachLoop() {
        String source = """
                fn test() {
                    for (var x in {1, 2, 3}) {
                        var y : x;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void forRangeSugarRoundTrips() {
        String source = """
                fn test() {
                    for (<0..5>) {
                        print(1);
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void forVarInRangeSugarRoundTrips() {
        String source = """
                fn test() {
                    for (var i in <0..5>) {
                        print(1);
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void switchStmt() {
        String source = """
                fn test(x) {
                    switch (x) {
                        case (1) {
                            return 1;
                        }
                        default {
                            return 0;
                        }
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void tryCatch() {
        String source = """
                fn test() {
                    try {
                        var x : 1;
                    } catch (e) {
                        var y : 2;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void fnLambdaPreserved() {
        assertEquals("var f : fn (x) {\n    return x + 1;\n};\n", fmt("var f : fn (x) { return x + 1; };"));
    }

    @Test
    void arrowLambdaPreserved() {
        assertEquals("var f : (x) -> x + 1;\n", fmt("var f : (x) -> x + 1;"));
    }

    @Test
    void doWhile() {
        String source = """
                fn test() {
                    do {
                        var x : 1;
                    } while (true);
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void idempotent() {
        String source = """
                fn add(a, b) {
                    return a + b;
                }

                fn main() {
                    var result : add(1, 2);
                }
                """;
        String once = fmt(source);
        assertEquals(once, fmt(once));
    }

    @Test
    void inlineCommentOnSingleLineStatement() {
        String source = """
                fn foo() {
                    var x : 5; // my var
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void inlineCommentOnOpeningBrace() {
        String source = """
                fn divide(a, b) { // validates input
                    if (b != 0) {
                        return a / b;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void inlineCommentOnClosingBrace() {
        String source = """
                fn foo() {
                    return 1;
                } // returns 1
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void standaloneCommentInBody() {
        String source = """
                fn foo() {
                    var a : 1;

                    // prepare result
                    var x : 42;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void standaloneCommentBetweenTopLevel() {
        String source = """
                fn a() {
                    return 1;
                }

                // helper function
                fn b() {
                    return 2;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void headerComment() {
        String source = """
                // file header
                fn foo() {
                    return 1;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void blankLineBetweenFunctionsPreservedWhenPresent() {
        String source = """
                fn a() {}

                fn b() {}
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void adjacentTopLevelStatementsStayAdjacent() {
        String source = """
                fn a() {}
                fn b() {}
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void adjacentVarDeclsStayAdjacent() {
        String source = """
                var a;
                var b;
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void blankLineInsideBodyPreserved() {
        String source = """
                fn foo() {
                    var a : 1;

                    var b : 2;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void noSpuriousBlankLineAfterMultiLineVarDecl() {
        String input = """
                fn foo() {
                    var x : add(
                        1,
                        2
                    );
                    return x;
                }
                """;
        String expected = """
                fn foo() {
                    var x : add(1, 2);
                    return x;
                }
                """;
        assertEquals(expected, fmt(input));
    }

    @Test
    void blankLinePreservedAfterMultiLineVarDecl() {
        String input = """
                fn foo() {
                    var x : add(
                        1,
                        2
                    );

                    return x;
                }
                """;
        String expected = """
                fn foo() {
                    var x : add(1, 2);

                    return x;
                }
                """;
        assertEquals(expected, fmt(input));
    }

    @Test
    void noSpuriousBlankLineAfterIfBlock() {
        String source = """
                fn foo() {
                    if (true) {
                        return 1;
                    }
                    return 2;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void blankLinePreservedAfterIfBlock() {
        String source = """
                fn foo() {
                    if (true) {
                        return 1;
                    }

                    return 2;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void lineCommentBeforeFirstStatement() {
        String source = """
                fn foo() {
                    // init
                    return 1;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void blockCommentBeforeFirstStatement() {
        String source = """
                fn foo() {
                    /* init */
                    return 1;
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void blockCommentOnSameLineAsStatement() {
        String input = """
                fn foo() {
                    /* setup */ return 1;
                }
                """;
        String expected = """
                fn foo() {
                    /* setup */
                    return 1;
                }
                """;
        assertEquals(expected, fmt(input));
    }

    @Test
    void enumExplicitValuesRoundTrip() {
        String source = """
                enum Color {
                    RED,
                    GREEN : "g",
                    BLUE
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void varDestructureRoundTrip() {
        String source = """
                var (a, b) : {1, 2};
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void varDestructureIsIdempotent() {
        String once = fmt("var (a, b) : {1, 2};");
        assertEquals(once, fmt(once));
    }

    @Test
    void mapKeyWithEscapedQuoteRoundTrips() {
        String source = """
                var m : {"a\\"b" : 1};
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void ternaryAsConditionKeepsParens() {
        String source = """
                var x : (1 ? 2 : 3) ? 4 : 5;
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void ternaryInElsePositionNeedsNoParens() {
        // right-associative by grammar: a ? b : c ? d : e == a ? b : (c ? d : e)
        String source = """
                var x : 1 ? 2 : 3 ? 4 : 5;
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void commentInEmptyFunctionBodyIsPreserved() {
        String source = """
                fn foo() {
                    // TODO implement
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void trailingCommentBeforeClosingBraceIsPreserved() {
        String source = """
                fn foo() {
                    var x : 1;
                    print(x);
                    // trailing note
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void multilineStringEndingInEscapedQuoteDoesNotCorruptOutput() {
        String source = """
                fn foo() {
                    var s : "line1\\nline2 ends with quote\\"";
                }
                """;
        String formatted = fmt(source);
        assertEquals(source, formatted);
        assertDoesNotThrow(() -> new Parser().parseTokens(new Tokenizer().tokenize(formatted, false)));
    }

    @Test
    void commentBetweenObjectLiteralMembersIsPreserved() {
        String source = """
                var obj : {
                    var x : 1;
                    // method comment
                    fn get() {
                        return x;
                    }
                };
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void nestedObjectLiteralFieldDoesNotInsertSpuriousBlankLine() {
        String source = """
                var wrapper : {
                    var a : {
                        var a : 0;
                        var b : 0;
                    };
                    var b : 0;
                };
                """;
        assertEquals(source, fmt(source));
    }
}
