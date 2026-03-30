package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class ShellLibTest {

    static Shell shell = new Shell();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        shell.loadLib(environment);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteEchoLinux() {
        assertEquals("hello", call("execute", "echo hello"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testExecuteEchoWindows() {
        String result = (String) call("execute", "echo hello");
        assertTrue(result.contains("hello"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteReturnsString() {
        assertInstanceOf(String.class, call("execute", "echo test"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteMultilineOutput() {
        String result = (String) call("execute", "printf 'a\\nb\\nc'");
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteEmptyOutput() {
        String result = (String) call("execute", "true");
        assertEquals("", result);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteStripTrailingNewline() {
        String result = (String) call("execute", "echo hello");
        assertFalse(result.endsWith("\n"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteCapturesStderr() {
        String result = (String) call("execute", "echo error >&2");
        assertInstanceOf(String.class, result);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteCodeSuccessReturnsZero() {
        assertEquals(0.0, call("executeCode", "true"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteCodeFailureReturnsNonZero() {
        assertNotEquals(0.0, call("executeCode", "false"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testExecuteCodeSuccessWindows() {
        assertEquals(0.0, call("executeCode", "cd ."));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteCodeReturnsDouble() {
        assertInstanceOf(Double.class, call("executeCode", "true"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testExecuteCodeSpecificExitCode() {
        assertEquals(42.0, call("executeCode", "exit 42"));
    }

    @Test
    void testGetenvPathIsNotEmpty() {
        String path = (String) call("getenv", "PATH");
        assertFalse(path.isEmpty());
    }

    @Test
    void testGetenvMissingReturnsEmpty() {
        assertEquals("", call("getenv", "MIRA_NONEXISTENT_VAR_XYZ"));
    }

    @Test
    void testGetenvReturnsString() {
        assertInstanceOf(String.class, call("getenv", "PATH"));
    }

    @Test
    void testHasenvPathExists() {
        assertEquals(true, call("hasenv", "PATH"));
    }

    @Test
    void testHasenvMissingReturnsFalse() {
        assertEquals(false, call("hasenv", "MIRA_NONEXISTENT_VAR_XYZ"));
    }

    @Test
    void testHasenvReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("hasenv", "PATH"));
    }

    @Test
    void testOsNameReturnsString() {
        assertInstanceOf(String.class, call("osName"));
    }

    @Test
    void testOsNameIsNotEmpty() {
        assertFalse(((String) call("osName")).isEmpty());
    }

    @Test
    void testOsNameMatchesSystemProperty() {
        assertEquals(System.getProperty("os.name"), call("osName"));
    }

    @Test
    void testIsWindowsReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("isWindows"));
    }

    @Test
    void testIsLinuxReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("isLinux"));
    }

    @Test
    void testIsMacReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("isMac"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testIsWindowsTrueOnWindows() {
        assertEquals(true, call("isWindows"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testIsLinuxTrueOnLinux() {
        assertEquals(true, call("isLinux"));
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void testIsMacTrueOnMac() {
        assertEquals(true, call("isMac"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void testIsWindowsFalseOnLinux() {
        assertEquals(false, call("isWindows"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testIsLinuxFalseOnWindows() {
        assertEquals(false, call("isLinux"));
    }

    @Test
    void testCwdReturnsString() {
        assertInstanceOf(String.class, call("cwd"));
    }

    @Test
    void testCwdIsNotEmpty() {
        assertFalse(((String) call("cwd")).isEmpty());
    }

    @Test
    void testCwdMatchesSystemProperty() {
        assertEquals(System.getProperty("user.dir"), call("cwd"));
    }

    @Test
    void testUsernameReturnsString() {
        assertInstanceOf(String.class, call("username"));
    }

    @Test
    void testUsernameIsNotEmpty() {
        assertFalse(((String) call("username")).isEmpty());
    }

    @Test
    void testUsernameMatchesSystemProperty() {
        assertEquals(System.getProperty("user.name"), call("username"));
    }

    @Test
    void testHomedirReturnsString() {
        assertInstanceOf(String.class, call("homedir"));
    }

    @Test
    void testHomedirIsNotEmpty() {
        assertFalse(((String) call("homedir")).isEmpty());
    }

    @Test
    void testHomedirMatchesSystemProperty() {
        assertEquals(System.getProperty("user.home"), call("homedir"));
    }
}
