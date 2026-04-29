package com.mira.compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public abstract class CompilerTestBase {

    private final Tokenizer tokenizer = new Tokenizer();
    private final Parser parser = new Parser();

    protected String run(String source) {
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));
        MiraCompiler compiler = new MiraCompiler();
        MiraCompiler.CompileResult result = compiler.compile(ast, "test.mira");

        Map<String, byte[]> all = new HashMap<>(result.lambdaClasses());
        all.put(result.className(), result.mainClass());
        CompiledClassLoader loader = new CompiledClassLoader(all);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));
        try {
            String dotName = result.className().replace('/', '.');
            Class<?> cls = loader.loadClass(dotName);
            Method main = cls.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(old);
        }
        return out.toString().trim();
    }
}
