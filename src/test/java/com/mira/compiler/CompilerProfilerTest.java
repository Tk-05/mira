package com.mira.compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.runtime.interpreter.Profiler;

public class CompilerProfilerTest {

    private final Tokenizer tokenizer = new Tokenizer();
    private final Parser parser = new Parser();

    @AfterEach
    void teardown() {
        Flags.profile = false;
        Runtime.setProfiler(null);
        ImportResolver.reset();
    }

    private Profiler runProfiled(String source) {
        Flags.profile = true;
        ImportResolver.reset();
        List<Node> ast = parser.parseTokens(tokenizer.tokenize(source, false));
        Compiler compiler = new Compiler();
        Compiler.CompileResult result = compiler.compile(ast, "test.mira");

        Map<String, byte[]> all = new HashMap<>(result.lambdaClasses());
        all.put(result.className(), result.mainClass());
        CompiledClassLoader loader = new CompiledClassLoader(all);

        Runtime.setProfiler(new Profiler());

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
        }

        Profiler profiler = Runtime.getProfiler();
        profiler.finishLineTracking();
        return profiler;
    }

    @Test
    void tracksFunctionCallCounts() {
        Profiler profiler = runProfiled("""
                module ProfilerCheck;

                fn noisy(x) {
                    print($x);
                    return $x;
                }

                for (var i : 0; $i < 5; $i++) {
                    noisy($i);
                }
                """);

        assertEquals(5, profiler.getCalls("noisy"));
        assertTrue(profiler.getSelfNanos("noisy") <= profiler.getTotalNanos("noisy"));
    }

    @Test
    void tracksLineHitsWithFunctionAndModuleContext() {
        Profiler profiler = runProfiled("""
                module ProfilerCheck;

                fn work(x) {
                    return $x + 1;
                }

                work(1);
                """);

        assertEquals(1, profiler.getLineHits(4));
        assertEquals("work", profiler.getLineFunction(4));
        assertEquals("ProfilerCheck", profiler.getLineModule(4));
    }

    @Test
    void disabledProfilerRecordsNothing() {
        Flags.profile = false;
        ImportResolver.reset();
        List<Node> ast = parser.parseTokens(tokenizer.tokenize("""
                module ProfilerCheck;
                fn add(a, b) {
                    return $a + $b;
                }
                add(1, 2);
                """, false));
        Compiler.CompileResult result = new Compiler().compile(ast, "test.mira");

        Map<String, byte[]> all = new HashMap<>(result.lambdaClasses());
        all.put(result.className(), result.mainClass());
        CompiledClassLoader loader = new CompiledClassLoader(all);

        Profiler profiler = new Profiler();
        Runtime.setProfiler(profiler);

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
        }

        assertEquals(0, profiler.getCalls("add"));
        assertEquals(0, profiler.getLineHits(3));
    }
}
