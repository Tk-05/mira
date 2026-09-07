package com.mira.buildtools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mira.lib.NativeInterfaceManifest;
import com.mira.lib.NativeInterfaceManifest.Signature;
import com.mira.lib.ReflectiveLib;
import com.mira.runtime.functions.NativeInterop;
import com.mira.runtime.functions.NativeInterop.ResolvedBinding;

/**
 * Build-time only: reflects a {@link ReflectiveLib} implementation's declared
 * bindings and writes them as a {@link NativeInterfaceManifest} into the
 * library's own build output. Run this from the library's own build (where
 * its native dependencies are on the classpath) - never from a consumer's
 * project, and never as part of loading a native jar at Mira runtime.
 *
 * <p>
 * Usage: {@code java -cp <lib classes + deps + mira> \
 * com.mira.buildtools.DescribeReflectiveLib <ReflectiveLib class name> <output file>}
 */
public final class DescribeReflectiveLib {

    private DescribeReflectiveLib() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: DescribeReflectiveLib <ReflectiveLib class name> <output file>");
            System.exit(1);
            return;
        }
        Class<?> cls = Class.forName(args[0]);
        Object instance = cls.getDeclaredConstructor().newInstance();
        if (!(instance instanceof ReflectiveLib lib)) {
            throw new IllegalArgumentException(args[0] + " does not implement com.mira.lib.ReflectiveLib");
        }
        Path outPath = Path.of(args[1]);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        write(lib, outPath);
    }

    private static void write(ReflectiveLib lib, Path outPath) throws IOException {
        Map<String, Signature> manifest = new LinkedHashMap<>();
        for (ResolvedBinding binding : NativeInterop.describe(lib)) {
            manifest.put(binding.name(), new Signature(
                    binding.paramTypes().stream().map(t -> t.miraTypeName()).toList(),
                    binding.returnType().miraTypeName()));
        }
        try (var out = new FileOutputStream(outPath.toFile())) {
            NativeInterfaceManifest.write(manifest, out);
        }
    }
}
