package com.mira.resolver;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.cli.Flags;
import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.warning.WarningCollector;

public class StaticCheckTest {

    private List<MiraError> errorsFor(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        MultipleStaticCheckErrors ex = assertThrows(MultipleStaticCheckErrors.class,
                () -> new StaticCheck().check(ast));
        return ex.getErrors();
    }

    private void assertClean(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        assertDoesNotThrow(() -> new StaticCheck().check(ast));
    }

    private boolean hasCode(List<MiraError> errors, String code) {
        return errors.stream().anyMatch(e -> code.equals(e.getErrorCode()));
    }

    @AfterEach
    void resetVerbose() {
        Flags.verbose = false;
    }

    @Test
    void validProgram() {
        assertClean("var x : 1; println(x);");
    }

    @Test
    void functionParamIsValid() {
        assertClean("fn greet(name) { println(name); }");
    }

    @Test
    void declaredFunctionCall() {
        assertClean("fn add(a, b) { return (a + b); } add(1, 2);");
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
        assertClean("var list : {1, 2, 3}; for(var item in list) { println(item); }");
    }

    @Test
    void catchParamIsValid() {
        assertClean("try { println(1); } catch(e) { println(e); }");
    }

    @Test
    void lambdaParamIsValid() {
        assertClean("var f : (x) -> (x + 1);");
    }

    @Test
    void breakInsideLoopIsValid() {
        assertClean("for(var i in <0..5>) { break; }");
    }

    @Test
    void continueInsideLoopIsValid() {
        assertClean("for(var i in <0..5>) { continue; }");
    }

    @Test
    void duplicateDifferentScopes() {
        assertClean("var x : 1; { var x : 2; }");
    }

    @Test
    void arityMatchIsValid() {
        assertClean("println(\"hi\");");
    }

