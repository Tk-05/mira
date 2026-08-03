package com.mira.parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

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
    void postfixIncrementIsNotPrefix() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("$x++;", false));
        UnaryExpression expr = (UnaryExpression) ast.getFirst();

        assertEquals("++", expr.getOperation().getLexeme());
        assertFalse(expr.isPrefix());
        assertInstanceOf(UnaryExpression.class, expr.getRight());
    }

    @Test
    void prefixIncrementIsMarkedPrefix() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("++$x;", false));
        UnaryExpression expr = (UnaryExpression) ast.getFirst();

        assertEquals("++", expr.getOperation().getLexeme());
        assertTrue(expr.isPrefix());
        assertInstanceOf(UnaryExpression.class, expr.getRight());
    }

    @Test
    void prefixDecrementOnArrayElement() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("--$arr[0];", false));
        UnaryExpression expr = (UnaryExpression) ast.getFirst();

        assertEquals("--", expr.getOperation().getLexeme());
        assertTrue(expr.isPrefix());
        assertInstanceOf(AccessExpression.class, expr.getRight());
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
        Loop loop = assertInstanceOf(Loop.class, ast.getFirst());
        assertFalse(loop.isForeach());
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
        Loop loop = assertInstanceOf(Loop.class, ast.getFirst());
        assertFalse(loop.isForeach());
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
        Loop loop = assertInstanceOf(Loop.class, ast.getFirst());
        assertFalse(loop.isForeach());
    }

    @Test
    void parseFuncDecl() {
        String funcDecl = """
                fn foo() {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(funcDecl, false));
        assertEquals(1, ast.size());
        assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
    }

    @Test
    void parseReturn() {
        String retStmt = """
                return;
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
    void parseDoWhile() {
        String doWhileStmt = """
                do {
                } while(1);
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(doWhileStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(While.class, ast.getFirst());
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
    void parseIfWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("if (1) foo();", false));
        If ifStmt = assertInstanceOf(If.class, ast.getFirst());
        assertEquals(1, ifStmt.getThenBody().size());
        assertNull(ifStmt.getElseBody());
    }

    @Test
    void parseIfElseWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("if (1) foo(); else bar();", false));
        If ifStmt = assertInstanceOf(If.class, ast.getFirst());
        assertEquals(1, ifStmt.getThenBody().size());
        assertEquals(1, ifStmt.getElseBody().size());
    }

    @Test
    void parseIfElseIfWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("if (1) foo(); else if (2) bar();", false));
        If outer = assertInstanceOf(If.class, ast.getFirst());
        assertEquals(1, outer.getThenBody().size());
        assertEquals(1, outer.getElseBody().size());
        assertInstanceOf(If.class, outer.getElseBody().getFirst());
    }

    @Test
    void parseWhileWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("while (1) foo();", false));
        While whileStmt = assertInstanceOf(While.class, ast.getFirst());
        assertEquals(1, whileStmt.getBody().size());
        assertFalse(whileStmt.getDoModifier());
    }

    @Test
    void parseDoWhileWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("do foo(); while (1);", false));
        While doWhile = assertInstanceOf(While.class, ast.getFirst());
        assertEquals(1, doWhile.getBody().size());
        assertTrue(doWhile.getDoModifier());
    }

    @Test
    void parseForWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(
                "for (var i : 0; $i < 10; $i : eval($i + 1)) foo();", false));
        Loop forStmt = assertInstanceOf(Loop.class, ast.getFirst());
        assertFalse(forStmt.isForeach());
        assertEquals(1, forStmt.getBody().size());
    }

    @Test
    void parseForeachWithoutBraces() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(
                "for (var x in $list) foo();", false));
        Loop foreachStmt = assertInstanceOf(Loop.class, ast.getFirst());
        assertTrue(foreachStmt.isForeach());
        assertEquals(1, foreachStmt.getBody().size());
    }

    @Test
    void parseIfWithBracesBodyStillWorks() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("if (1) { foo(); bar(); }", false));
        If ifStmt = assertInstanceOf(If.class, ast.getFirst());
        assertEquals(2, ifStmt.getThenBody().size());
    }

    @Test
    void parseEmptyCall() {
        String callExpression = """
                foo();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(CallExpression.class, ast.getFirst());
    }

    @Test
    void parseCall() {
        String callExpression = """
                foo("bar");
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
                break;
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
    void parseModule() {
        String moduleStmt = """
                module foo;
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(moduleStmt, false));
        assertEquals(1, ast.size());
        assertInstanceOf(ModuleDecl.class, ast.getFirst());
    }

    @Test
    void parseForInCollection() {
        String forStmt = """
                for(var i in $arr) {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        Loop loop = assertInstanceOf(Loop.class, ast.getFirst());
        assertTrue(loop.isForeach());
    }

    @Test
    void parseForInRange() {
        String forStmt = """
                for(var i in <0..5>) {}
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(forStmt, false));
        assertEquals(1, ast.size());
        Loop loop = assertInstanceOf(Loop.class, ast.getFirst());
        assertTrue(loop.isForeach());
    }

    @Test
    void parseNamespaceCallExpression() {
        String callExpression = """
                Test.foo();
                """;
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(callExpression, false));
        assertEquals(1, ast.size());
        assertInstanceOf(NamespaceCallExpression.class, ast.getFirst());
    }

    @Test
    void parseConstVar() {
        String callExpression = """
                const foo : 0;
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
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("continue;", false));
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
                    case (1) { return true; }
                    case (2) { return false; }
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
                    case (1) { return true; }
                    default { return false; }
                }
                """, false));
        assertEquals(1, ast.size());
        Switch sw = assertInstanceOf(Switch.class, ast.getFirst());
        assertNotNull(sw.getDefaultBody());
    }

    @Test
    void parseThrow() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("throw error(\"error\");", false));
        assertEquals(1, ast.size());
        assertInstanceOf(Throw.class, ast.getFirst());
    }

    @Test
    void parseTryCatch() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                try {
                    throw error("err");
                } catch(error) {
                    print($error);
                }
                """, false));
        assertEquals(1, ast.size());
        TryCatch tc = assertInstanceOf(TryCatch.class, ast.getFirst());
        assertNotNull(tc.getTryBody());
        assertNotNull(tc.getCatchClauses());
    }

    @Test
    void parseLambdaExpression() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn(x) { return $x; };", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(1, lambda.getArity());
    }

    @Test
    void parseLambdaExpressionNoParams() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn() { return 42; };", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(0, lambda.getArity());
    }

    @Test
    void parseLambdaExpressionMultipleParams() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : fn(a, b, c) { return $a; };", false));
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

    @Test
    void parseMap() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var vals : {\"69\" : 420};", false));
        assertEquals(1, ast.size());
        assertInstanceOf(VarDecl.class, ast.get(0));
    }

    @Test
    void parseArrowLambda() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("var f : (x) -> eval($x * 2);", false));
        assertEquals(1, ast.size());
        VarDecl decl = assertInstanceOf(VarDecl.class, ast.getFirst());
        LambdaExpression lambda = assertInstanceOf(LambdaExpression.class, decl.getInitializer());
        assertEquals(1, lambda.getArity());
    }

    @Test
    void parseAsyncFunc() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("async fn fetch() {}", false));
        assertEquals(1, ast.size());
        Statement.FuncDecl decl = assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
        assertTrue(decl.isAsync());
    }

    @Test
    void parseAwaitExpr() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("await fetch();", false));
        assertEquals(1, ast.size());
        assertInstanceOf(AwaitExpression.class, ast.getFirst());
    }

    @Test
    void parseVariadicFunc() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("fn sum(...args) {}", false));
        assertEquals(1, ast.size());
        Statement.FuncDecl decl = assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
        assertNotNull(decl.getVariadicParam());
        assertEquals("args", decl.getVariadicParam());
    }

    @Test
    void parseTryCatchFinally() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                try {
                    throw error("err");
                } catch(error) {
                    print($error);
                } finally {
                    print("done");
                }
                """, false));
        assertEquals(1, ast.size());
        TryCatch tc = assertInstanceOf(TryCatch.class, ast.getFirst());
        assertNotNull(tc.getFinallyBody());
        assertFalse(tc.getFinallyBody().isEmpty());
    }

    @Test
    void parseTryCatchMultipleClauses() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                try {
                    throw error("err");
                } catch(TypeError e) {
                    print("type");
                } catch(error) {
                    print("other");
                }
                """, false));
        assertEquals(1, ast.size());
        TryCatch tc = assertInstanceOf(TryCatch.class, ast.getFirst());
        assertEquals(2, tc.getCatchClauses().size());
    }

    @Test
    void parseFuncDeclParams() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("fn add(a, b) {}", false));
        assertEquals(1, ast.size());
        Statement.FuncDecl decl = assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
        assertEquals(2, decl.getParameters().size());
        assertEquals("a", decl.getParameters().get(0).name());
        assertEquals("b", decl.getParameters().get(1).name());
    }

    @Test
    void parseFuncDeclDefaultParam() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("fn greet(name : \"World\") {}", false));
        assertEquals(1, ast.size());
        Statement.FuncDecl decl = assertInstanceOf(Statement.FuncDecl.class, ast.getFirst());
        assertEquals(1, decl.getParameters().size());
        assertTrue(decl.getParameters().getFirst().hasDefault());
    }

    @Test
    void parseReturnWithValue() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("return 42;", false));
        assertEquals(1, ast.size());
        Statement.Return ret = assertInstanceOf(Statement.Return.class, ast.getFirst());
        assertNotNull(ret.getValue());
    }

    @Test
    void parseLockStatement() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("lock($mutex) { }", false));
        assertEquals(1, ast.size());
        assertInstanceOf(Lock.class, ast.getFirst());
    }

    @Test
    void parseComptimeBlock() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("comptime { var x : 1; }", false));
        assertEquals(1, ast.size());
        assertInstanceOf(ComptimeBlock.class, ast.getFirst());
    }

    @Test
    void parseTernaryExpression() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("$x ? 1 : 0;", false));
        assertEquals(1, ast.size());
        TernaryExpression ternary = assertInstanceOf(TernaryExpression.class, ast.getFirst());
        assertNotNull(ternary.getCondition());
        assertNotNull(ternary.getThenExpr());
        assertNotNull(ternary.getElseExpr());
    }

    @Test
    void parseNullCoalescing() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("$x ?? 0;", false));
        assertEquals(1, ast.size());
        BinaryExpression expr = assertInstanceOf(BinaryExpression.class, ast.getFirst());
        assertEquals("??", expr.getOperator().getLexeme());
    }

    @Test
    void parseOptionalChaining() {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("$x?.field;", false));
        assertEquals(1, ast.size());
        FieldAccessExpression access = assertInstanceOf(FieldAccessExpression.class, ast.getFirst());
        assertEquals("field", access.getField());
    }

    @Test
    void parseInvalidSyntaxThrows() {
        assertThrows(Exception.class, () -> parser.parseTokens(tokenizer.tokenize("var ;", false)));
    }
}
