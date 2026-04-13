package com.mira.lib.std;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class IOLibTest {
    static com.mira.lib.std.IO io = new com.mira.lib.std.IO();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setup() {
        io.loadLib(environment);
    }

    @Test
    void testReadFile() {
        if (environment.get("readFile") instanceof NativeFunction activeFunction) {
            String readFile = (String) activeFunction.call(interpreter, List.of("src/main/resources/demo/Debug.mira"));
            assertNotNull(readFile);
        }
    }

    @Test
    void writeFileCreatesFile() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        NativeFunction writeFile = (NativeFunction) environment.get("writeFile");
        writeFile.call(interpreter, List.of(file.toString(), "Hello World"));
        assertTrue(Files.exists(file));
        assertEquals("Hello World", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeFileCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("sub/dir/hello.txt");
        NativeFunction writeFile = (NativeFunction) environment.get("writeFile");
        writeFile.call(interpreter, List.of(file.toString(), "nested"));
        assertTrue(Files.exists(file));
        assertEquals("nested", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeFileOverwritesExistingContent() throws IOException {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content", StandardCharsets.UTF_8);
        NativeFunction writeFile = (NativeFunction) environment.get("writeFile");
        writeFile.call(interpreter, List.of(file.toString(), "new content"));
        assertEquals("new content", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void writeFileThrowsOnInvalidPath() {
        NativeFunction writeFile = (NativeFunction) environment.get("writeFile");
        assertThrows(RuntimeException.class, () ->
                writeFile.call(interpreter, List.of("\0invalid", "content")));
    }
}