    @Test
    void undeclaredVariable() {
        List<MiraError> errors = errorsFor("println(x);");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void undeclaredVariableInFunction() {
        List<MiraError> errors = errorsFor("fn f() { println(missing); }");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void declaredInsideScopeNotVisibleOutside() {
        List<MiraError> errors = errorsFor("{ var x : 1; } println(x);");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void assignToUndeclaredVariable() {
        List<MiraError> errors = errorsFor("x : 5;");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void undefinedFunction() {
        List<MiraError> errors = errorsFor("foo();");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void undefinedFunctionInsideFunction() {
        List<MiraError> errors = errorsFor("fn main() { foo(); }");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void undefinedFunctionInExpression() {
        List<MiraError> errors = errorsFor("var x : unknownFunc(1);");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void namespaceAliasCalledDirectlyIsE302() {
        List<MiraError> errors = errorsFor("import math as m; sqrt(4);");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void namespaceNameCalledDirectlyIsE302() {
        List<MiraError> errors = errorsFor("import math as m; m();");
        assertTrue(hasCode(errors, "E302"));
    }

    @Test
    void unknownNamespace() {
        // "ns" was never bound by an "import ... as ns" — with no sigil to mark
        // variable references, "ns.sqrt(4)" now parses as an ordinary method
        // call on the variable "ns", so the undeclared name is now what fires.
        List<MiraError> errors = errorsFor("ns.sqrt(4);");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void constReassignment() {
        List<MiraError> errors = errorsFor("const x : 1; x : 5;");
        assertTrue(hasCode(errors, "E304"));
    }

    @Test
    void varReassignmentIsValid() {
        assertClean("var x : 1; x : 5;");
    }

    @Test
    void breakOutsideLoop() {
        List<MiraError> errors = errorsFor("break;");
        assertTrue(hasCode(errors, "E305"));
    }

    @Test
    void continueOutsideLoop() {
        List<MiraError> errors = errorsFor("continue;");
        assertTrue(hasCode(errors, "E305"));
    }

    @Test
    void duplicateDeclaration() {
        List<MiraError> errors = errorsFor("var x : 1; var x : 2;");
        assertTrue(hasCode(errors, "E306"));
    }

    @Test
    void duplicateFunctionDeclaration() {
        List<MiraError> errors = errorsFor("fn foo() {} fn foo() {}");
        assertTrue(hasCode(errors, "E306"));
    }

    @Test
    void duplicateEnumDeclaration() {
        List<MiraError> errors = errorsFor("enum Foo { A } enum Foo { B }");
        assertTrue(hasCode(errors, "E306"));
    }

    @Test
    void fieldAccessOnList() {
        List<MiraError> errors = errorsFor("var a : {1, 2}; println(a.x);");
        assertTrue(hasCode(errors, "E320"));
    }

    @Test
    void fieldAccessOnDirectLiteral() {
        List<MiraError> errors = errorsFor("println({1, 2}.x);");
        assertTrue(hasCode(errors, "E320"));
    }

    @Test
    void fieldAccessOnObjectIsClean() {
        assertClean("var a : { var name : \"hi\"; }; println(a.name);");
    }

    @Test
    void duplicateDestructureDeclaration() {
        List<MiraError> errors = errorsFor("var (x, y) : {1, 2}; var (x, z) : {3, 4};");
        assertTrue(hasCode(errors, "E306"));
    }

    @Test
    void arityMismatchTooMany() {
        List<MiraError> errors = errorsFor("println(1, 2);");
        assertTrue(hasCode(errors, "E307"));
    }

    @Test
    void arityMismatchTooFew() {
        List<MiraError> errors = errorsFor("println();");
        assertTrue(hasCode(errors, "E307"));
    }

    @Test
    void userFunctionArityMismatch() {
        List<MiraError> errors = errorsFor("fn add(a, b) { return (a + b); } add(1);");
        assertTrue(hasCode(errors, "E307"));
    }

    @Test
    void multipleErrorsCollected() {
        List<MiraError> errors = errorsFor("println(a); println(b);");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> "E301".equals(e.getErrorCode())));
    }

    @Test
    void staticAssertWithLiteralIsClean() {
        assertClean("static_assert(true);");
    }

    @Test
    void staticAssertWithLiteralExprIsClean() {
        assertClean("static_assert(1 == 1);");
    }

    @Test
    void staticAssertWithComptimeConstIsClean() {
        assertClean("comptime { const SIZE : 64; } static_assert(SIZE > 0);");
    }

    @Test
    void staticAssertWithRuntimeVarProducesE311() {
        List<MiraError> errors = errorsFor("var x : 5; static_assert(x > 0);");
        assertTrue(hasCode(errors, "E311"));
    }

    @Test
    void staticAssertWithFunctionParamProducesE311() {
        List<MiraError> errors = errorsFor("fn check(x) { static_assert(x > 0); } check(1);");
        assertTrue(hasCode(errors, "E311"));
    }

    @Test
    void staticAssertMixedComptimeAndRuntimeProducesE311() {
        List<MiraError> errors = errorsFor("comptime { const A : 1; } var b : 2; static_assert(A + b > 0);");
        assertTrue(hasCode(errors, "E311"));
    }

    @Test
    void staticAssertEvaluatesTrueWithSuppliedComptimeConstsIsClean() {
        // the comptime block's own literal value is irrelevant here — the supplied
        // comptimeConsts map stands in for what ComptimeExecutor would have produced.
        String source = "comptime { const SIZE : 0; } static_assert(SIZE > 0);";
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        assertDoesNotThrow(() -> new StaticCheck(Set.of(), null, Map.of(),
                Map.of("SIZE", 64.0)).check(ast));
    }

    @Test
    void staticAssertEvaluatesFalseWithSuppliedComptimeConstsProducesE308() {
        String source = "comptime { const SIZE : 0; } static_assert(SIZE > 0);";
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        MultipleStaticCheckErrors ex = assertThrows(MultipleStaticCheckErrors.class,
                () -> new StaticCheck(Set.of(), null, Map.of(),
                        Map.of("SIZE", -1.0)).check(ast));
        assertTrue(hasCode(ex.getErrors(), "E308"));
    }

    @Test
    void staticAssertWithoutComptimeConstsSuppliedIsNeverEvaluated() {
        // no 4th constructor arg (comptimeConsts == null) -> structural check only, never evaluated,
        // matching how LSP's DiagnosticCollector constructs StaticCheck (must never execute user code).
        assertClean("comptime { const SIZE : -1; } static_assert(SIZE > 0);");
    }

    @Test
    void constArrayIndexAssignProducesE321() {
        List<MiraError> errors = errorsFor("const arr : [1, 2, 3]; arr[0] : 9;");
        assertTrue(hasCode(errors, "E321"));
    }

    @Test
    void constObjectFieldAssignProducesE321() {
        List<MiraError> errors = errorsFor("const obj : { var x : 1; }; obj.x : 99;");
        assertTrue(hasCode(errors, "E321"));
    }

    @Test
    void varArrayIndexAssignIsValid() {
        assertClean("var arr : [1, 2, 3]; arr[0] : 9;");
    }

    @Test
    void rangeStepZeroViaVarProducesE313() {
        List<MiraError> errors = errorsFor("var step : 0; for(var i in <1..10, step>) { }");
        assertTrue(hasCode(errors, "E313"));
    }

    @Test
    void rangeStepNonZeroVarIsValid() {
        assertClean("var step : 2; for(var i in <1..10, step>) { }");
    }

    @Test
    void divisionByZeroLiteralProducesWarning() {
        WarningCollector.clear();
        assertClean("var r : 10 / 0;");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("Division by zero")));
        WarningCollector.clear();
    }

    @Test
    void divisionByZeroViaVarProducesWarning() {
        WarningCollector.clear();
        assertClean("var d : 0; var r : 10 / d;");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("Division by zero")));
        WarningCollector.clear();
    }

    @Test
    void divisionByNonZeroVarIsValid() {
        assertClean("var d : 2; var r : 10 / d;");
    }

    @Test
    void arithmeticOnStringLiteralProducesWarning() {
        WarningCollector.clear();
        assertClean("var r : \"foo\" - 1;");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("String literal")));
        WarningCollector.clear();
    }

    @Test
    void multiplyOnStringLiteralProducesWarning() {
        WarningCollector.clear();
        assertClean("var r : \"foo\" * 2;");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("String literal")));
        WarningCollector.clear();
    }

    @Test
    void divideOnStringLiteralProducesWarning() {
        WarningCollector.clear();
        assertClean("var r : \"foo\" / 2;");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("String literal")));
        WarningCollector.clear();
    }

    @Test
    void unaryMinusOnStringLiteralProducesWarning() {
        WarningCollector.clear();
        assertClean("var r : -\"foo\";");
        assertTrue(WarningCollector.getWarnings().stream()
                .anyMatch(w -> w.message().contains("String literal")));
        WarningCollector.clear();
    }

    @Test
    void barewordMinusOnUndeclaredNameIsUndeclaredVariable() {
        List<MiraError> errors = errorsFor("var r : x - 1;");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void barewordInPlusOnUndeclaredNameIsUndeclaredVariable() {
        List<MiraError> errors = errorsFor("var r : x + 1;");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void unaryMinusOnUndeclaredNameIsUndeclaredVariable() {
        List<MiraError> errors = errorsFor("var r : -x;");
        assertTrue(hasCode(errors, "E301"));
    }

    @Test
    void barewordArithmeticOnDeclaredVariableIsClean() {
        assertClean("var x : 5; var r : x - 1; var s : x + 1; var t : -x;");
    }

    @Test
    void arithmeticOnRealVariablesIsClean() {
        WarningCollector.clear();
        assertClean("var a : 5; var b : 3; var r : a - b;");
        assertTrue(WarningCollector.getWarnings().stream()
                .noneMatch(w -> w.message().contains("String literal") || w.message().contains("missing '$")));
        WarningCollector.clear();
    }

    @Test
    void stringConcatenationOfTwoLiteralsIsClean() {
        WarningCollector.clear();
        assertClean("var r : \"foo\" + \"bar\";");
        assertTrue(WarningCollector.getWarnings().stream()
                .noneMatch(w -> w.message().contains("String literal") || w.message().contains("missing '$")));
        WarningCollector.clear();
    }

    @Test
    void reservedWordsAreNotTreatedAsBareword() {
        WarningCollector.clear();
        assertClean("var r : true - 1;");
        assertTrue(WarningCollector.getWarnings().stream()
                .noneMatch(w -> w.message().contains("String literal") || w.message().contains("missing '$")));
        WarningCollector.clear();
    }

    @Test
    void compoundAssignWithVariablesIsClean() {
        WarningCollector.clear();
        assertClean("var x : 5; var y : 3; x -: y;");
        assertTrue(WarningCollector.getWarnings().stream()
                .noneMatch(w -> w.message().contains("String literal") || w.message().contains("missing '$")));
        WarningCollector.clear();
    }

    @Test
    void incrementStringVarProducesE322() {
        List<MiraError> errors = errorsFor("var s : \"hello\"; s++;");
        assertTrue(hasCode(errors, "E322"));
    }

    @Test
    void incrementListVarProducesE322() {
        List<MiraError> errors = errorsFor("var lst : {1, 2}; lst++;");
        assertTrue(hasCode(errors, "E322"));
    }

    @Test
    void incrementNumericVarIsValid() {
        assertClean("var n : 5; n++;");
    }

    @Test
    void incrementNonReferentExpressionIsValid() {
        assertClean("1++; 2--;");
    }

    @Test
    void incrementArrayElementIsValid() {
        assertClean("var arr : [1,2,3]; arr[0]++;");
    }

    @Test
    void incrementObjectFieldIsValid() {
        assertClean("var obj : { var x : 1; }; obj.x++;");
    }

    @Test
    void prefixIncrementStringVarProducesE322() {
        List<MiraError> errors = errorsFor("var s : \"hello\"; ++s;");
        assertTrue(hasCode(errors, "E322"));
    }

    @Test
    void prefixIncrementNumericVarIsValid() {
        assertClean("var n : 5; ++n;");
    }

    @Test
    void prefixIncrementNonReferentExpressionIsValid() {
        assertClean("++1; --2;");
    }

    @Test
    void prefixIncrementArrayElementIsValid() {
        assertClean("var arr : [1,2,3]; ++arr[0];");
    }

    @Test
    void prefixIncrementObjectFieldIsValid() {
        assertClean("var obj : { var x : 1; }; ++obj.x;");
    }

    @Test
    void accessUndefinedFieldOnKnownObjectProducesE323() {
        List<MiraError> errors = errorsFor("var o : { var x : 1; }; var y : o.z;");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void accessDefinedFieldOnKnownObjectIsValid() {
        assertClean("var o : { var x : 1; }; var y : o.x;");
    }

    @Test
    void optionalAccessUndefinedFieldIsValid() {
        assertClean("var o : { var x : 1; }; var y : o?.z;");
    }

    @Test
    void accessExistingFieldOnStructTemplateIsValid() {
        assertClean("var point : struct { var x : 0; var y : 0; }; var p : point{}; print(p.x);");
    }

    @Test
    void accessNonexistentFieldOnStructTemplateIsE323() {
        List<MiraError> errors = errorsFor("var point : struct { var x; var y; }; print(point.z);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void accessNonexistentFieldOnStructInstanceIsE323() {
        List<MiraError> errors = errorsFor("var point : struct { var x; var y; }; var p : point{}; print(p.z);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void accessExistingMethodOnStructTemplateIsValid() {
        assertClean("var counter : struct { var count : 0; fn get() { return this.count; } }; var c : counter{}; c.get();");
    }

    @Test
    void assignStructInstancePropagatesTypeForFieldCheck() {
        List<MiraError> errors = errorsFor("var point : struct { var x; var y; }; var q; q : point{}; print(q.z);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void assignStructInstanceThenValidFieldIsClean() {
        assertClean("var point : struct { var x; var y; }; var q; q : point{}; print(q.x);");
    }

    @Test
    void assignInsideBranchDoesNotPropagate() {
        assertClean("var point : struct { var x; }; var q; if (true) { q : point{}; } print(q.z);");
    }

    @Test
    void reassignToNonStructInvalidatesFieldCheck() {
        List<MiraError> errors = errorsFor("var point : struct { var x; }; var q : point{}; q : 42; print(q.x);");
        assertTrue(hasCode(errors, "E320"));
    }

    @Test
    void accessNonexistentFieldOnStructInstanceWithOverridesIsE323() {
        List<MiraError> errors = errorsFor("var point : struct { var x : 0; var y : 0; }; var origin : point{x : 0, y : 5}; println(origin.z);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void accessNonexistentFieldOnFunctionParamViaCallSiteIsE323() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; var y; }; var origin : point{}; "
                + "fn hello(name) { println(name.z); } hello(origin);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void accessExistingFieldOnFunctionParamViaCallSiteIsClean() {
        assertClean(
                "var point : struct { var x; var y; }; var origin : point{}; "
                + "fn hello(name) { println(name.x); } hello(origin);");
    }

    @Test
    void accessNonexistentFieldInReturnComplexExprIsE323() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; var y; }; var origin : point{}; "
                + "fn hello(name) { return \"hello\" + name.z; } hello(origin);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void paramTypeFollowedThroughNestedCallIsE323() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; var y; }; var origin : point{}; "
                + "fn hello(name) { hello2(name); } "
                + "fn hello2(name) { name.z; } "
                + "hello(origin);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void paramTypeFollowedThroughNestedCallValidFieldIsClean() {
        assertClean(
                "var point : struct { var x; var y; }; var origin : point{}; "
                + "fn hello(name) { hello2(name); } "
                + "fn hello2(name) { name.x; } "
                + "hello(origin);");
    }

    @Test
    void recursiveFunctionDoesNotCauseInfiniteLoop() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; }; var origin : point{}; "
                + "fn recurse(name) { recurse(name); name.z; } recurse(origin);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void fieldAccessOnStringParamIsE320() {
        List<MiraError> errors = errorsFor(
                "fn hello(name) { println(name.z); } hello(\"test\");");
        assertTrue(hasCode(errors, "E320"));
    }

    @Test
    void verboseModePrintsSummaryLine() {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; var x : 1;", false));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            Flags.verbose = true;
            new StaticCheck().check(ast);
        } finally {
            System.setOut(old);
        }

        assertTrue(out.toString().contains("static check:"));
    }

    @Test
    void nonVerboseModeHasNoSummaryLine() {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; var x : 1;", false));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            Flags.verbose = false;
            new StaticCheck().check(ast);
        } finally {
            System.setOut(old);
        }

        assertFalse(out.toString().contains("static check:"));
    }

    // --- Type checking (E324-E327) ---

    @Test
    void typedVarDeclWithMismatchedInitializerIsE324() {
        List<MiraError> errors = errorsFor("var x : Number : \"hi\";");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void typedVarDeclWithMatchingInitializerIsClean() {
        assertClean("var x : Number : 5;");
    }

    @Test
    void untypedVarDeclWithAnyValueIsUnaffected() {
        assertClean("var x : \"hi\"; var y : 5; var z : true;");
    }

    @Test
    void typedVarReassignmentMismatchIsE324() {
        List<MiraError> errors = errorsFor("var x : Number : 5; $x : \"hi\";");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void typedVarReassignmentMatchingIsClean() {
        assertClean("var x : Number : 5; $x : 6;");
    }

    @Test
    void untypedVarReassignmentToDifferentShapeIsUnaffected() {
        assertClean("var x : 5; $x : \"hi\";");
    }

    @Test
    void nullableTypedVarAcceptsNull() {
        assertClean("var x : Number? : null; $x : 5;");
    }

    @Test
    void callArgumentTypeMismatchIsE325() {
        List<MiraError> errors = errorsFor(
                "fn add(a : Number, b : Number) { return eval($a + $b); } add(1, \"x\");");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void callArgumentTypeMatchingIsClean() {
        assertClean("fn add(a : Number, b : Number) { return eval($a + $b); } add(1, 2);");
    }

    @Test
    void callToUntypedFunctionIsUnaffected() {
        assertClean("fn add(a, b) { return eval($a + $b); } add(1, \"x\");");
    }

    @Test
    void returnTypeMismatchIsE326() {
        List<MiraError> errors = errorsFor(
                "fn greet() -> String { return 5; }");
        assertTrue(hasCode(errors, "E326"));
    }

    @Test
    void returnTypeMatchingIsClean() {
        assertClean("fn greet() -> String { return \"hi\"; }");
    }

    @Test
    void untypedFunctionReturnIsUnaffected() {
        assertClean("fn greet() { return 5; }");
    }

    @Test
    void unknownTypeNameInVarDeclIsE327() {
        List<MiraError> errors = errorsFor("var x : Frobnicate : 5;");
        assertTrue(hasCode(errors, "E327"));
    }

    @Test
    void typeAliasResolvesToAliasedType() {
        assertClean("type UserId : Number; var x : UserId : 5;");
    }

    @Test
    void typeAliasMismatchIsE324() {
        List<MiraError> errors = errorsFor("type UserId : Number; var x : UserId : \"hi\";");
        assertTrue(hasCode(errors, "E324"));
    }
}
