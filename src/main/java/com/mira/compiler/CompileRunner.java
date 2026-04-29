package com.mira.compiler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.Flags;
import com.mira.compiler.MiraCompiler.CompileResult;
import com.mira.parser.nodes.Node;

public class CompileRunner {

    public void run(List<Node> ast) throws Exception {
        long compileStart = System.currentTimeMillis();
        CompileResult result = new MiraCompiler().compile(ast, Flags.fileName);
        long compileMs = System.currentTimeMillis() - compileStart;

        if (Flags.outputDir != null) {
            writeToDisk(result, Flags.outputDir);
            printStats(result, Flags.outputDir, compileMs);
        } else if (!Flags.compileAndRun) {
            Path outDir = Flags.inputPath.get().getParent();
            writeToDisk(result, outDir);
            printStats(result, outDir, compileMs);
        }

        if (Flags.compileAndRun) {
            executeInMemory(result);
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
        try {
            mainMethod.invoke(null, (Object) programArgs);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
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

    private static void writeClass(Path outDir, String internalName, byte[] bytes) throws IOException {
        Path file = outDir.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
