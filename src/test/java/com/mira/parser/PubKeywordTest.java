package com.mira.parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.parser.MultipleParserErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public class PubKeywordTest {

    private Tokenizer tokenizer;
    private Parser parser;

    @BeforeEach
    void setup() {
        tokenizer = new Tokenizer();
        parser = new Parser();
    }

    private List<Node> parse(String source) {
        return parser.parseTokens(tokenizer.tokenize(source, false));
    }

    @Test
    void pubFnIsPublic() {
        List<Node> ast = parse("pub fn greet() {}");
        FuncDecl fd = assertInstanceOf(FuncDecl.class, ast.getFirst());
        assertTrue(fd.isPublic());
    }

    @Test
    void fnWithoutPubIsPrivate() {
        List<Node> ast = parse("fn helper() {}");
        FuncDecl fd = assertInstanceOf(FuncDecl.class, ast.getFirst());
        assertFalse(fd.isPublic());
    }

    @Test
    void pubConstIsPublic() {
        List<Node> ast = parse("pub const PI : 3;");
        VarDecl vd = assertInstanceOf(VarDecl.class, ast.getFirst());
        assertTrue(vd.isPublic());
        assertTrue(vd.isConst());
    }

    @Test
    void pubVarIsPublic() {
        List<Node> ast = parse("pub var counter : 0;");
        VarDecl vd = assertInstanceOf(VarDecl.class, ast.getFirst());
        assertTrue(vd.isPublic());
        assertFalse(vd.isConst());
    }

    @Test
    void varWithoutPubIsPrivate() {
        List<Node> ast = parse("var counter : 0;");
        VarDecl vd = assertInstanceOf(VarDecl.class, ast.getFirst());
        assertFalse(vd.isPublic());
    }

    @Test
    void pubEnumIsPublic() {
        List<Node> ast = parse("pub enum Color { Red, Green }");
        EnumDecl ed = assertInstanceOf(EnumDecl.class, ast.getFirst());
        assertTrue(ed.isPublic());
    }

    @Test
    void enumWithoutPubIsPrivate() {
        List<Node> ast = parse("enum Status { Active }");
        EnumDecl ed = assertInstanceOf(EnumDecl.class, ast.getFirst());
        assertFalse(ed.isPublic());
    }

    @Test
    void pubAsyncFnIsPublicAndAsync() {
        List<Node> ast = parse("pub async fn fetch() {}");
        FuncDecl fd = assertInstanceOf(FuncDecl.class, ast.getFirst());
        assertTrue(fd.isPublic());
        assertTrue(fd.isAsync());
    }

    @Test
    void pubPureFnIsPublicAndPure() {
        List<Node> ast = parse("pub pure fn compute() {}");
        FuncDecl fd = assertInstanceOf(FuncDecl.class, ast.getFirst());
        assertTrue(fd.isPublic());
        assertTrue(fd.isPure());
    }

    @Test
    void pubInsideFunctionThrows() {
        assertThrows(MultipleParserErrors.class, () -> parse("fn outer() { pub fn inner() {} }"));
    }

    @Test
    void moduleSelectiveImportWithBraces() {
        List<Node> ast = parse("import module \"./mod.mira\" {foo, bar};");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertTrue(expr.isExternalModule());
        assertTrue(expr.isSelective());
        assertEquals(List.of("foo", "bar"), expr.getSelectedFunctions());
    }

    @Test
    void moduleSelectiveImportWithBracesAndAlias() {
        List<Node> ast = parse("import module \"./mod.mira\" {foo} as m;");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertTrue(expr.isExternalModule());
        assertTrue(expr.isSelective());
        assertEquals("m", expr.getNamespace());
        assertEquals(List.of("foo"), expr.getSelectedFunctions());
    }

    @Test
    void moduleFullImportRemainsNonSelective() {
        List<Node> ast = parse("import module \"./mod.mira\";");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertFalse(expr.isSelective());
    }

    @Test
    void stdlibSelectiveImportWithBraces() {
        List<Node> ast = parse("import string {trim, split};");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertFalse(expr.isExternalModule());
        assertTrue(expr.isSelective());
        assertEquals(List.of("trim", "split"), expr.getSelectedFunctions());
    }

    @Test
    void stdlibSelectiveImportWithBracesAndAlias() {
        List<Node> ast = parse("import string {trim} as str;");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertTrue(expr.isSelective());
        assertEquals("str", expr.getNamespace());
        assertEquals(List.of("trim"), expr.getSelectedFunctions());
    }

    @Test
    void stdlibColonSyntaxStillWorks() {
        List<Node> ast = parse("import string: trim;");
        ImportExpression expr = assertInstanceOf(ImportExpression.class, ast.getFirst());
        assertTrue(expr.isSelective());
        assertEquals(List.of("trim"), expr.getSelectedFunctions());
    }
}
