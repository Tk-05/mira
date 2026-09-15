package com.mira.runtime.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.cli.Flags;
import com.mira.integration.InterpreterTestBase;

public class ProfilerTest extends InterpreterTestBase {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        Flags.inputPath.remove();
        ImportResolver.reset();
    }

    @Test
    void tracksFunctionCallCountsAndSelfTime() {
        interpreter.setProfilingEnabled(true);

        // "noisy" has a side effect (println), so it is never auto-memoized as pure -
        // every one of the 5 loop iterations must produce a real, separate call.
        run("""
                fn noisy(x) {
                    println(x);
                    return x;
                }

                for (var i in 0..5) {
                    noisy(i);
                }
                """);

        Profiler profiler = interpreter.getProfiler();

        assertEquals(5, profiler.getCalls("noisy"));
        assertTrue(profiler.getSelfNanos("noisy") <= profiler.getTotalNanos("noisy"));
        assertTrue(profiler.getTotalNanos("noisy") > 0);

        profiler.finishLineTracking();

        assertEquals(5, profiler.getLineHits(7));
        assertTrue(profiler.getTotalLineNanos() > 0);
        // Line 7 is the loop body at top level - not inside any named function,
        // and the test source has no module declaration.
        assertEquals("<script>", profiler.getLineFunction(7));
        assertEquals("<script>", profiler.getLineModule(7));
    }

    @Test
    void lineReportAttributesLinesToImportedModule() throws IOException {
        Flags.inputPath.set(tempDir.resolve("main.mira"));
        Files.writeString(tempDir.resolve("helper.mira"), """
                module Helper;
                pub fn helperFn() {
                    println("in helper");
                }
                """);

        interpreter.setProfilingEnabled(true);

        run("""
                import module "helper.mira";
                helperFn();
                """);

        Profiler profiler = interpreter.getProfiler();
        profiler.finishLineTracking();

        assertEquals(1, profiler.getLineHits(3));
        assertEquals("helperFn", profiler.getLineFunction(3));
        assertEquals("Helper", profiler.getLineModule(3));
    }

    @Test
    void memoizedPureFunctionCallsAreStillCountedOnCacheHit() {
        interpreter.setProfilingEnabled(true);

        // fib is auto-detected as pure and memoized: repeated calls with an
        // already-cached argument short-circuit without recursing further, so the
        // call count reflects the memoized trace (19 for fib(10)), not the naive
        // non-memoized recursion tree size (177). Cache hits must still be counted.
        run("""
                fn fib(n) {
                    if (n < 2) {
                        return n;
                    }
                    return fib(n - 1) + fib(n - 2);
                }

                fib(10);
                """);

        Profiler profiler = interpreter.getProfiler();

        assertEquals(19, profiler.getCalls("fib"));
    }

    @Test
    void disabledProfilerRecordsNothing() {
        run("""
                fn add(a, b) {
                    return a + b;
                }
                add(1, 2);
                """);

        Profiler profiler = interpreter.getProfiler();

        assertEquals(0, profiler.getCalls("add"));
        assertEquals(0, profiler.getLineHits(1));
    }
}
