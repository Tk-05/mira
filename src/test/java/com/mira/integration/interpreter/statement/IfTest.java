package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractIfTests;

public class IfTest extends AbstractIfTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void ifWithoutElse() {
        assertNull(backend.runAndGetValue("var x : 1; if ($x > 3) { return true; }"));
    }

    @Test
    void ifWithNewlineInString() {
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            ImportResolver.reset();
            backend.getInterpreter().run(parser.parseTokens(tokenizer.tokenize("""
                    import string;
                    var str : "\n";
                    if(charAt($str, 0) == "\n") {
                        return;
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
            backend.getInterpreter().run(parser.parseTokens(tokenizer.tokenize("""
                    import string as s;
                    var str : "}";
                    if(s.charAt($str, 0) == "}") {
                        return;
                    }
                    """, false)), false);
        } catch (ReturnSignal r) {
        }
    }
}
