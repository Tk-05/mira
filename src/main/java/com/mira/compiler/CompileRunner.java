package com.mira.compiler;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import com.mira.cli.Flags;
import com.mira.cli.Main;
import com.mira.build.BuildCache;
import com.mira.compiler.Compiler.CompileResult;
import com.mira.compiler.support.CompiledRuntimeSupport;
import com.mira.parser.nodes.Node;
import com.mira.resolver.ModuleChecker;

public class CompileRunner {

    public void run(List<Node> ast) throws Exception {
        run(ast, null);
    }

    public void run(List<Node> ast, Map<String, Object> precomputedComptimeConsts) throws Exception {
        long compileStart = System.currentTimeMillis();
        CompileResult result = new Compiler().compile(ast, Flags.fileName, precomputedComptimeConsts);
        long compileMs = System.currentTimeMillis() - compileStart;

        Path outDir = Flags.outputDir != null
                ? Flags.outputDir
                : Flags.inputPath.get().getParent();

        if (!Flags.compileAndRun) {
            writeToDisk(result, outDir);
            printStats(result, outDir, compileMs);
            if (Flags.packageJar) {
                packageToJar(result, outDir);
            }
            BuildCache cache = BuildCache.load(outDir);
            cache.update(ModuleChecker.collectDependencyGraph(ast, Flags.inputPath.get()));
            cache.save(outDir);
        }

        if (Flags.dumpByteCode) {
            dumpBytecode(result);
        }

        if (Flags.compileAndRun) {
            executeInMemory(result);
        }
    }

    private void packageToJar(CompileResult result, Path outDir) throws Exception {
        String stem = Flags.fileName;
        if (stem.endsWith(".mira")) {
            stem = stem.substring(0, stem.length() - 5);
        }
        int sep = Math.max(stem.lastIndexOf('/'), stem.lastIndexOf('\\'));
        if (sep >= 0) {
            stem = stem.substring(sep + 1);
        }
        Path jarPath = outDir.resolve(stem + ".jar");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, result.className().replace('/', '.'));
        if (!result.nativeJars().isEmpty()) {
            attrs.putValue("Enable-Native-Access", "ALL-UNNAMED");
        }

        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        Path miraSource = Path.of(location.toURI());

