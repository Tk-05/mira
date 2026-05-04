package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.integration.InterpreterTestBase;

public class IfTest extends InterpreterTestBase {

    @Test
    void trueBranchExecutes() {
        try {
            run("var x : 5; if ($x > 3) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void falseBranchExecutes() {
        try {
            run("var x : 1; if ($x > 3) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }

    @Test
    void ifWithoutElse() {
        assertNull(run("var x : 1; if ($x > 3) { return true; }"));
    }

    @Test
    void nestedIf() {
        try {
            run("""
                    var x : 5;
                    var y : 10;
                    if ($x > 3) {
                        if ($y > 5) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void ifWithLogicalAndCondition() {
        try {
            run("""
                    var x : 5;
                    var y : 10;
                    if ($x > 3 && $y > 5) {
                        return true;
                    } else {
                        return false;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void ifWithLogicalOrCondition() {
        try {
            run("""
                    var x : 1;
                    var y : 10;
                    if ($x > 3 || $y > 5) {
                        return true;
                    } else {
                        return false;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void ifWithTrueLiteral() {
        try {
            run("if(true) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void ifWithFalseLiteral() {
        try {
            run("if(false) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }

    @Test
    void ifWithBooleanVariable() {
        try {
            run("var x : true; if($x) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
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
                    if(charAt($test, 0) == "\n") {
                        return;
                    }
                    """, false)), false);
        } catch (ReturnSignal r) {
        }
    }

    @Test
    void elseIfTaken() {
        try {
            run("var x : 2; if ($x > 3) { return false; } else if ($x > 1) { return true; } else { return false; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void elseIfSkipped() {
        try {
            run("var x : 0; if ($x > 3) { return false; } else if ($x > 1) { return false; } else { return true; }");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void elseIfChain() {
        try {
            run("""
                    var x : 5;
                    if ($x == 1) {
                        return 1;
                    } else if ($x == 2) {
                        return 2;
                    } else if ($x == 5) {
                        return 5;
                    } else {
                        return 0;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(5.0, normNum(r.getValue()));
        }
    }

    @Test
    void ifWithDelimiterInString() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            interpreter.run(parser.parseTokens(tokenizer.tokenize("""
                    import string as s;
                    var test : "}";
                    if(s.charAt($test, 0) == "}") {
                        return;
                    }
                    """, false)), false);
        } catch (ReturnSignal r) {
        }
    }
}
