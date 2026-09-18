package com.mira.runtime.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mira.format.AstWalker;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;

/**
 * Phase 1 of the resolver/slot-based-scope architecture: verifies the Resolver
 * computes correct (distance, slot) pairs - or leaves a reference UNRESOLVED -
 * directly against real parser output, not hand-built AST nodes. Nothing in the
 * interpreter consumes this yet (see the plan); these tests exist purely to
 * validate the resolver's own output before anything depends on it.
 */
public class ResolverTest {

    private static List<Node> parse(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        Resolver.resolve(ast);
        return ast;
    }

    /**
     * Every "$name" reference to {@code varName} anywhere in the AST, in traversal
     * order.
     */
    private static List<UnaryExpression> findReferences(List<Node> ast, String varName) {
        List<UnaryExpression> found = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node instanceof UnaryExpression u && "$".equals(u.getOperation().getLexeme())
                    && u.getRight() instanceof DumbExpression d && d.getValue().equals(varName)) {
                found.add(u);
            }
            AstWalker.children(node, queue);
        }
        return found;
    }

    private static UnaryExpression singleReference(List<Node> ast, String varName) {
        List<UnaryExpression> refs = findReferences(ast, varName);
        assertEquals(1, refs.size(), "expected exactly one reference to " + varName);
        return refs.get(0);
    }

    @Test
    void simpleLocalReadResolves() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var x : 1;
                    x;
                }
                """);
        UnaryExpression ref = singleReference(ast, "x");
        assertTrue(ref.isResolved());
        assertEquals(0, ref.resolvedDistance);
        assertEquals(0, ref.resolvedSlot);
    }

    @Test
    void assignmentTargetResolvesToo() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var x : 1;
                    x : 2;
                }
                """);
        List<UnaryExpression> refs = findReferences(ast, "x");
        assertEquals(1, refs.size());
        assertTrue(refs.get(0).isResolved());
        assertEquals(0, refs.get(0).resolvedDistance);
    }

    @Test
    void shadowingInNestedBlockResolvesToInnerSlot() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var x : 1;
                    {
                        var x : 2;
                        x;
                    }
                }
                """);
        UnaryExpression ref = singleReference(ast, "x");
        assertTrue(ref.isResolved());
        // The inner block is a fresh scope one level deeper than the function's own
        // scope, and the reference is inside that inner block, so distance 0 must
        // land on the *inner* x, not the outer one.
        assertEquals(0, ref.resolvedDistance);
    }

    @Test
    void referenceBeforeAndAfterNestedBlockSeesOuterSlot() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var x : 1;
                    x;
                    {
                        var y : 2;
                    }
                    x;
                }
                """);
        List<UnaryExpression> refs = findReferences(ast, "x");
        assertEquals(2, refs.size());
        for (UnaryExpression ref : refs) {
            assertTrue(ref.isResolved());
            assertEquals(0, ref.resolvedDistance, "both outer references must resolve at the function's own scope");
        }
    }

    @Test
    void forLoopCounterResolvesInConditionAndPostExpression() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    for (var i : 0; i < 10; i++) {
                    }
                }
                """);
        List<UnaryExpression> refs = findReferences(ast, "i");
        // condition read + post-expression's read-then-write both reference "i" by
        // name at least once each.
        assertTrue(refs.size() >= 2, "expected at least a condition read and a post-expression reference");
        for (UnaryExpression ref : refs) {
            assertTrue(ref.isResolved(), "every reference to the for-loop counter must resolve");
        }
    }

    @Test
    void forLoopCounterCapturedByLambdaInBodyResolves() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    for (var i : 0; i < 3; i++) {
                        var capture : () -> i;
                    }
                }
                """);
        // "i" also appears in the header's condition and post-expression - every
        // occurrence, including the one captured by the lambda, must resolve.
        List<UnaryExpression> refs = findReferences(ast, "i");
        assertTrue(refs.size() >= 3, "expected condition + post-expression + lambda-capture references");
        for (UnaryExpression ref : refs) {
            assertTrue(ref.isResolved(), "every reference to the for-loop counter must resolve, including the one "
                    + "captured by the lambda in the body");
        }
    }

    @Test
    void whileLoopBodyLocalCapturedByNestedLambdaResolves() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    while (true) {
                        var x : 1;
                        var capture : () -> x;
                    }
                }
                """);
        UnaryExpression ref = singleReference(ast, "x");
        assertTrue(ref.isResolved());
    }

    @Test
    void foreachIteratorResolves() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    for (var item in [1, 2, 3]) {
                        item;
                    }
                }
                """);
        UnaryExpression ref = singleReference(ast, "item");
        assertTrue(ref.isResolved());
    }

    @Test
    void functionParameterResolves() {
        List<Node> ast = parse("""
                module m;
                fn f(a) {
                    a;
                }
                """);
        UnaryExpression ref = singleReference(ast, "a");
        assertTrue(ref.isResolved());
        assertEquals(0, ref.resolvedDistance);
        assertEquals(0, ref.resolvedSlot);
    }

    @Test
    void lambdaClosingOverOuterFunctionLocalResolves() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var outer : 1;
                    var l : () -> outer;
                }
                """);
        UnaryExpression ref = singleReference(ast, "outer");
        assertTrue(ref.isResolved());
        // The lambda's own scope is one level deeper than the function's scope
        // where "outer" is declared.
        assertEquals(1, ref.resolvedDistance);
    }

    @Test
    void structMethodReferencingOwnFieldByBareNameStaysUnresolved() {
        List<Node> ast = parse("""
                module m;
                fn f() {
                    var s : struct {
                        var count : 0;
                        fn get() {
                            count;
                        }
                    };
                }
                """);
        UnaryExpression ref = singleReference(ast, "count");
        assertFalse(ref.isResolved(),
                "a struct method's own field, referenced by bare name, must stay unresolved so the "
                        + "existing dynamic instance-field fallback handles it");
    }

    @Test
    void topLevelReferenceToUndeclaredGlobalStaysUnresolved() {
        List<Node> ast = parse("""
                module m;
                someGlobal;
                """);
        UnaryExpression ref = singleReference(ast, "someGlobal");
        assertFalse(ref.isResolved());
    }

    @Test
    void topLevelVariableIsNeverResolverManaged() {
        List<Node> ast = parse("""
                module m;
                var x : 1;
                x;
                """);
        UnaryExpression ref = singleReference(ast, "x");
        assertFalse(ref.isResolved(), "top-level/module scope always stays name-based, never resolver-managed");
    }
}
