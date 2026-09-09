package com.mira.build.dependency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

import com.mira.build.BuildException;

/**
 * A local, Maven-`.m2`-style install cache under
 * {@code ~/.mira/packages/local}: "mira install" copies the current project
 * into {@code local/<name>/<version>/}, keyed by its own [project] name and
 * version, so other local projects can depend on it by coordinates alone
 * ({@code { version = "1.2.3" }}) instead of a relative path.
 */
public final class LocalRegistry {

    private LocalRegistry() {
    }

    public static Path root() {
        return DependencyCache.root().resolve("local");
    }

    public static Path installDir(String name, String version) {
        return root().resolve(name).resolve(version);
    }

    /**
     * Copies projectRoot into installDir(name, version), replacing any prior
     * install of the same name+version. Skips the build output directory and
     * .git/mira.lock, none of which a consumer needs.
     */
    public static Path install(String name, String version, Path projectRoot, Path outputDir) {
        Path dest = installDir(name, version);
        try {
            if (Files.exists(dest)) {
                deleteRecursively(dest);
            }
            Files.createDirectories(dest);
            copyProject(projectRoot, dest, outputDir);
        } catch (IOException e) {
            throw new BuildException(
                    "Failed to install '" + name + "' " + version + " to " + dest + ": " + e.getMessage());
        }
        return dest;
    }

    private static void copyProject(Path src, Path dest, Path outputDir) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(src) && isExcludedDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isExcludedFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            private boolean isExcludedDir(Path dir) {
                return dir.equals(outputDir) || dir.getFileName().toString().equals(".git");
            }

            private boolean isExcludedFile(Path file) {
                return file.getFileName().toString().equals("mira.lock");
            }
        });
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
