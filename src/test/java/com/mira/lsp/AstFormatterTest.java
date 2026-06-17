package com.mira.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

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
    void functionDecl() {
        String source = """
                fn add(a, b) {
                    return $a + $b;
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
                    while ($i < 10) {
                        $i++;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void forLoop() {
        String source = """
                fn test() {
                    for (var i : 0; $i < 10; $i++) {
                        var x : $i;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void foreachLoop() {
        String source = """
                fn test() {
                    foreach (var x in {1, 2, 3}) {
                        var y : $x;
                    }
                }
                """;
        assertEquals(source, fmt(source));
    }

    @Test
    void switchStmt() {
        String source = """
                fn test(x) {
                    switch ($x) {
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
    void arrowLambdaCanonicalForm() {
        assertEquals("var f : (x) -> $x + 1;\n", fmt("var f : fn (x) { return $x + 1; };"));
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
                    return $a + $b;
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
                    if ($b != 0) {
                        return $a / $b;
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
    void blankLineBetweenFunctions() {
        String input = """
                fn a() {}
                fn b() {}
                """;
        String expected = """
                fn a() {}

                fn b() {}
                """;
        assertEquals(expected, fmt(input));
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
}
