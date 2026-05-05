package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class LockTest extends InterpreterTestBase {

    @Test
    void lockExecutesBody() {
        assertEquals(1.0, run("""
                import thread as thread;
                var mu : thread.newMutex();
                var x : 0;
                lock($mu) { $x : 1; }
                $x;
                """));
    }

    @Test
    void lockBodyRunsExactlyOnce() {
        assertEquals(5.0, run("""
                import thread as thread;
                var mu : thread.newMutex();
                var counter : 0;
                lock($mu) {
                    $counter : eval($counter + 1);
                    $counter : eval($counter + 4);
                }
                $counter;
                """));
    }

    @Test
    void lockSerializesParallelIncrements() {
        Object result = run("""
                import thread as thread;
                import collection as col;
                var mu      : thread.newMutex();
                var counter : 0;
                fn inc() {
                    lock($mu) { $counter : eval($counter + 1); }
                }
                var tasks : {};
                var i : 0;
                while ($i < 10) {
                    col.push($tasks, spawn(fn() { inc(); }));
                    $i : eval($i + 1);
                }
                foreach (var t in $tasks) { await($t); }
                $counter;
                """);
        assertEquals(10.0, ((Number) result).doubleValue(), 0);
    }

    @Test
    void multipleMutexesAreIndependent() {
        assertEquals(2.0, run("""
                import thread as thread;
                var mu1 : thread.newMutex();
                var mu2 : thread.newMutex();
                var x : 0;
                lock($mu1) { $x : eval($x + 1); }
                lock($mu2) { $x : eval($x + 1); }
                $x;
                """));
    }

    @Test
    void lockReleasedAfterException() {
        Object result = run("""
                import thread as thread;
                var mu      : thread.newMutex();
                var reached : false;
                try {
                    lock($mu) { throw oob("boom"); }
                } catch(oob) {}
                lock($mu) { $reached : true; }
                $reached;
                """);
        assertEquals(true, result);
    }

    @Test
    void nestedLocksDifferentMutexes() {
        assertEquals(3.0, run("""
                import thread as thread;
                var mu1 : thread.newMutex();
                var mu2 : thread.newMutex();
                var x : 0;
                lock($mu1) {
                    $x : eval($x + 1);
                    lock($mu2) { $x : eval($x + 2); }
                }
                $x;
                """));
    }

    @Test
    void lockPreservesReturnValue() {
        assertEquals(42.0, run("""
                import thread as thread;
                var mu : thread.newMutex();
                var result : 0;
                lock($mu) { $result : 42; }
                $result;
                """));
    }

    @Test
    void highContentionCounterIsCorrect() {
        Object result = run("""
                import thread as thread;
                import collection as col;
                var mu      : thread.newMutex();
                var counter : 0;
                fn inc() {
                    var j : 0;
                    while ($j < 10) {
                        lock($mu) { $counter : eval($counter + 1); }
                        $j : eval($j + 1);
                    }
                }
                var tasks : {};
                var i : 0;
                while ($i < 5) {
                    col.push($tasks, spawn(fn() { inc(); }));
                    $i : eval($i + 1);
                }
                foreach (var t in $tasks) { await($t); }
                $counter;
                """);
        assertEquals(50.0, ((Number) result).doubleValue(), 0);
    }
}
