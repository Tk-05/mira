package com.mira.lib.std;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class ProcessLibTest {

    Environment environment;
    Interpreter interpreter = new Interpreter();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        environment = new Environment();
        new Process().loadLib(environment);
    }

    @AfterEach
    void restoreStderr() {
        call("uninstallCrashLog");
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
    @EnabledOnOs(OS.LINUX)
    void testProcessDoneRunning() {
        double id = startProcess("sleep 5");
        assertEquals(false, call("processDone", id));
        call("processKill", id);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessDoneFinished() throws InterruptedException {
        double id = startProcess("true");
        Thread.sleep(200);
        assertEquals(true, call("processDone", id));
    }

    @Test
    void testProcessDoneUnknownIdReturnsTrue() {
        assertEquals(true, call("processDone", 99999.0));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessReadPartialReturnsString() throws InterruptedException {
        double id = startProcess("echo hello");
        Thread.sleep(200);
        assertInstanceOf(String.class, call("processReadPartial", id));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessReadPartialReadsOutput() throws InterruptedException {
        double id = startProcess("echo hello");
        Thread.sleep(200);
        String output = (String) call("processReadPartial", id);
        assertTrue(output.contains("hello"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testProcessReadPartialNonBlocking() {
        double id = startProcess("sleep 5");
        String output = (String) call("processReadPartial", id);
        assertEquals("", output);
        call("processKill", id);
    }

    @Test
    void testProcessReadPartialUnknownIdThrows() {
        assertThrows(RuntimeException.class, () -> call("processReadPartial", 99999.0));
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
        boolean found = result.getMembers().stream().anyMatch(e -> e.toString().contains(currentPid));
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

    @Test
    void testInstallCrashLogReturnsTrue() {
        Path log = tempDir.resolve("crash.log");
        assertEquals(true, call("installCrashLog", log.toString()));
    }

    @Test
    void testInstallCrashLogWritesToFile() throws IOException {
        Path log = tempDir.resolve("crash.log");
        call("installCrashLog", log.toString());
        System.err.println("boom");
        System.err.flush();
        String content = Files.readString(log);
        assertTrue(content.contains("boom"));
    }

    @Test
    void testInstallCrashLogWritesBannerLine() throws IOException {
        Path log = tempDir.resolve("crash.log");
        call("installCrashLog", log.toString());
        String content = Files.readString(log);
        assertTrue(content.contains("Mira crash log"));
    }

    @Test
    void testInstallCrashLogCreatesParentDirectories() {
        Path log = tempDir.resolve("nested/dir/crash.log");
        assertEquals(true, call("installCrashLog", log.toString()));
        assertTrue(Files.exists(log));
    }

    @Test
    void testInstallCrashLogTeesOriginalStream() {
        java.io.PrintStream before = System.err;
        Path log = tempDir.resolve("crash.log");
        call("installCrashLog", log.toString());
        call("uninstallCrashLog");
        assertSame(before, System.err);
    }

    @Test
    void testUninstallCrashLogWithNothingInstalledReturnsFalse() {
        java.io.PrintStream before = System.err;
        assertEquals(false, call("uninstallCrashLog"));
        assertSame(before, System.err);
    }

    @Test
    void testUninstallCrashLogTwiceReturnsFalseSecondTime() {
        Path log = tempDir.resolve("crash.log");
        call("installCrashLog", log.toString());
        assertEquals(true, call("uninstallCrashLog"));
        assertEquals(false, call("uninstallCrashLog"));
    }

    @Test
    void testInstallCrashLogTwiceOnlyLatestFileReceivesFurtherWrites() throws IOException {
        Path log1 = tempDir.resolve("crash1.log");
        Path log2 = tempDir.resolve("crash2.log");
        call("installCrashLog", log1.toString());
        call("installCrashLog", log2.toString());
        System.err.println("after-second-install");
        System.err.flush();

        assertFalse(Files.readString(log1).contains("after-second-install"));
        assertTrue(Files.readString(log2).contains("after-second-install"));
    }

    @Test
    void testInstallCrashLogTwiceDoesNotNestTeesOnOriginal() {
        java.io.PrintStream before = System.err;
        call("installCrashLog", tempDir.resolve("crash1.log").toString());
        call("installCrashLog", tempDir.resolve("crash2.log").toString());
        call("uninstallCrashLog");
        assertSame(before, System.err);
    }

    @Test
    void testInstallCrashLogWithUnwritablePathReturnsFalse() throws IOException {
        Path blockingFile = tempDir.resolve("not-a-directory");
        Files.writeString(blockingFile, "i am a file, not a directory");
        Path log = blockingFile.resolve("crash.log");

        assertEquals(false, call("installCrashLog", log.toString()));
    }
}
