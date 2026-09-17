package com.mira.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.runtime.interpreter.Interpreter;

public class InterpreterRunner {

    private Interpreter interpreter;

    public InterpreterRunner() {
        reset();
    }

    public void reset() {
        ImportResolver.reset();
        interpreter = new Interpreter();
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public String run(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
        } finally {
            System.setOut(old);
        }
        return out.toString().trim();
    }

    public Object runAndGetValue(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        Object result = interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
        if (result instanceof Long l)
            return l.doubleValue();
        return result;
    }

    public Object runContinued(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        Object result = interpreter.runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(source, false)));
        if (result instanceof Long l)
            return l.doubleValue();
        return result;
    }

    public void createNewGlobalContext() {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        interpreter.run(parser.parseTokens(tokenizer.tokenize("", false)), false);
    }

    public static double normNum(Object value) {
        return ((Number) value).doubleValue();
    }
}
