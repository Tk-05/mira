package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class IfTest extends InterpreterTestBase {

    @Test
    void trueBranchExecutes() {
        try {
            run("var x : 5; if ($x > 3) { ret(true); } else { ret(false); }");
        } catch (ReturnSignal r) {
            org.junit.jupiter.api.Assertions.assertEquals("true", r.getValue());
        }
    }

    @Test
    void falseBranchExecutes() {
        try {
            run("var x : 1; if ($x > 3) { ret(true); } else { ret(false); }");
        } catch (ReturnSignal r) {
            org.junit.jupiter.api.Assertions.assertEquals("false", r.getValue());
        }
    }

    @Test
    void ifWithoutElse() {
        assertNull(run("var x : 1; if ($x > 3) { ret(true); }"));
    }

    @Test
    void nestedIf() {
        try {
            run("""
                    var x : 5;
                    var y : 10;
                    if ($x > 3) {
                        if ($y > 5) {
                            ret(true);
                        } else {
                            ret(false);
                        }
                    }
                    """);
        } catch (ReturnSignal r) {
            org.junit.jupiter.api.Assertions.assertEquals("true", r.getValue());
        }
    }

    @Test
    void ifWithLogicalAndCondition() {
        try {
            run("""
                    var x : 5;
                    var y : 10;
                    if ($x > 3 && $y > 5) {
                        ret(true);
                    } else {
                        ret(false);
                    }
                    """);
        } catch (ReturnSignal r) {
            org.junit.jupiter.api.Assertions.assertEquals("true", r.getValue());
        }
    }

    @Test
    void ifWithLogicalOrCondition() {
        try {
            run("""
                    var x : 1;
                    var y : 10;
                    if ($x > 3 || $y > 5) {
                        ret(true);
                    } else {
                        ret(false);
                    }
                    """);
        } catch (ReturnSignal r) {
            org.junit.jupiter.api.Assertions.assertEquals("true", r.getValue());
        }
    }

    @Test
    void ifWithNewlineInString() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            ImportResolver.reset();
            interpreter.run(parser.parseTokens(tokenizer.tokenize("""
                    import string;
                    var test : "\n";
                    if(charAt(0, $test) == "\n") {
                        ret();
                    }
                    """, false)), false);
        } catch (ReturnSignal r) {
        }
    }

    @Test
    void ifWithDelimiterInString() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            interpreter.run(parser.parseTokens(tokenizer.tokenize("""
                    import string;
                    var test : "}";
                    if(charAt(0, $test) == "}") {
                        ret();
                    }
                    """, false)), false);
        } catch (ReturnSignal r) {
        }
    }
}
