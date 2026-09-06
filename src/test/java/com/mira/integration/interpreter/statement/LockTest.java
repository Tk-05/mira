package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractLockTests;

public class LockTest extends AbstractLockTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void lockExecutesBody() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import thread as thread;
                var mu : thread.newMutex();
                var x : 0;
                lock(mu) { x : 1; }
                x;
                """)));
    }

    @Test
    void lockBodyRunsExactlyOnce() {
        assertEquals(5.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import thread as thread;
                var mu : thread.newMutex();
                var counter : 0;
                lock(mu) {
                    counter : (counter + 1);
                    counter : (counter + 4);
                }
                counter;
                """)));
    }

    @Test
    void lockSerializesParallelIncrements() {
        Object result = backend.runAndGetValue("""
                import thread as thread;
                import collection as col;
                var mu      : thread.newMutex();
                var counter : 0;
                fn inc() {
                    lock(mu) { counter : (counter + 1); }
                }
                var tasks : {};
                var i : 0;
                while (i < 10) {
                    col.push(tasks, spawn(fn() { inc(); }));
                    i : (i + 1);
                }
                for (var t in tasks) { await(t); }
                counter;
                """);
        assertEquals(10.0, ((Number) result).doubleValue(), 0);
    }

    @Test
    void multipleMutexesAreIndependent() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import thread as thread;
                var mu1 : thread.newMutex();
                var mu2 : thread.newMutex();
                var x : 0;
                lock(mu1) { x : (x + 1); }
                lock(mu2) { x : (x + 1); }
                x;
                """)));
    }

    @Test
    void lockReleasedAfterException() {
        Object result = backend.runAndGetValue("""
                import thread as thread;
                var mu      : thread.newMutex();
                var reached : false;
                try {
                    lock(mu) { throw oob("boom"); }
                } catch(oob) {}
                lock(mu) { reached : true; }
                reached;
                """);
        assertEquals(true, result);
    }

    @Test
    void nestedLocksDifferentMutexes() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import thread as thread;
                var mu1 : thread.newMutex();
                var mu2 : thread.newMutex();
                var x : 0;
                lock(mu1) {
                    x : (x + 1);
                    lock(mu2) { x : (x + 2); }
                }
                x;
                """)));
    }

    @Test
    void lockPreservesReturnValue() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import thread as thread;
                var mu : thread.newMutex();
                var result : 0;
                lock(mu) { result : 42; }
                result;
                """)));
    }

    @Test
    void highContentionCounterIsCorrect() {
        Object result = backend.runAndGetValue("""
                import thread as thread;
                import collection as col;
                var mu      : thread.newMutex();
                var counter : 0;
                fn inc() {
                    var j : 0;
                    while (j < 10) {
                        lock(mu) { counter : (counter + 1); }
                        j : (j + 1);
                    }
                }
                var tasks : {};
                var i : 0;
                while (i < 5) {
                    col.push(tasks, spawn(fn() { inc(); }));
                    i : (i + 1);
                }
                for (var t in tasks) { await(t); }
                counter;
                """);
        assertEquals(50.0, ((Number) result).doubleValue(), 0);
    }
}
