package com.mira.parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public class ParserTest {

    Tokenizer tokenizer = new Tokenizer();
    Parser parser = new Parser();

    @Test
    void uninitializedVariable() {
        String source = "var x;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));

        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());

        VarDecl decl = (VarDecl) ast.getFirst();

        assertEquals("x", decl.getName());
        assertNull(decl.getInitializer());
    }

    @Test
    void initializedVariable() {
        String source = "var x : 10;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));

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

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));

        VarDecl decl = (VarDecl) ast.getFirst();

        assertEquals("name", decl.getName());
        assertInstanceOf(DumbExpression.class, decl.getInitializer());

        DumbExpression value = (DumbExpression) decl.getInitializer();

        assertEquals("Mira", value.getValue());
    }

    @Test
    void unaryExpression() {
        String unaryExpression = "$val1;";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(unaryExpression, false));

        assertEquals(1, ast.size());

        assertInstanceOf(UnaryExpression.class, ast.getFirst());

        UnaryExpression expr = (UnaryExpression) ast.getFirst();

        assertEquals("$", expr.getOperation().getLexeme());
        assertNotNull(expr.getRight());
    }

    @Test
    void simpleExpression() {
        String simpleExpression = "((1+2)+3);";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(simpleExpression, false));

        BinaryExpression outer = assertInstanceOf(BinaryExpression.class, ast.getFirst());

        assertInstanceOf(BinaryExpression.class, outer.getLeft());
        assertEquals("+", outer.getOperator().getLexeme());
    }

    @Test
    void complexExpression() {
        String complexExpression = "((($val1 + $val3) + val()) + 1);";

        List<Node> ast = parser.parseTokens(tokenizer.tokenize(complexExpression, false));

        assertEquals(1, ast.size());

        BinaryExpression outer = assertInstanceOf(BinaryExpression.class, ast.getFirst());
        assertEquals("+", outer.getOperator().getLexeme());

        BinaryExpression mid = assertInstanceOf(BinaryExpression.class, outer.getLeft());
        assertEquals("+", mid.getOperator().getLexeme());

        assertInstanceOf(BinaryExpression.class, mid.getLeft());
        assertInstanceOf(CallExpression.class, mid.getRight());
    }

    @Test
    void parseForWithOneVar() {
        String forStmt = """
                for (var i : 0; $i < 10; $i : eval($i + 1)) {
                    print(eval(fibonacci($i)));
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.getFirst());
    }

    @Test
    void parseForWithMultipleVars() {
        String forStmt = """
                for (var i : 0, var j : 0; $i < 10 && $j != 0; $i : eval($i + 1)) {
                    print(eval(fibonacci($i)));
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.getFirst());
    }

    @Test
    void parseEmptyFor() {
        String forStmt = """
                for (;;) {
                    print("Hello World");
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(For.class, ast.getFirst());
    }

    @Test
    void parseFuncDecl() {
        String funcDecl = """
                fn test() {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(funcDecl, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
    }

    @Test
    void parseReturn() {
        String retStmt = """
                ret();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(retStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Statement.Return.class, ast.getFirst());
    }

    @Test
    void parseIf() {
        String ifStmt = """
                if(1){} else {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(ifStmt, false));
        assertInstanceOf(Statement.If.class, ast.getFirst());
    }

    @Test
    void parseWhile() {
        String whileStmt = """
                while(1){}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(whileStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Statement.While.class, ast.getFirst());
    }

    @Test
    void parseEmptyCall() {
        String callExpression = """
                test();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(CallExpression.class, ast.getFirst());
    }

    @Test
    void parseCall() {
        String callExpression = """
                test("test");
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(CallExpression.class, ast.getFirst());
    }

    @Test
    void parseTuple() {
        String tuple = """
                var tuple : [1,2,3];
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(tuple, false));
        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());
    }

    @Test
    void parseAccessExpression() {
        String accessExpression = """
                $x[0];
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(accessExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(AccessExpression.class, ast.getFirst());
    }

    @Test
    void parseBreak() {
        String breakStmt = """
                break();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(breakStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Break.class, ast.getFirst());
    }

    @Test
    void parseList() {
        String list = """
                var list : {};
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(list, false));
        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());
    }

    @Test
    void parseBlock() {
        String block = """
                {
                    var ref : 10;
                }
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(block, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Block.class, ast.getFirst());
    }

    @Test
    void parseImport() {
        String importStmt = """
                import HelloWorld;
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(importStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(ImportExpression.class, ast.getFirst());
    }

    @Test
    void parseModuleImport() {
        String importStmt = """
                import module HelloWorld;
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(importStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(ImportExpression.class, ast.getFirst());
    }

    @Test
    void parseOverwrite() {
        String overwriteStmt = """
                overwrite(1);
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(overwriteStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Overwrite.class, ast.getFirst());
    }

    @Test
    void parseModule() {
        String moduleStmt = """
                module test;
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(moduleStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(ModuleDecl.class, ast.getFirst());
    }

    @Test
    void parseForeach() {
        String foreachStmt = """
                foreach(var i in $test) {} 
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(foreachStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Foreach.class, ast.getFirst());
    }

    @Test
    void parseForeachRange() {
        String foreachStmt = """
               foreach(var i in <0..5>) {} 
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(foreachStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Foreach.class, ast.getFirst());
    }

    @Test
    void parseForWithRange() {
        String forStmt = """
                for(var i in <0..5>) {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Foreach.class, ast.getFirst());
    }

    @Test
    void parseNamespaceCallExpression() {
        String callExpression = """
                Test.test();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(NamespaceCallExpression.class, ast.getFirst());
    }

    @Test
    void parseConstVar() {
        String callExpression = """
                const test : 0;
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());
        if (ast.getFirst() instanceof VarDecl varDecl) {
            assertTrue(varDecl.isConst());
        }
    }

    @Test
    void parseObjectExpression() {
        String callExpression = """
                var wrapper : {
                    var a;
                };
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.getFirst());
        if (ast.getFirst() instanceof VarDecl varDecl) {
            assertTrue(varDecl.getInitializer() instanceof ObjectExpression);
        }
    }
}
