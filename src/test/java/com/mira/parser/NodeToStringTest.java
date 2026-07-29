package com.mira.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.StructInitExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

public class NodeToStringTest {

    Tokenizer tokenizer = new Tokenizer();
    Parser parser = new Parser();

    private Node first(String source) {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));
        return ast.getFirst();
    }

    @Test
    void varDecl() {
        assertEquals("var x : 5;", first("var x : 5;").toString());
    }

    @Test
    void varDeclPublicConst() {
        assertEquals("pub const PI : 3;", first("pub const PI : 3;").toString());
    }

    @Test
    void funcDecl() {
        assertEquals("fn add(a, b) {...}", first("fn add(a, b) { return 1; }").toString());
    }

    @Test
    void funcDeclWithDefaultAndVariadic() {
        assertEquals("fn f(a, b : 2, ...rest) {...}",
                first("fn f(a, b : 2, ...rest) { return 1; }").toString());
    }

    @Test
    void returnStatement() {
        FuncDecl fn = (FuncDecl) first("fn f() { return 5; }");
        assertEquals(Return.class, fn.getBody().getFirst().getClass());
        assertEquals("return 5;", fn.getBody().getFirst().toString());
    }

    @Test
    void assignStatement() {
        assertEquals("$x : 5;", first("$x : 5;").toString());
        assertEquals(Assign.class, first("$x : 5;").getClass());
    }

    @Test
    void ifElse() {
        assertEquals("if (true) {...} else {...}",
                first("if (true) { print(1); } else { print(2); }").toString());
        assertEquals(If.class, first("if (true) { print(1); } else { print(2); }").getClass());
    }

    @Test
    void ifNoElse() {
        assertEquals("if (true) {...}", first("if (true) { print(1); }").toString());
    }

    @Test
    void forLoop() {
        Node node = first("for (var i : 0; $i < 5; $i++) { print(1); }");
        assertEquals(For.class, node.getClass());
        assertEquals("for (...; ($i < 5); ...) {...}", node.toString());
    }

    @Test
    void whileLoop() {
        assertEquals("while (true) {...}", first("while (true) { print(1); }").toString());
        assertEquals(While.class, first("while (true) { print(1); }").getClass());
    }

    @Test
    void doWhileLoop() {
        assertEquals("do {...} while (true);", first("do { print(1); } while (true);").toString());
    }

    @Test
    void breakStatement() {
        While w = (While) first("while (true) { break; }");
        assertEquals(Break.class, w.getBody().getFirst().getClass());
        assertEquals("break;", w.getBody().getFirst().toString());
    }

    @Test
    void continueStatement() {
        While w = (While) first("while (true) { continue; }");
        assertEquals(Continue.class, w.getBody().getFirst().getClass());
        assertEquals("continue;", w.getBody().getFirst().toString());
    }

    @Test
    void block() {
        Node node = first("{ print(1); }");
        assertEquals(Block.class, node.getClass());
        assertEquals("{...}", node.toString());
    }

    @Test
    void foreachLoop() {
        Node node = first("foreach (var item in <0..5>) { print(1); }");
        assertEquals(Foreach.class, node.getClass());
        assertEquals("foreach (var item in <0..5>) {...}", node.toString());
    }

    @Test
    void switchStatement() {
        Node node = first("""
                switch (1) {
                    case (1) { print(1); }
                    default { print(0); }
                }
                """);
        assertEquals(Switch.class, node.getClass());
        assertEquals("switch (1) {...}", node.toString());
    }

    @Test
    void moduleDecl() {
        Node node = first("module Test;");
        assertEquals(ModuleDecl.class, node.getClass());
        assertEquals("module Test;", node.toString());
    }

    @Test
    void throwStatement() {
        Node node = first("throw MyError(1);");
        assertEquals(Throw.class, node.getClass());
        assertEquals("throw MyError(1);", node.toString());
    }

    @Test
    void tryCatchFinally() {
        Node node = first("""
                try {
                    print(1);
                } catch (Error e) {
                    print(2);
                } catch (e2) {
                    print(3);
                } finally {
                    print(4);
                }
                """);
        assertEquals(TryCatch.class, node.getClass());
        assertEquals("try {...} catch(Error e) {...} catch(e2) {...} finally {...}", node.toString());
    }

    @Test
    void enumDecl() {
        Node node = first("enum Color { RED, GREEN, BLUE }");
        assertEquals(EnumDecl.class, node.getClass());
        assertEquals("enum Color {RED, GREEN, BLUE}", node.toString());
    }

    @Test
    void lockStatement() {
        Node node = first("lock ($mutex) { print(1); }");
        assertEquals(Lock.class, node.getClass());
        assertEquals("lock ($mutex) {...}", node.toString());
    }

    @Test
    void varDestructure() {
        Node node = first("var (a, b) : $pair;");
        assertEquals(VarDestructure.class, node.getClass());
        assertEquals("var (a, b) : $pair;", node.toString());
    }

    @Test
    void comptimeBlock() {
        Node node = first("comptime { var x : 1; }");
        assertEquals(ComptimeBlock.class, node.getClass());
        assertEquals("comptime {...}", node.toString());
    }

    @Test
    void staticAssertNoMessage() {
        Node node = first("static_assert(true);");
        assertEquals(StaticAssert.class, node.getClass());
        assertEquals("static_assert(true);", node.toString());
    }

    @Test
    void staticAssertWithMessage() {
        Node node = first("static_assert(true, \"must be true\");");
        assertEquals("static_assert(true, must be true);", node.toString());
    }

    @Test
    void testCall() {
        Node node = first("test(\"name\", fn() { assert(true); });");
        assertEquals(TestCall.class, node.getClass());
        assertEquals("test(name, <lambda/0>);", node.toString());
    }

    @Test
    void importStdlib() {
        Node node = first("import math;");
        assertEquals(ImportExpression.class, node.getClass());
        assertEquals("import math", node.toString());
    }

    @Test
    void importStdlibSelectiveAliased() {
        Node node = first("import math {sqrt, abs} as m;");
        assertEquals("import math {sqrt, abs} as m", node.toString());
    }

    @Test
    void importModule() {
        Node node = first("import module \"foo.mira\" as foo;");
        assertEquals("import module foo.mira as foo", node.toString());
    }

    @Test
    void importNative() {
        Node node = first("import native \"foo.jar\" as foo;");
        assertEquals("import native foo.jar as foo", node.toString());
    }

    @Test
    void rangeExpression() {
        Foreach fe = (Foreach) first("foreach (var item in <1..5>) { print(1); }");
        assertEquals(RangeExpression.class, fe.getCollection().getClass());
        assertEquals("<1..5>", fe.getCollection().toString());
    }

    @Test
    void rangeExpressionWithStep() {
        Foreach fe = (Foreach) first("foreach (var item in <1..10,2>) { print(1); }");
        assertEquals("<1..10,2>", fe.getCollection().toString());
    }

    @Test
    void objectExpression() {
        VarDecl decl = (VarDecl) first("var o : { var x : 1; var y : 2; };");
        assertEquals(ObjectExpression.class, decl.getInitializer().getClass());
        assertEquals("{ 2 field(s), 0 method(s) }", decl.getInitializer().toString());
    }

    @Test
    void structExpression() {
        VarDecl decl = (VarDecl) first("var s : struct { var x : 1; };");
        assertEquals(StructExpression.class, decl.getInitializer().getClass());
        assertEquals("struct { 1 field(s), 0 method(s) }", decl.getInitializer().toString());
    }

    @Test
    void structInitExpression() {
        VarDecl decl = (VarDecl) first("var p : Point { $x: 1, $y: 2 };");
        assertEquals(StructInitExpression.class, decl.getInitializer().getClass());
        assertEquals("Point { $x: 1, $y: 2 }", decl.getInitializer().toString());
    }
}
