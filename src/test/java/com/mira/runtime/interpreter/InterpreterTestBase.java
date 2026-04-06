package com.mira.runtime.interpreter;

import org.junit.jupiter.api.BeforeEach;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;

public abstract class InterpreterTestBase {

    protected Interpreter interpreter;

    @BeforeEach
    void setup() {
        interpreter = new Interpreter();
    }

    protected void createNewGlobalContext() {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        interpreter.run(parser.parseTokens(tokenizer.tokenize("", false)), false);
    }

    protected Object run(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
    }

    protected Object runContinued(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return interpreter.runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(source, false)));
    }
}
