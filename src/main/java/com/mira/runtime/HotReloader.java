package com.mira.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.Flags;
import com.mira.Main;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.warning.WarningCollector;

public class HotReloader {

    private final Path filePath;
    private final String fileName;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public HotReloader(Path filePath) {
        this.filePath = filePath;
        this.fileName = filePath.getFileName().toString();
    }

    public void run() {
        System.out.println("[watch] Starting " + fileName + "...\n");
        Thread runner = startRunner();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keyToDir = new HashMap<>();
            Set<Path> watchedFiles = collectWatchedFiles();
            registerDirs(watchService, watchedFiles, keyToDir);

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    runner.interrupt();
                    return;
                }

                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    runner.interrupt();
                    return;
                }

                boolean changed = false;
                Path dir = keyToDir.get(key);
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changed = true;
                        continue;
                    }
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = dir != null ? dir.resolve(pathEvent.context()) : pathEvent.context();
                    if (watchedFiles.contains(changedFile.normalize())) {
                        changed = true;
                    }
                }

                if (changed) {
                    stopping.set(true);
                    if (runner.isAlive()) {
                        runner.interrupt();
                        try {
                            runner.join(2000);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            stopping.set(false);
                            return;
                        }
                    }
                    stopping.set(false);

                    System.out.println("\n[watch] Change detected — restarting " + fileName + "...\n");
                    WarningCollector.clear();
                    ImportResolver.reset();

                    for (WatchKey k : keyToDir.keySet()) {
                        k.cancel();
                    }
                    keyToDir.clear();
                    watchedFiles = collectWatchedFiles();
                    registerDirs(watchService, watchedFiles, keyToDir);

                    runner = startRunner();
                }

                if (!key.reset()) {
                    keyToDir.remove(key);
                    if (keyToDir.isEmpty()) {
                        System.err.println("[watch] All watched directories gone, stopping.");
                        runner.interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[watch] Failed to start file watcher: " + e.getMessage());
        }
    }

    private Set<Path> collectWatchedFiles() {
        Set<Path> files = new HashSet<>();
        files.add(filePath.normalize());
        try {
            String src = Files.readString(filePath);
            List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            for (Node n : ast) {
                if (!(n instanceof Expression.ImportExpression imp)) {
                    continue;
                }
                if (imp.getKind() != Expression.ImportExpression.ImportKind.MODULE) {
                    continue;
                }
                String raw = imp.getModule().replace("\"", "");
                if (!raw.endsWith(".mira")) {
                    raw += ".mira";
                }
                Path p = filePath.getParent().resolve(raw).normalize();
                if (Files.exists(p)) {
                    files.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        return files;
    }

    private void registerDirs(WatchService watchService, Set<Path> files, Map<WatchKey, Path> keyToDir) throws IOException {
        Set<Path> dirs = new HashSet<>();
        for (Path f : files) {
            Path parent = f.getParent();
            if (parent != null) {
                dirs.add(parent);
            }
        }
        for (Path dir : dirs) {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            keyToDir.put(key, dir);
        }
    }

    private Thread startRunner() {
        Thread t = new Thread(() -> {
            Flags.inputPath.set(filePath);
            Main.runFile(stopping);
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
