package com.mira.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The classloading-free contract between a {@link ReflectiveLib}'s own build
 * (which has the real target classes on its classpath, and so can safely
 * reflect them) and Mira's static checker / LSP (which must never load a native
 * jar's classes just to typecheck a script that imports it). A manifest is
 * plain text, one binding per line: {@code name=Param,Param->Return}. It lives
 * at {@link #RESOURCE_PATH} inside the native jar, generated once at build time
 * - see {@code com.mira.buildtools.DescribeReflectiveLib}.
 */
public final class NativeInterfaceManifest {

    public static final String RESOURCE_PATH = "META-INF/mira/interface.properties";

    public record Signature(List<String> paramTypes, String returnType) {

    }

    private record Cached(long mtime, Map<String, Signature> signatures) {

    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private NativeInterfaceManifest() {
    }

    /**
     * Reads a native jar's manifest, caching by (path, mtime) - shared by the
     * static checker and every LSP feature (hover, signature help, completion) that
     * wants a native lib's declared signatures without ever loading its actual Java
     * classes. Returns an empty map if the jar has no manifest, or can't be read.
     */
    public static Map<String, Signature> readFromJar(Path jarPath) {
        try {
            String key = jarPath.toAbsolutePath().toString();
            long mtime = Files.getLastModifiedTime(jarPath).toMillis();
            Cached cached = CACHE.get(key);
            if (cached != null && cached.mtime() == mtime) {
                return cached.signatures();
            }
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                JarEntry entry = jar.getJarEntry(RESOURCE_PATH);
                if (entry == null) {
                    return Map.of();
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    Map<String, Signature> signatures = read(in);
                    CACHE.put(key, new Cached(mtime, signatures));
                    return signatures;
                }
            }
        } catch (IOException e) {
            return Map.of();
        }
    }

    public static void write(Map<String, Signature> bindings, OutputStream out) throws IOException {
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Signature> entry : bindings.entrySet()) {
                Signature sig = entry.getValue();
                w.write(entry.getKey());
                w.write('=');
                w.write(String.join(",", sig.paramTypes()));
                w.write("->");
                w.write(sig.returnType());
                w.write('\n');
            }
        }
    }

    public static Map<String, Signature> read(InputStream in) throws IOException {
        Map<String, Signature> result = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                int arrow = line.indexOf("->", eq);
                if (eq < 0 || arrow < 0) {
                    continue;
                }
                String name = line.substring(0, eq);
                String paramsPart = line.substring(eq + 1, arrow).strip();
                String returnType = line.substring(arrow + 2).strip();
                List<String> params = paramsPart.isEmpty() ? List.of() : List.of(paramsPart.split(","));
                result.put(name, new Signature(params, returnType));
            }
        }
        return result;
    }
}
