package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.values.NullValue;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractReturnTests;

public class ReturnTest extends AbstractReturnTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void returnWithNumericValue() {
        try {
            backend.runAndGetValue("return (42);");
        } catch (ReturnSignal r) {
            assertEquals(42.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void returnWithZeroViaTokenizer() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            backend.getInterpreter().run(parser.parseTokens(tokenizer.tokenize("return (0);", true)), false);
        } catch (ReturnSignal r) {
            assertEquals(0.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void returnWithNullValue() {
        try {
            backend.runAndGetValue("return;");
        } catch (ReturnSignal r) {
            assertEquals("0.0", r.getValue());
        }
    }

    @Test
    void returnFromVariable() {
        try {
            backend.runAndGetValue("var x : 24; var y : 18; var z : (x + y); return z;");
        } catch (ReturnSignal r) {
            assertEquals(42.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void returnUninitializedVariable() {
        try {
            backend.runAndGetValue("var x; return x;");
        } catch (ReturnSignal r) {
            assertInstanceOf(NullValue.class, r.getValue());
        }
    }
}
