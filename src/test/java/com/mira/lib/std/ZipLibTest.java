package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.BytesValue;

public class ZipLibTest {

    static Zip lib = new Zip();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testGzipRoundTrip() {
        NativeFunction compress = (NativeFunction) environment.get("gzipCompress");
        NativeFunction decompress = (NativeFunction) environment.get("gzipDecompress");
        byte[] original = "Hello, world!".getBytes();
        BytesValue input = new BytesValue(original);
        BytesValue compressed = (BytesValue) compress.call(interpreter, List.of(input));
        assertNotNull(compressed);
        assertTrue(compressed.getData().length > 0);
        BytesValue result = (BytesValue) decompress.call(interpreter, List.of(compressed));
        assertArrayEquals(original, result.getData());
    }

    @Test
    void testDeflateRoundTrip() {
        NativeFunction deflate = (NativeFunction) environment.get("deflate");
        NativeFunction inflate = (NativeFunction) environment.get("inflate");
        byte[] original = "Test deflate/inflate".getBytes();
        BytesValue input = new BytesValue(original);
        BytesValue compressed = (BytesValue) deflate.call(interpreter, List.of(input));
        assertNotNull(compressed);
        BytesValue result = (BytesValue) inflate.call(interpreter, List.of(compressed));
        assertArrayEquals(original, result.getData());
    }
}
