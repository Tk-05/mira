package com.mira.parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public class ParserTest {

    Tokenizer tokenizer = new Tokenizer();
    Parser parser = new Parser();

    @Test
    void uninitializedVariable() {
        String source = "var x;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source));

        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());

        VarDecl decl = (VarDecl) ast.getFirst();

        assertEquals("x", decl.getName());
        assertNull(decl.getInitializer());
    }

    @Test
    void initializedVariable() {
        String source = "var x : 10;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source));

        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());

        VarDecl decl = (VarDecl) ast.getFirst();

        assertEquals("x", decl.getName());
        assertNotNull(decl.getInitializer());
        assertInstanceOf(DumbExpression.class, decl.getInitializer());

        DumbExpression value = (DumbExpression) decl.getInitializer();

        assertEquals("10", value.getValue());
    }

    @Test
    void stringInitializer() {
        String source = "var name : \"Mira\";";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source));

        VarDecl decl = (VarDecl) ast.getFirst();

        assertEquals("name", decl.getName());
        assertInstanceOf(DumbExpression.class, decl.getInitializer());

        DumbExpression value = (DumbExpression) decl.getInitializer();

        assertEquals("Mira", value.getValue());
    }

    @Test
    void unaryExpression() {
        String unaryExpression = "$val1;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(unaryExpression));

        assertEquals(1, ast.size());

        assertInstanceOf(UnaryExpression.class, ast.get(0));

        UnaryExpression expr = (UnaryExpression) ast.get(0);

        assertEquals("$", expr.getOperation().getLexeme());
        assertNotNull(expr.getRight());
    }

    @Test
    void simpleExpression() {
        String simpleExpression = "((1+2)+3);";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(simpleExpression));

        ComplexExpression outer = (ComplexExpression) ast.get(0);

        assertEquals(3, outer.getExpressions().size());

        assertInstanceOf(ComplexExpression.class, outer.getExpressions().get(0));
    }

    @Test
    void complexExpression() {
        String complexExpression = "((($val1 + $val3) + val()) + 1);";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(complexExpression));

        assertEquals(1, ast.size());

        assertInstanceOf(ComplexExpression.class, ast.get(0));

        ComplexExpression outer = (ComplexExpression) ast.get(0);

        assertEquals(3, outer.getExpressions().size());

        assertInstanceOf(ComplexExpression.class, outer.getExpressions().get(0));

        ComplexExpression inner = (ComplexExpression) outer.getExpressions().get(0);

        assertEquals(3, inner.getExpressions().size());

        assertInstanceOf(CallExpression.class, inner.getExpressions().get(2));
    }

    @Test
    void parseForWithOneVar() {
        String forStmt = """
                for (var i : 0; $i < 10; $i : eval($i + 1)) {
                    print(eval(fibonacci($i)));
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.get(0));
    }

    @Test
    void parseForWithMultipleVars() {
        String forStmt = """
                for (var i : 0, var j : 0; $i < 10 && $j != 0; $i : eval($i + 1)) {
                    print(eval(fibonacci($i)));
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.get(0));
    }

    @Test
    void parseEmptyFor() {
        String forStmt = """
                for (;;) {
                    print("Hello World");
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.get(0));
    }
}
