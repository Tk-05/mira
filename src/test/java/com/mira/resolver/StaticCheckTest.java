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
        Flags.strictTypes = false;
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
    void callNonexistentMethodOnStructInstanceIsE323() {
        List<MiraError> errors = errorsFor(
                "var counter : struct { var count : 0; fn get() { return this.count; } }; "
                + "var c : counter{}; c.missing();");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void callLambdaHeldInStructFieldIsClean() {
        assertClean("var box : struct { var run; }; var b : box{ run : fn() { return 1; } }; b.run();");
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
    void callNonexistentMethodOnFunctionParamViaCallSiteIsE323() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; fn get() { return this.x; } }; var origin : point{}; "
                + "fn hello(name) { name.missing(); } hello(origin);");
        assertTrue(hasCode(errors, "E323"));
    }

    @Test
    void callExistingMethodOnFunctionParamViaCallSiteIsClean() {
        assertClean(
                "var point : struct { var x; fn get() { return this.x; } }; var origin : point{}; "
                + "fn hello(name) { name.get(); } hello(origin);");
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
        List<MiraError> errors = errorsFor("var x : Number : 5; x : \"hi\";");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void typedVarReassignmentMatchingIsClean() {
        assertClean("var x : Number : 5; x : 6;");
    }

    @Test
    void untypedVarReassignmentToDifferentShapeIsUnaffected() {
        assertClean("var x : 5; x : \"hi\";");
    }

    @Test
    void nullableTypedVarAcceptsNull() {
        assertClean("var x : Number? : null; x : 5;");
    }

    @Test
    void callArgumentTypeMismatchIsE325() {
        List<MiraError> errors = errorsFor(
                "fn add(a : Number, b : Number) { return eval(a + b); } add(1, \"x\");");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void callArgumentTypeMatchingIsClean() {
        assertClean("fn add(a : Number, b : Number) { return eval(a + b); } add(1, 2);");
    }

    @Test
    void callToUntypedFunctionIsUnaffected() {
        assertClean("fn add(a, b) { return eval(a + b); } add(1, \"x\");");
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
    void voidFunctionWithNoReturnStatementIsClean() {
        assertClean("fn log(msg) -> Void { println(msg); }");
    }

    @Test
    void voidFunctionWithBareReturnIsClean() {
        assertClean("fn log(msg) -> Void { println(msg); return; }");
    }

    @Test
    void voidFunctionReturningValueIsE326() {
        List<MiraError> errors = errorsFor("fn log(msg) -> Void { return 5; }");
        assertTrue(hasCode(errors, "E326"));
    }

    @Test
    void voidFunctionReturningNullLiteralIsStillE326() {
        // returning an explicit value - even `null` itself - is still wrong for
        // Void, unlike a `-> Null` declared function which would accept this
        List<MiraError> errors = errorsFor("fn log(msg) -> Void { return null; }");
        assertTrue(hasCode(errors, "E326"));
    }

    @Test
    void nullReturnTypeStillAcceptsExplicitNull() {
        assertClean("fn log(msg) -> Null { return null; }");
    }

    @Test
    void bareReturnAgainstDeclaredNullTypeIsClean() {
        // regression: a bare `return;` is parsed as a synthetic 0.0-literal
        // sentinel value internally (Parser.parseReturn), not a true null -
        // must not be misread as an explicit Number return
        assertClean("fn f() -> Null { return; }");
    }

    @Test
    void bareReturnAgainstDeclaredNumberTypeIsE326() {
        List<MiraError> errors = errorsFor("fn f() -> Number { return; }");
        assertTrue(hasCode(errors, "E326"));
    }

    @Test
    void explicitZeroPointZeroReturnAgainstNumberTypeIsClean() {
        // regression: must not be confused with the bare-return sentinel,
        // which also happens to be a "0.0" literal internally
        assertClean("fn f() -> Number { return 0.0; }");
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

    @Test
    void strictModeOffAllowsUnannotatedTopLevelFunction() {
        assertClean("fn add(a, b) { return eval(a + b); }");
    }

    @Test
    void strictModeFlagsMissingParamType() {
        Flags.strictTypes = true;
        List<MiraError> errors = errorsFor("fn add(a, b : Number : 0) -> Number { return b; }");
        assertTrue(hasCode(errors, "E328"));
    }

    @Test
    void strictModeFlagsMissingReturnType() {
        Flags.strictTypes = true;
        List<MiraError> errors = errorsFor("fn add(a : Number, b : Number) { return eval(a + b); }");
        assertTrue(hasCode(errors, "E328"));
    }

    @Test
    void strictModeFlagsCompletelyUnannotatedFunction() {
        Flags.strictTypes = true;
        List<MiraError> errors = errorsFor("fn add(a, b) { return eval(a + b); }");
        assertTrue(hasCode(errors, "E328"));
    }

    @Test
    void strictModeAllowsFullyAnnotatedFunction() {
        Flags.strictTypes = true;
        assertClean("fn add(a : Number, b : Number) -> Number { return eval(a + b); }");
    }

    @Test
    void structFieldOverrideTypeMismatchIsE329() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x : Number : 0; }; var p : point{x : \"oops\"};");
        assertTrue(hasCode(errors, "E329"));
    }

    @Test
    void structFieldOverrideMatchingTypeIsClean() {
        assertClean("var point : struct { var x : Number : 0; }; var p : point{x : 5};");
    }

    @Test
    void structFieldWithoutTypeIsUnaffectedByOverrideShape() {
        assertClean("var point : struct { var x : 0; }; var p : point{x : \"anything\"};");
    }

    @Test
    void structFieldOverrideOnUnknownFieldStaysE323NotE329() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x : Number : 0; }; var p : point{z : 5};");
        assertTrue(hasCode(errors, "E323"));
        assertFalse(hasCode(errors, "E329"));
    }

    // --- Struct nominal typing (return types) ---
    @Test
    void functionReturningMatchingStructTemplateIsClean() {
        assertClean(
                "var point : struct { var x; var y; }; "
                + "fn make() -> point { return point{x : 1, y : 2}; } make();");
    }

    @Test
    void functionReturningStructInstanceThroughVariableIsClean() {
        assertClean(
                "var point : struct { var x; var y; }; var origin : point{x : 0, y : 0}; "
                + "fn get() -> point { return origin; } get();");
    }

    @Test
    void functionReturningDifferentStructTemplateIsE326() {
        List<MiraError> errors = errorsFor(
                "var point : struct { var x; var y; }; var color : struct { var r; }; "
                + "fn make() -> point { return color{r : 1}; } make();");
        assertTrue(hasCode(errors, "E326"));
    }

    @Test
    void structInitReturnedWithoutDeclaredReturnTypeIsUnaffected() {
        assertClean(
                "var point : struct { var x; var y; }; "
                + "fn make() { return point{x : 1, y : 2}; } make();");
    }

    @Test
    void realDollarReferenceIsUnaffectedByBarewordInference() {
        assertClean("fn f(a : Number) { println(a); } var n : Number : 5; f(n);");
    }

    // --- Field reassignment type checking (after struct-init, not just at instantiation) ---
    @Test
    void structFieldReassignmentTypeMismatchIsE329() {
        List<MiraError> errors = errorsFor(
                "var Point : struct { var x : Number : 0; }; var p : Point{}; p.x : \"oops\";");
        assertTrue(hasCode(errors, "E329"));
    }

    @Test
    void structFieldReassignmentMatchingTypeIsClean() {
        assertClean("var Point : struct { var x : Number : 0; }; var p : Point{}; p.x : 5;");
    }

    @Test
    void objectFieldReassignmentTypeMismatchIsE329() {
        List<MiraError> errors = errorsFor("var o : { var x : Number : 0; }; o.x : \"oops\";");
        assertTrue(hasCode(errors, "E329"));
    }

    @Test
    void untypedFieldReassignmentIsUnaffected() {
        assertClean("var Point : struct { var x : 0; }; var p : Point{}; p.x : \"anything\";");
    }

    @Test
    void constCollectionFieldReassignmentStillReportsE321() {
        // regression: the const-mutation check that already lived in this code path
        // must survive being refactored to share logic with the new type check
        List<MiraError> errors = errorsFor("const p : { var x : 0; }; p.x : 5;");
        assertTrue(hasCode(errors, "E321"));
    }

    // --- Method-call and call-via-variable argument type checking ---
    @Test
    void structMethodCallArgumentMismatchIsE325() {
        List<MiraError> errors = errorsFor(
                "var Point : struct { var x : Number : 0; fn set(v : Number) { this.x : v; } }; "
                + "var p : Point{}; p.set(\"oops\");");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void structMethodCallMatchingArgumentIsClean() {
        assertClean(
                "var Point : struct { var x : Number : 0; fn set(v : Number) { this.x : v; } }; "
                + "var p : Point{}; p.set(5);");
    }

    @Test
    void objectMethodCallArgumentMismatchIsE325() {
        List<MiraError> errors = errorsFor(
                "var o : { var x : Number : 0; fn set(v : Number) { this.x : v; } }; o.set(\"oops\");");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void lambdaValueCallArgumentMismatchIsE325() {
        List<MiraError> errors = errorsFor("var f : fn(a : Number) { return a; }; f(\"oops\");");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void lambdaValueCallMatchingArgumentIsClean() {
        assertClean("var f : fn(a : Number) { return a; }; f(5);");
    }

    @Test
    void untypedLambdaValueCallIsUnaffected() {
        assertClean("var f : fn(a) { return a; }; f(\"anything\");");
    }

    // --- Diagnostic position regressions ---
    @Test
    void argumentTypeMismatchPointsAtTheArgumentNotColumnZero() {
        // regression: this used to hardcode column 0, so the editor
        // underlined whatever happened to sit at the start of that line
        // instead of the actual offending argument
        List<MiraError> errors = errorsFor(
                "fn f(a : Number) { println(a); } var s : String : \"hi\"; f(s);");
        MiraError err = errors.stream().filter(e -> "E325".equals(e.getErrorCode())).findFirst().orElseThrow();
        assertTrue(err.getColumn() > 0);
    }

    @Test
    void structFieldTypeMismatchPointsAtTheOverrideValueNotColumnZero() {
        List<MiraError> errors = errorsFor(
                "var Point : struct { var x : Number : 0; }; var p : Point{x : \"oops\"};");
        MiraError err = errors.stream().filter(e -> "E329".equals(e.getErrorCode())).findFirst().orElseThrow();
        assertTrue(err.getColumn() > 0);
    }

    // --- Binary operator operand type checking ---
    @Test
    void explicitlyTypedOperandMismatchInPlusIsE330() {
        // a genuine mismatch: neither side is Number+Number, and neither
        // side is a String (which '+' always allows, as concatenation)
        List<MiraError> errors = errorsFor(
                "var flag : Bool : true; var n : Number : 5; var r : flag + n;");
        assertTrue(hasCode(errors, "E330"));
    }

    @Test
    void explicitlyTypedOperandsSameTypeInPlusIsClean() {
        assertClean("var a : Number : 5; var b : Number : 3; var r : a + b;");
    }

    @Test
    void explicitlyTypedStringAndNumberInPlusIsCleanConcatenation() {
        // '+' on a String and anything else is concatenation, not arithmetic -
        // unlike every other arithmetic operator, mismatched types here are fine
        assertClean("var n : Number : 5; var s : String : \"x\"; var r : s + n;");
    }

    @Test
    void explicitlyTypedNonNumberOperandInMinusIsE330() {
        List<MiraError> errors = errorsFor("var s : String : \"x\"; var r : s - 1;");
        assertTrue(hasCode(errors, "E330"));
    }

    @Test
    void explicitlyTypedNumberOperandsInMinusIsClean() {
        assertClean("var a : Number : 5; var r : a - 1;");
    }

    @Test
    void bareLiteralMismatchInMinusStaysSoftWarningNotE330() {
        // regression guard: with no explicit annotation on either side, this must
        // stay covered only by the pre-existing soft-warning system (see
        // arithmeticOnStringLiteralProducesWarning above), never escalate to E330
        assertClean("var r : \"foo\" - 1;");
    }

    @Test
    void untypedVariablesInPlusAreUnaffected() {
        assertClean("var a : 5; var b : 3; var r : a + b;");
    }

    @Test
    void typedFunctionReturnStringConcatenationInPlusIsClean() {
        // '+' with a typed function's Number return and a String is
        // concatenation, not a mismatch - same as any other String operand
        assertClean("fn getNum() -> Number { return 1; } var s : String : \"x\"; var r : getNum() + s;");
    }

    // --- Parameter default value type checking ---
    @Test
    void paramDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor("fn f(a : Number : \"wrong\") { println(a); }");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void paramDefaultMatchingTypeIsClean() {
        assertClean("fn f(a : Number : 5) { println(a); }");
    }

    @Test
    void untypedParamDefaultIsUnaffected() {
        assertClean("fn f(a : \"anything\") { println(a); }");
    }

    @Test
    void structMethodParamDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor(
                "var Point : struct { fn set(v : Number : \"wrong\") { println(v); } };");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void objectMethodParamDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor(
                "var o : { fn set(v : Number : \"wrong\") { println(v); } };");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void lambdaParamDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor("var f : fn(a : Number : \"wrong\") { return a; };");
        assertTrue(hasCode(errors, "E324"));
    }

    // --- Enum member type inference ---
    @Test
    void enumMemberTypeMismatchIsE324() {
        List<MiraError> errors = errorsFor(
                "enum Color { RED, GREEN } enum Size { SMALL, LARGE } var c : Color : Size.SMALL;");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void enumMemberMatchingTypeIsClean() {
        assertClean("enum Color { RED, GREEN } var c : Color : Color.RED;");
    }

    @Test
    void enumMemberUnannotatedUsageIsUnaffected() {
        assertClean("enum Color { RED, GREEN } var c : Color.RED; println(c);");
    }

    @Test
    void enumMemberArgumentTypeMismatchIsE325() {
        List<MiraError> errors = errorsFor(
                "enum Color { RED, GREEN } enum Size { SMALL, LARGE } "
                + "fn f(c : Color) { println(c); } f(Size.SMALL);");
        assertTrue(hasCode(errors, "E325"));
    }

    // --- Struct/object field's own default value type checking ---
    @Test
    void structFieldOwnDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor("var Point : struct { var x : Number : \"wrong\"; };");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void structFieldOwnDefaultMatchingTypeIsClean() {
        assertClean("var Point : struct { var x : Number : 0; };");
    }

    @Test
    void objectFieldOwnDefaultMismatchIsE324() {
        List<MiraError> errors = errorsFor("var o : { var x : Number : \"wrong\"; };");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void untypedStructFieldOwnDefaultIsUnaffected() {
        assertClean("var Point : struct { var x : \"anything\"; };");
    }

    // --- Ternary/switch branches checked against the declaring type ---
    @Test
    void ternaryBranchMismatchAgainstDeclaredTypeIsE324() {
        List<MiraError> errors = errorsFor("var c : Number : true ? 1 : \"two\";");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void ternaryBranchesMatchingDeclaredTypeIsClean() {
        assertClean("var c : Number : true ? 1 : 2;");
    }

    @Test
    void switchCaseResultMismatchAgainstDeclaredTypeIsE324() {
        List<MiraError> errors = errorsFor(
                "var n : Number : 1; "
                + "var c : Number : switch(n) { case(1) -> \"one\" default -> 2 };");
        assertTrue(hasCode(errors, "E324"));
    }

    @Test
    void switchCaseResultsMatchingDeclaredTypeIsClean() {
        assertClean(
                "var n : Number : 1; "
                + "var c : Number : switch(n) { case(1) -> 1 default -> 2 };");
    }

    @Test
    void untypedTernaryIsUnaffected() {
        assertClean("var c : true ? 1 : \"two\";");
    }

    // --- Comparison operator operand type checking ---
    @Test
    void explicitlyTypedOperandMismatchInLessThanIsE330() {
        List<MiraError> errors = errorsFor("var n : Number : 5; var t : String : \"x\"; var r : n < t;");
        assertTrue(hasCode(errors, "E330"));
    }

    @Test
    void explicitlyTypedOperandsSameTypeInLessThanIsClean() {
        assertClean("var a : Number : 5; var b : Number : 3; var r : a < b;");
    }

    @Test
    void bareLiteralMismatchInLessThanIsUnaffected() {
        assertClean("var r : \"foo\" < 1;");
    }

    @Test
    void untypedVariablesInLessThanAreUnaffected() {
        assertClean("var a : 5; var b : 3; var r : a < b;");
    }

    @Test
    void explicitlyTypedOperandMismatchInGreaterEqualIsE330() {
        List<MiraError> errors = errorsFor("var n : Number : 5; var t : String : \"x\"; var r : n >= t;");
        assertTrue(hasCode(errors, "E330"));
    }

    // --- Unary minus/tilde operand type checking ---
    @Test
    void unaryMinusOnExplicitlyTypedStringIsE331() {
        List<MiraError> errors = errorsFor("var s : String : \"hi\"; var r : -s;");
        assertTrue(hasCode(errors, "E331"));
    }

    @Test
    void unaryMinusOnExplicitlyTypedNumberIsClean() {
        assertClean("var n : Number : 5; var r : -n;");
    }

    @Test
    void unaryMinusOnBarewordStaysUnaffected() {
        // regression guard: bare literals/barewords have no explicit type to gate
        // on, so this must stay covered only by the pre-existing warnIfStringOperand
        // warning (see unaryMinusOnStringLiteralProducesWarning), never escalate
        assertClean("var r : -\"foo\";");
    }

    @Test
    void unaryTildeOnExplicitlyTypedStringIsE331() {
        List<MiraError> errors = errorsFor("var s : String : \"hi\"; var r : ~s;");
        assertTrue(hasCode(errors, "E331"));
    }

    // --- Calling a variable known to hold a non-callable value ---
    @Test
    void callingLiteralNumberVariableIsE332() {
        List<MiraError> errors = errorsFor("var x : 5; x();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingExplicitlyTypedNonFnVariableIsE332() {
        List<MiraError> errors = errorsFor("var s : String : \"hi\"; s();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingStructInstanceVariableIsE332() {
        List<MiraError> errors = errorsFor(
                "var Point : struct { var x : 0; }; var p : Point{}; p();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingLambdaVariableIsClean() {
        assertClean("var f : fn() { return 1; }; f();");
    }

    @Test
    void callingFnTypedParameterIsClean() {
        assertClean("fn apply(cb : Fn) { cb(); }");
    }

    @Test
    void callingTypedFnParamWithMismatchedArgumentIsE325() {
        List<MiraError> errors = errorsFor(
                "fn apply(cb : Fn(Number, Number) -> Number) { cb(\"oops\", 2); }");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void callingTypedFnParamWithMatchingArgumentsIsClean() {
        assertClean("fn apply(cb : Fn(Number, Number) -> Number) { cb(1, 2); }");
    }

    @Test
    void passingMismatchedLambdaToTypedFnParamIsE325() {
        List<MiraError> errors = errorsFor(
                "fn apply(cb : Fn(Number) -> Number) { cb(1); } "
                + "apply(fn(a : String) { return a; });");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void passingMatchingLambdaToTypedFnParamIsClean() {
        assertClean(
                "fn apply(cb : Fn(Number) -> Number) { cb(1); } "
                + "apply(fn(a : Number) { return a; });");
    }

    @Test
    void passingMismatchedNamedFunctionToTypedFnParamIsE325() {
        List<MiraError> errors = errorsFor(
                "fn apply(cb : Fn(Number, Number) -> Number) { cb(1, 2); } "
                + "fn wrong(a : String, b : String) { return a; } apply(wrong);");
        assertTrue(hasCode(errors, "E325"));
    }

    @Test
    void passingMatchingNamedFunctionToTypedFnParamIsClean() {
        assertClean(
                "fn apply(cb : Fn(Number, Number) -> Number) { cb(1, 2); } "
                + "fn add(a : Number, b : Number) { return a + b; } apply(add);");
    }

    @Test
    void callingUntypedParameterIsUnaffected() {
        assertClean("fn apply(cb) { cb(); }");
    }

    @Test
    void callingUntypedUntrackedVariableIsUnaffected() {
        // gradual typing: no declared type and no known literal shape means the
        // check has nothing to go on, so it must stay silent rather than guess
        assertClean("fn wrap(v) { var f : v; f(); }");
    }

    // --- Negative-number-literal tracking (a UnaryExpression, not a DumbExpression) ---
    @Test
    void callingNegativeLiteralVariableIsE332() {
        List<MiraError> errors = errorsFor("var x : -1; x();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void negativeLiteralArithmeticStaysClean() {
        // regression guard: recognizing `-1` as a known Number literal must not
        // disturb ordinary arithmetic on it
        assertClean("var n : -5; var r : n - 1; println(r);");
    }

    // --- Reassignment through a ternary/switch whose branches agree on type ---
    @Test
    void callingVariableReassignedViaTernaryWithAgreeingBranchesIsE332() {
        List<MiraError> errors = errorsFor("var a : () -> 0; a : true ? 69 : -1; a();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingVariableReassignedViaTernaryWithDisagreeingBranchesIsUnaffected() {
        // branches disagree on type (Number vs String) - the reassignment can't
        // be classified, so tracking is dropped (falls back to "unknown") rather
        // than guessed; this must not produce a false positive
        assertClean("var a : () -> 0; a : true ? 1 : \"two\"; a();");
    }

    @Test
    void callingVariableReassignedViaSwitchWithAgreeingBranchesIsE332() {
        List<MiraError> errors = errorsFor(
                "var a : () -> 0; var n : Number : 1; "
                + "a : switch(n) { case(1) -> 1 default -> 2 }; a();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void ternaryReassignmentToAFunctionStaysCallable() {
        assertClean("var a : () -> 0; a : true ? (() -> 1) : (() -> 2); println(a());");
    }

    // --- Boolean negation / bitwise NOT literal tracking ---
    @Test
    void callingBooleanNegationLiteralVariableIsE332() {
        List<MiraError> errors = errorsFor("var y : !true; y();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingBitwiseNotLiteralVariableIsE332() {
        List<MiraError> errors = errorsFor("var z : ~5; z();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void booleanNegationStaysUsableAsABool() {
        assertClean("var y : !true; var b : Bool : y;");
    }

    // --- Tracking a variable's type through a typed-function-call result ---
    @Test
    void callingVariableDeclaredFromTypedFunctionCallIsE332() {
        List<MiraError> errors = errorsFor(
                "fn getNum() -> Number { return 1; } var a : getNum(); a();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void callingVariableReassignedFromTypedFunctionCallIsE332() {
        List<MiraError> errors = errorsFor(
                "fn getNum() -> Number { return 1; } var a : () -> 0; a : getNum(); a();");
        assertTrue(hasCode(errors, "E332"));
    }

    @Test
    void reassigningFromFnTypedFunctionCallStaysCallable() {
        assertClean(
                "fn getFn() -> Fn { return () -> 42; } var a : () -> 0; a : getFn(); println(a());");
    }

    @Test
    void reassigningFromUntypedFunctionCallIsUnaffected() {
        assertClean("fn getNum() { return 1; } var a : () -> 0; a : getNum(); a();");
    }
}
