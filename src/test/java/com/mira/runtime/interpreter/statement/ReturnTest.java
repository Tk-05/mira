package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;
import com.mira.runtime.interpreter.NullValue;

public class ReturnTest extends InterpreterTestBase {

    @Test
    void returnWithNumericValue() {
        try {
            run("return eval(42);");
        } catch (ReturnSignal r) {
            assertEquals(42.0, normNum(r.getValue()));
        }
    }

    @Test
    void returnWithZero() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            interpreter.run(parser.parseTokens(tokenizer.tokenize("return eval(0);", true)), false);
        } catch (ReturnSignal r) {
            assertEquals(0.0, normNum(r.getValue()));
        }
    }

    @Test
    void returnWithNullValue() {
        try {
            run("return;");
        } catch (ReturnSignal r) {
            assertEquals("0.0", r.getValue());
        }
    }

    @Test
    void returnFromVariable() {
        try {
            run("var x : 24; var y : 18; var z : eval($x + $y); return $z;");
        } catch (ReturnSignal r) {
            assertEquals(42.0, normNum(r.getValue()));
        }
    }

    @Test
    void returnInsideFunctionDoesNotPropagate() {
        assertEquals(42.0, run("fn answer() { return 42; } eval(answer());"));
    }

    @Test
    void returnInsideFunctionWithString() {
        try {
            run("fn msg() { return \"done\"; } var r : \"return msg();\"; exec($r);");
        } catch (ReturnSignal r) {
            assertEquals("done", r.getValue());
        }
    }

    @Test
    void returnUninitializedVariable() {
        try {
            run("var x; return $x;");
        } catch (ReturnSignal r) {
            assertInstanceOf(NullValue.class, r.getValue());
        }
    }
}
