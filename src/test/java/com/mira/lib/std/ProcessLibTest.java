package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class ProcessLibTest {

    Environment environment;
    Interpreter interpreter = new Interpreter();

    @BeforeEach
    void setup() {
        environment = new Environment();
        new Process().loadLib(environment);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    private double startProcess(String command) {
        return (double) call("processStart", command);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessStartReturnsId() {
        double id = startProcess("sleep 1");
        assertTrue(id >= 1);
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessStartIdsIncrement() {
        double id1 = startProcess("sleep 1");
        double id2 = startProcess("sleep 1");
        assertEquals(id2, id1 + 1);
        call("processKill", id1);
        call("processKill", id2);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessStartReturnsDouble() {
        double id = startProcess("sleep 1");
        assertInstanceOf(Double.class, id);
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testProcessStartOnWindows() {
        double id = startProcess("timeout /t 2 /nobreak");
        assertTrue(id >= 1);
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessAliveRunning() {
        double id = startProcess("sleep 5");
        assertEquals(true, call("processAlive", id));
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessAliveFinished() throws InterruptedException {
        double id = startProcess("true");
        Thread.sleep(200);
        assertEquals(false, call("processAlive", id));
    }

    @Test
    void testProcessAliveUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processAlive", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessAliveReturnsBoolean() {
        double id = startProcess("sleep 5");
        assertInstanceOf(Boolean.class, call("processAlive", id));
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessWaitReturnsExitCode() {
        double id = startProcess("true");
        assertEquals(0.0, call("processWait", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessWaitNonZeroExitCode() {
        double id = startProcess("false");
        assertNotEquals(0.0, call("processWait", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessWaitReturnsDouble() {
        double id = startProcess("true");
        assertInstanceOf(Double.class, call("processWait", id));
    }

    @Test
    void testProcessWaitUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processWait", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessKillReturnsNull() {
        double id = startProcess("sleep 5");
        assertNull(call("processKill", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessKillStopsProcess() throws InterruptedException {
        double id = startProcess("sleep 10");
        call("processKill", id);
        Thread.sleep(200);
        assertThrows(RuntimeException.class, () -> call("processAlive", id));
    }

    @Test
    void testProcessKillUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processKill", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessKillRemovesFromRegistry() {
        double id = startProcess("sleep 10");
        call("processKill", id);
        assertThrows(RuntimeException.class, () -> call("processAlive", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessOutputAfterFinished() throws InterruptedException {
        double id = startProcess("echo hello");
        Thread.sleep(200);
        String output = (String) call("processOutput", id);
        assertTrue(output.contains("hello"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessOutputReturnsString() throws InterruptedException {
        double id = startProcess("echo test");
        Thread.sleep(200);
        assertInstanceOf(String.class, call("processOutput", id));
    }

    @Test
    void testProcessOutputUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processOutput", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessExitCodeZero() throws InterruptedException {
        double id = startProcess("true");
        Thread.sleep(200);
        assertEquals(0.0, call("processExitCode", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessExitCodeNonZero() throws InterruptedException {
        double id = startProcess("false");
        Thread.sleep(200);
        assertNotEquals(0.0, call("processExitCode", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessExitCodeOnRunningThrows() {
        double id = startProcess("sleep 10");
        assertThrows(RuntimeException.class, () -> call("processExitCode", id));
        call("processKill", id);
    }

    @Test
    void testProcessExitCodeUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processExitCode", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessExitCodeReturnsDouble() throws InterruptedException {
        double id = startProcess("true");
        Thread.sleep(200);
        assertInstanceOf(Double.class, call("processExitCode", id));
    }

    @Test
    void testPidReturnsDouble() {
        assertInstanceOf(Double.class, call("pid"));
    }

    @Test
    void testPidIsPositive() {
        double pid = (double) call("pid");
        assertTrue(pid > 0);
    }

    @Test
    void testPidMatchesProcessHandle() {
        double pid = (double) call("pid");
        assertEquals((double) ProcessHandle.current().pid(), pid);
    }

    @Test
    void testListProcessesReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("listProcesses"));
    }

    @Test
    void testListProcessesNotEmpty() {
        ListExpression result = (ListExpression) call("listProcesses");
        assertTrue(result.getMembers().size() > 0);
    }

    @Test
    void testListProcessesContainsCurrentPid() {
        ListExpression result = (ListExpression) call("listProcesses");
        String currentPid = String.valueOf((long) ProcessHandle.current().pid());
        boolean found = result.getMembers().stream()
                .anyMatch(e -> e.toString().contains(currentPid));
        assertTrue(found);
    }

    @Test
    void testProcessInfoCurrentPid() {
        double pid = (double) call("pid");
        String info = (String) call("processInfo", pid);
        assertNotNull(info);
        assertFalse(info.isEmpty());
    }

    @Test
    void testProcessInfoUnknownPidReturnsUnknown() {
        assertEquals("unknown", call("processInfo", 999999999.0));
    }

    @Test
    void testProcessInfoReturnsString() {
        double pid = (double) call("pid");
        assertInstanceOf(String.class, call("processInfo", pid));
    }
}
