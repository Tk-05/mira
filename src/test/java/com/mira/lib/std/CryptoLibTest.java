package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class CryptoLibTest {

    static Crypto lib = new Crypto();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testMd5() {
        NativeFunction fn = (NativeFunction) environment.get("md5");
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", fn.call(interpreter, List.of("")));
        assertEquals("5d41402abc4b2a76b9719d911017c592", fn.call(interpreter, List.of("hello")));
    }

    @Test
    void testSha1() {
        NativeFunction fn = (NativeFunction) environment.get("sha1");
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", fn.call(interpreter, List.of("hello")));
    }

    @Test
    void testSha256() {
        NativeFunction fn = (NativeFunction) environment.get("sha256");
        String result = (String) fn.call(interpreter, List.of("hello"));
        assertEquals(64, result.length());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
    }

    @Test
    void testSha512() {
        NativeFunction fn = (NativeFunction) environment.get("sha512");
        String result = (String) fn.call(interpreter, List.of("hello"));
        assertEquals(128, result.length());
    }

    @Test
    void testHmacSha256() {
        NativeFunction fn = (NativeFunction) environment.get("hmacSha256");
        String result = (String) fn.call(interpreter, List.of("key", "message"));
        assertEquals(64, result.length());
    }

    @Test
    void testUuid() {
        NativeFunction fn = (NativeFunction) environment.get("uuid");
        String result = (String) fn.call(interpreter, List.of());
        assertNotNull(result);
        assertEquals(36, result.length());
        assertTrue(result.contains("-"));
    }

    @Test
    void testUuidNoDashes() {
        NativeFunction fn = (NativeFunction) environment.get("uuidNoDashes");
        String result = (String) fn.call(interpreter, List.of());
        assertEquals(32, result.length());
        assertTrue(!result.contains("-"));
    }
}
