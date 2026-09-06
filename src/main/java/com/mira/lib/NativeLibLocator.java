package com.mira.lib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.mira.cli.Flags;

/**
 * Finds the jar behind an {@code import native "..." as alias;} without
 * loading anything from it - shared by {@code ImportResolver} (which goes on
 * to actually load the jar) and the static checker / LSP (which only ever
 * needs the path, to read a classloading-free manifest resource out of it;
 * see {@link NativeInterfaceManifest}). Deliberately skips the compiled-jar
 * {@code mira-native-libs.properties} fast path - that one requires
 * instantiating the {@code Lib} class itself, which is a runtime-only
 * concern this locator must stay safe without.
 */
public final class NativeLibLocator {

    private NativeLibLocator() {
    }

    /**
     * @param rawPath the literal path/filename from the import statement
     * @param importingFile the source file containing the import, for resolving a relative path (may be null)
     * @return the resolved jar path, or null if it can't be found
     */
    public static Path locate(String rawPath, Path importingFile) {
        String basename = Path.of(rawPath).getFileName().toString();

        try (InputStream bundled = NativeLibLocator.class.getClassLoader()
                .getResourceAsStream("mira-native/" + basename)) {
            if (bundled != null) {
                Path tempJar = Files.createTempFile("mira-native-", "-" + basename);
                tempJar.toFile().deleteOnExit();
                Files.copy(bundled, tempJar, StandardCopyOption.REPLACE_EXISTING);
                return tempJar;
            }
        } catch (IOException ignored) {
        }

        for (Path root : Flags.nativeRoots) {
            Path candidate = root.resolve(basename);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        Path currentFile = importingFile != null ? importingFile.toAbsolutePath() : Path.of("").toAbsolutePath();
        Path candidate = Paths.get(rawPath);
        Path parent = currentFile.getParent();
        Path jarPath = candidate.isAbsolute() || parent == null
                ? candidate.normalize()
                : parent.resolve(candidate).normalize();
        return Files.exists(jarPath) ? jarPath : null;
    }
}
