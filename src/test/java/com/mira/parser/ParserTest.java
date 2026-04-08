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
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
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

    @Test
    void parseContinue() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("continue();", false));
        assertEquals(1, ast.size());
        assertInstanceOf(Continue.class, ast.getFirst());
    }

    @Test
    void parseEnumDecl() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("enum Color { RED, GREEN, BLUE }", false));
        assertEquals(1, ast.size());
        EnumDecl decl = assertInstanceOf(EnumDecl.class, ast.getFirst());
        assertEquals("Color", decl.getIdentifier());
        assertEquals(3, decl.getValues().size());
    }

    @Test
    void parseEnumDeclWithExplicitValues() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("enum Status { OK : 200, ERR : 500 }", false));
        assertEquals(1, ast.size());
        assertInstanceOf(EnumDecl.class, ast.getFirst());
    }

    @Test
    void parseSwitch() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                switch ($x) {
                    case (1) { ret(true); }
                    case (2) { ret(false); }
                }
                """, false));
        assertEquals(1, ast.size());
        Switch sw = assertInstanceOf(Switch.class, ast.getFirst());
        assertEquals(2, sw.getCases().size());
    }

    @Test
    void parseSwitchWithDefault() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                switch ($x) {
                    case (1) { ret(true); }
                    default { ret(false); }
                }
                """, false));
        assertEquals(1, ast.size());
        Switch sw = assertInstanceOf(Switch.class, ast.getFirst());
        assertNotNull(sw.getDefaultBody());
    }

    @Test
    void parseThrow() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("throw(\"error\");", false));
        assertEquals(1, ast.size());
        assertInstanceOf(Throw.class, ast.getFirst());
    }

    @Test
    void parseTryCatch() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                try {
                    throw("err");
                } catch(e) {
                    print($e);
                }
                """, false));
        assertEquals(1, ast.size());
        TryCatch tc = assertInstanceOf(TryCatch.class, ast.getFirst());
        assertNotNull(tc.getTryBody());
        assertNotNull(tc.getCatchBody());
        assertEquals("e", tc.getCatchParam());
    }

    @Test
    void parseLambdaExpression() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn(x) { ret($x); };", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(1, lambda.getArity());
    }

    @Test
    void parseLambdaExpressionNoParams() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn() { ret(42); };", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(0, lambda.getArity());
    }

    @Test
    void parseLambdaExpressionMultipleParams() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn(a, b, c) { ret($a); };", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(3, lambda.getArity());
    }

    @Test
    void parseLibImportWithAlias() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("import string as str;", false));
        assertEquals(1, ast.size());
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertEquals("string", expr.getModule());
        assertEquals("str", expr.getNamespace());
        assertTrue(!expr.isExternalModule());
    }

    @Test
    void parseLibImportWithoutAlias() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("import string;", false));
        assertEquals(1, ast.size());
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertEquals("string", expr.getModule());
        assertNull(expr.getNamespace());
        assertTrue(!expr.isExternalModule());
    }

    @Test
    void parseModuleImportWithAlias() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("import module \"./test.mira\" as myMod;", false));
        assertEquals(1, ast.size());
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertEquals("myMod", expr.getNamespace());
        assertTrue(expr.isExternalModule());
    }

    @Test
    void parseModuleImportWithoutAlias() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("import module \"./test.mira\";", false));
        assertEquals(1, ast.size());
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertNull(expr.getNamespace());
        assertTrue(expr.isExternalModule());
    }

    @Test
    void parseNamespaceCallExpressionAlias() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("str.trim();", false));
        assertEquals(1, ast.size());
        NamespaceCallExpression expr = assertInstanceOf(NamespaceCallExpression.class, ast.getFirst());
        assertEquals("str", expr.getAlias());
        assertEquals("trim", expr.getFunctionName());
    }
}
