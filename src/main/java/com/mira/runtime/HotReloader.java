package com.mira.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mira.Flags;
import com.mira.Main;
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
        Path dir = filePath.getParent();

        System.out.println("[watch] Starting " + fileName + "...\n");
        Thread runner = startRunner();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

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
                    Thread.sleep(80); //let editors finish writing
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    runner.interrupt();
                    return;
                }

                boolean changed = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changed = true;
                        continue;
                    }
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    if (pathEvent.context().getFileName().toString().equals(fileName)) {
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
                    runner = startRunner();
                }

                if (!key.reset()) {
                    System.err.println("[watch] Watch key invalidated, stopping.");
                    runner.interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[watch] Failed to start file watcher: " + e.getMessage());
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
