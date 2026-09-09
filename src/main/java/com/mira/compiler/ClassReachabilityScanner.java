package com.mira.compiler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the transitive closure of classes reachable from a set of seed
 * classes by scanning each class file's constant pool for CONSTANT_Class
 * entries. Used to tree-shake the compiler/IDE toolchain (parser, lexer, LSP,
 * DAP, REPL, build system, ASM, LSP4J) out of slim package executables, since
 * none of it is referenced from a compiled program's runtime path.
 *
 * The scan is constant-pool based rather than precise data-flow analysis, so it
 * over-approximates (a class can be marked reachable even if only dead code
 * references it) - that's safe for tree-shaking, since it only ever keeps
 * classes, never drops one that's actually needed.
 */
public final class ClassReachabilityScanner {

    private static final String[] TRACKED_PREFIXES = {"com/mira/", "org/objectweb/asm/", "org/eclipse/lsp4j/"};

    private ClassReachabilityScanner() {
    }

    public static boolean isTracked(String internalName) {
        for (String prefix : TRACKED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> reachableClasses(Map<String, byte[]> classUniverse, Set<String> seeds) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String seed : seeds) {
            if (classUniverse.containsKey(seed) && visited.add(seed)) {
                pending.add(seed);
            }
        }
        while (!pending.isEmpty()) {
            String name = pending.poll();
            byte[] bytes = classUniverse.get(name);
            if (bytes == null) {
                continue;
            }
            for (String ref : referencedClassNames(bytes)) {
                if (!isTracked(ref) || !classUniverse.containsKey(ref)) {
                    continue;
                }
                if (visited.add(ref)) {
                    pending.add(ref);
                }
            }
        }
        return visited;
    }

    /**
     * Returns the tracked-prefix class names referenced by a class file's constant
     * pool. Used to seed the reachability scan from classes outside the Mira
     * distribution itself (e.g. user-compiled @native libraries) that reference
     * Mira runtime classes such as ReflectiveBinder.
     */
    public static Set<String> trackedReferences(byte[] classBytes) {
        Set<String> result = new HashSet<>();
        for (String ref : referencedClassNames(classBytes)) {
            if (isTracked(ref)) {
                result.add(ref);
            }
        }
        return result;
    }

    private static List<String> referencedClassNames(byte[] classBytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();

            String[] utf8 = new String[count];
            int[] classNameIndex = new int[count];
            boolean[] isClass = new boolean[count];

            int i = 1;
            while (i < count) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 7 -> {
                        isClass[i] = true;
                        classNameIndex[i] = in.readUnsignedShort();
                    }
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 15 -> in.skipBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++;
                    }
                    default -> throw new IOException("Unknown constant pool tag: " + tag);
                }
                i++;
            }

            List<String> result = new ArrayList<>();
            for (int idx = 1; idx < count; idx++) {
                if (!isClass[idx]) {
                    continue;
                }
                String raw = utf8[classNameIndex[idx]];
                String normalized = normalizeClassName(raw);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan class file for references", e);
        }
    }

    private static String normalizeClassName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw;
        while (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return null;
        }
        if (s.charAt(0) == 'L' && s.endsWith(";")) {
            return s.substring(1, s.length() - 1);
        }
        if (s.length() == 1) {
            return null;
        }
        return s;
    }
}
