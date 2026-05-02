package com.mira.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;

import com.mira.compiler.CompiledClassLoader;
import com.mira.compiler.Compiler;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.interpreter.Interpreter;

public abstract class InterpreterTestBase {

    protected Interpreter interpreter;
    protected boolean skipCompilerTest = false;

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
        Object result = normalize(interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false));

        if (!skipCompilerTest) {
            verifyWithCompiler(source, result);
        }

        return result;
    }

    protected Object runContinued(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return normalize(interpreter.runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(source, false))));
    }

    private void verifyWithCompiler(String source, Object interpreterResult) {
        String compilerSrc = toCompilerSource(source, interpreterResult);
        String stdout;
        try {
            stdout = compileAndRun(compilerSrc);
        } catch (Throwable t) {
            throw new AssertionError("Compiler failed on source: " + source.trim(), t);
        }

        if (interpreterResult != null) {
            String expected = toDisplayString(interpreterResult);
            assertCompilerOutput(expected, stdout, source);
        }
    }

    private static String toCompilerSource(String source, Object interpreterResult) {
        if (interpreterResult == null) {
            return source;
        }

        String trimmed = source.trim();
        if (!trimmed.endsWith(";")) {
            return source;
        }

        int lastStart = 0;
        int braces = 0, parens = 0;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < trimmed.length() - 1; i++) {
            char c = trimmed.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < trimmed.length()) {
                    i++;
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"', '\'' -> { inString = true; stringChar = c; }
                case '{' -> braces++;
                case '}' -> {
                    braces--;
                    if (braces == 0 && parens == 0) lastStart = i + 1;
                }
                case '(' -> parens++;
                case ')' -> parens--;
                case ';' -> {
                    if (braces == 0 && parens == 0) lastStart = i + 1;
                }
                default -> { }
            }
        }

        String prefix = trimmed.substring(0, lastStart).stripTrailing();
        String lastExpr = trimmed.substring(lastStart, trimmed.length() - 1).trim();

        String newLast = "print(" + lastExpr + ");";
        return prefix.isEmpty() ? newLast : prefix + "\n" + newLast;
    }

    private static String compileAndRun(String source) {
        com.mira.runtime.interpreter.ImportResolver.reset();
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(source, false));
        Compiler compiler = new Compiler();
        Compiler.CompileResult result = compiler.compile(ast, "test.mira");

        Map<String, byte[]> all = new HashMap<>(result.lambdaClasses());
        all.put(result.className(), result.mainClass());
        CompiledClassLoader loader = new CompiledClassLoader(all);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            Thread.currentThread().setContextClassLoader(loader);
            String dotName = result.className().replace('/', '.');
            Class<?> cls = loader.loadClass(dotName);
            Method main = cls.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(old);
            com.mira.runtime.interpreter.ImportResolver.reset();
        }
        return out.toString().trim();
    }

    private static String toDisplayString(Object value) {
        if (value instanceof Double d) {
            long l = d.longValue();
            if ((double) l == d) {
                return String.valueOf(l);
            }
            return d.toString();
        }
        if (value instanceof Long l) {
            return String.valueOf(l);
        }
        return String.valueOf(value);
    }

    private static void assertCompilerOutput(String expected, String actual, String source) {
        try {
            double expD = Double.parseDouble(expected);
            double actD = Double.parseDouble(actual);
            assertEquals(expD, actD, 1e-9, "Compiler output mismatch for source: " + source.trim());
        } catch (NumberFormatException e) {
            assertEquals(expected, actual, "Compiler output mismatch for source: " + source.trim());
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Long l) {
            return l.doubleValue();
        }
        return value;
    }

    protected static double normNum(Object value) {
        return ((Number) value).doubleValue();
    }
}
