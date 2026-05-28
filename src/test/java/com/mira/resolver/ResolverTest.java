package com.mira.resolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleResolverErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class ResolverTest {

    private List<MiraError> errorsFor(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        MultipleResolverErrors ex = assertThrows(MultipleResolverErrors.class,
                () -> new Resolver().resolve(ast));
        return ex.getErrors();
    }

    private void assertClean(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        assertDoesNotThrow(() -> new Resolver().resolve(ast));
    }

    private boolean hasCode(List<MiraError> errors, String code) {
        return errors.stream().anyMatch(e -> code.equals(e.getErrorCode()));
    }

    @Test
    void validProgram() {
        assertClean("var x : 1; println($x);");
    }

    @Test
    void functionParamIsValid() {
        assertClean("fn greet(name) { println($name); }");
    }

    @Test
    void declaredFunctionCall() {
        assertClean("fn add(a, b) { return eval($a + $b); } add(1, 2);");
    }

    @Test
    void builtinFunctionCall() {
        assertClean("println(\"hello\");");
    }

    @Test
    void namespaceImportIsValid() {
        assertClean("import math as m; m.sqrt(4);");
    }

    @Test
    void foreachIteratorIsValid() {
        assertClean("var list : {1, 2, 3}; foreach(var item in $list) { println($item); }");
    }

    @Test
    void catchParamIsValid() {
        assertClean("try { println(1); } catch(e) { println($e); }");
    }

    @Test
    void lambdaParamIsValid() {
        assertClean("var f : (x) -> eval($x + 1);");
    }

    @Test
    void undeclaredVariable() {
        List<MiraError> errors = errorsFor("println($x);");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void undeclaredVariableInFunction() {
        List<MiraError> errors = errorsFor("fn f() { println($missing); }");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void declaredInsideScopeNotVisibleOutside() {
        List<MiraError> errors = errorsFor("{ var x : 1; } println($x);");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void assignToUndeclaredVariable() {
        List<MiraError> errors = errorsFor("$x : 5;");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void undefinedFunction() {
        List<MiraError> errors = errorsFor("foo();");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void undefinedFunctionInsideFunction() {
        List<MiraError> errors = errorsFor("fn main() { test(); }");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void undefinedFunctionInExpression() {
        List<MiraError> errors = errorsFor("var x : unknownFunc(1);");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void unknownNamespace() {
        List<MiraError> errors = errorsFor("ns.sqrt(4);");
        assertTrue(hasCode(errors, "E303"));
    }

    @Test
    void namespaceAliasCalledDirectlyIsE302() {
        List<MiraError> errors = errorsFor("import math as m; sqrt(4);");
        assertTrue(hasCode(errors, "E302"), "calling stdlib function without alias should be E302");
    }

    @Test
    void namespaceNameCalledDirectlyIsE302() {
        List<MiraError> errors = errorsFor("import math as m; m();");
        assertTrue(hasCode(errors, "E302"), "calling a namespace name directly should be E302");
    }

    @Test
    void multipleErrorsCollected() {
        List<MiraError> errors = errorsFor("println($a); println($b);");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> "E301".equals(e.getErrorCode())));
    }
}