        Set<String> written = new HashSet<>();
        Files.createDirectories(jarPath.getParent());

        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(jarPath.toFile())), manifest)) {

            written.add("META-INF/MANIFEST.MF");
            written.add("META-INF/");

            writeJarEntry(jos, result.className() + ".class", result.mainClass(), written);
            for (Map.Entry<String, byte[]> e : result.lambdaClasses().entrySet()) {
                writeJarEntry(jos, e.getKey() + ".class", e.getValue(), written);
            }

            Map<String, String> nativeLibClasses = new LinkedHashMap<>();
            List<byte[]> nativeClassBytes = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : result.nativeJars().entrySet()) {
                String basename = Path.of(e.getKey()).getFileName().toString();
                try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(e.getValue()))) {
                    String libClassName = null;
                    JarEntry entry;
                    while ((entry = jis.getNextJarEntry()) != null) {
                        String name = entry.getName();
                        if (name.equals("META-INF/MANIFEST.MF")
                                || (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")))) {
                            jis.closeEntry();
                            continue;
                        }
                        byte[] entryBytes = jis.readAllBytes();
                        if (name.equals("META-INF/services/com.mira.lib.Lib")) {
                            libClassName = new String(entryBytes, StandardCharsets.UTF_8).strip();
                        }
                        if (name.endsWith(".class")) {
                            nativeClassBytes.add(entryBytes);
                        }
                        writeJarEntry(jos, name, entryBytes, written);
                        jis.closeEntry();
                    }
                    if (libClassName != null) {
                        nativeLibClasses.put(basename, libClassName);
                    }
                }
            }
            if (!nativeLibClasses.isEmpty()) {
                StringBuilder props = new StringBuilder();
                for (Map.Entry<String, String> e : nativeLibClasses.entrySet()) {
                    props.append(e.getKey()).append('=').append(e.getValue()).append('\n');
                }
                writeJarEntry(jos, "mira-native-libs.properties",
                        props.toString().getBytes(StandardCharsets.UTF_8), written);
            }

            if (Flags.slimJar) {
                Map<String, byte[]> allEntries = miraSource.toString().endsWith(".jar")
                        ? collectJarEntries(miraSource)
                        : collectDirEntries(miraSource);
                Set<String> seeds = new HashSet<>(result.lambdaClasses().keySet());
                seeds.add(result.className());
                seeds.add(ClassEmitter.ENV_NAME);
                seeds.add(ClassEmitter.RT_NAME);
                seeds.add("com/mira/runtime/interpreter/ImportResolver");
                for (byte[] nativeClass : nativeClassBytes) {
                    seeds.addAll(ClassReachabilityScanner.trackedReferences(nativeClass));
                }
                mergeSlim(jos, allEntries, written, seeds);
            } else if (miraSource.toString().endsWith(".jar")) {
                mergeFromJar(jos, miraSource, written);
            } else {
                mergeFromDirectory(jos, miraSource, miraSource, written);
            }
        }

        System.out.println("  jar    : " + jarPath.toAbsolutePath());
        System.out.println("  run    : java -jar " + jarPath.getFileName());
    }

    private static Map<String, byte[]> collectJarEntries(Path sourceJar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(sourceJar.toFile())) {
            var jarEntries = jf.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"))) {
                    continue;
                }
                try (var is = jf.getInputStream(entry)) {
                    entries.put(name, is.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static Map<String, byte[]> collectDirEntries(Path root) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        collectDirEntries(root, root, entries);
        return entries;
    }

    private static void collectDirEntries(Path root, Path current, Map<String, byte[]> entries) throws IOException {
        try (var stream = Files.list(current)) {
            for (Path child : stream.toList()) {
                if (Files.isDirectory(child)) {
                    collectDirEntries(root, child, entries);
                } else {
                    String name = root.relativize(child).toString().replace('\\', '/');
                    entries.put(name, Files.readAllBytes(child));
                }
            }
        }
    }

    private static void mergeSlim(JarOutputStream jos, Map<String, byte[]> allEntries,
            Set<String> written, Set<String> seeds) throws IOException {
        Map<String, byte[]> classUniverse = new HashMap<>();
        for (Map.Entry<String, byte[]> e : allEntries.entrySet()) {
            String name = e.getKey();
            if (name.endsWith(".class")) {
                String internalName = name.substring(0, name.length() - ".class".length());
                if (ClassReachabilityScanner.isTracked(internalName)) {
                    classUniverse.put(internalName, e.getValue());
                }
            }
        }

        Set<String> reachable = ClassReachabilityScanner.reachableClasses(classUniverse, seeds);

        for (Map.Entry<String, byte[]> e : allEntries.entrySet()) {
            String name = e.getKey();
            if (name.endsWith(".class")) {
                String internalName = name.substring(0, name.length() - ".class".length());
                if (ClassReachabilityScanner.isTracked(internalName) && !reachable.contains(internalName)) {
                    continue;
                }
            } else if (ClassReachabilityScanner.isTracked(name)) {
                continue;
            }
            writeJarEntry(jos, name, e.getValue(), written);
        }
    }

    private static void writeJarEntry(JarOutputStream jos, String name,
            byte[] data, Set<String> written) throws IOException {
        if (!written.add(name)) {
            return;
        }
        jos.putNextEntry(new JarEntry(name));
        jos.write(data);
        jos.closeEntry();
    }

    private static void mergeFromJar(JarOutputStream jos, Path sourceJar,
            Set<String> written) throws IOException {
        try (JarFile jf = new JarFile(sourceJar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"))) {
                    continue;
                }
                if (!written.add(name)) {
                    continue;
                }
                jos.putNextEntry(new JarEntry(name));
                if (!entry.isDirectory()) {
                    try (var is = jf.getInputStream(entry)) {
                        jos.write(is.readAllBytes());
                    }
                }
                jos.closeEntry();
            }
        }
    }

    private static void mergeFromDirectory(JarOutputStream jos, Path root,
            Path current, Set<String> written) throws IOException {
        try (var stream = Files.list(current)) {
            for (Path child : stream.toList()) {
                if (Files.isDirectory(child)) {
                    mergeFromDirectory(jos, root, child, written);
                } else {
                    String name = root.relativize(child).toString().replace('\\', '/');
                    if (!written.add(name)) {
                        continue;
                    }
                    jos.putNextEntry(new JarEntry(name));
                    jos.write(Files.readAllBytes(child));
                    jos.closeEntry();
                }
            }
        }
    }

    private void executeInMemory(CompileResult result) throws Exception {
        Map<String, byte[]> all = new HashMap<>(result.lambdaClasses());
        all.put(result.className(), result.mainClass());
        CompiledClassLoader loader = new CompiledClassLoader(all);
        String dotName = result.className().replace('/', '.');
        Class<?> cls = loader.loadClass(dotName);
        Method mainMethod = cls.getMethod("main", String[].class);
        String[] programArgs = Flags.args != null ? Flags.args : new String[0];
        if (Flags.profile) {
            CompiledRuntimeSupport.setProfiler(new com.mira.runtime.interpreter.Profiler());
        }
        try {
            Thread.currentThread().setContextClassLoader(loader);
            mainMethod.invoke(null, (Object) programArgs);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
        if (Flags.profile) {
            com.mira.runtime.interpreter.Profiler profiler = CompiledRuntimeSupport.getProfiler();
            profiler.finishLineTracking();
            profiler.printReport(System.out);
        }
    }

    private void writeToDisk(CompileResult result, Path outDir) throws IOException {
        writeClass(outDir, result.className(), result.mainClass());
        for (Map.Entry<String, byte[]> entry : result.lambdaClasses().entrySet()) {
            writeClass(outDir, entry.getKey(), entry.getValue());
        }
    }

    private void printStats(CompileResult result, Path outDir, long compileMs) {
        int totalClasses = 1 + result.lambdaClasses().size();
        int totalBytes = result.mainClass().length
                + result.lambdaClasses().values().stream().mapToInt(b -> b.length).sum();
        System.out.println("  source : " + Flags.fileName);
        System.out.println("  output : " + outDir.toAbsolutePath());
        System.out.println("  classes: " + totalClasses);
        System.out.println("  size   : " + totalBytes + " bytes");
        System.out.println("  time   : " + compileMs + " ms");
        System.out.println("  run    : java -cp mira-RELEASE.jar" + File.pathSeparator
                + outDir.toAbsolutePath() + " " + result.className().replace('/', '.'));
    }

    private void dumpBytecode(CompileResult result) {
        PrintWriter pw = new PrintWriter(System.out, true);
        dumpClass(result.className(), result.mainClass(), pw);
        for (Map.Entry<String, byte[]> entry : result.lambdaClasses().entrySet()) {
            dumpClass(entry.getKey(), entry.getValue(), pw);
        }
    }

    private static void dumpClass(String internalName, byte[] bytes, PrintWriter pw) {
        pw.println("=== " + internalName + " ===");
        new ClassReader(bytes).accept(new TraceClassVisitor(pw), ClassReader.SKIP_DEBUG);
        pw.println();
    }

    private static void writeClass(Path outDir, String internalName, byte[] bytes) throws IOException {
        Path file = outDir.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
