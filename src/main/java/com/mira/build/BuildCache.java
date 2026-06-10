package com.mira.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildCache {

    private static final String CACHE_FILENAME = ".mira-build-cache";

    private record CacheEntry(long lastModifiedMs, List<String> imports) {

    }

    private final Map<String, CacheEntry> entries;

    private BuildCache(Map<String, CacheEntry> entries) {
        this.entries = entries;
    }

    public static BuildCache load(Path outputDir) {
        Path cacheFile = outputDir.resolve(CACHE_FILENAME);
        if (!Files.exists(cacheFile)) {
            return new BuildCache(new HashMap<>());
        }
        try {
            Map<String, CacheEntry> entries = new HashMap<>();
            for (String line : Files.readAllLines(cacheFile)) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 2) {
                    continue;
                }
                String path = parts[0];
                long ts = Long.parseLong(parts[1]);
                List<String> imports = parts.length > 2
                        ? new ArrayList<>(Arrays.asList(parts).subList(2, parts.length))
                        : List.of();
                entries.put(path, new CacheEntry(ts, imports));
            }
            return new BuildCache(entries);
        } catch (IOException | NumberFormatException e) {
            return new BuildCache(new HashMap<>());
        }
    }

    public boolean isUpToDate(Path entryFile) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.push(entryFile.toAbsolutePath().normalize().toString());
        while (!queue.isEmpty()) {
            String path = queue.poll();
            if (!visited.add(path)) {
                continue;
            }
            CacheEntry entry = entries.get(path);
            if (entry == null) {
                return false;
            }
            long actual;
            try {
                actual = Files.getLastModifiedTime(Path.of(path)).toMillis();
            } catch (IOException e) {
                return false;
            }
            if (actual != entry.lastModifiedMs()) {
                return false;
            }
            queue.addAll(entry.imports());
        }
        return !visited.isEmpty();
    }

    public void update(Map<Path, List<Path>> depGraph) {
        entries.clear();
        for (Map.Entry<Path, List<Path>> e : depGraph.entrySet()) {
            Path file = e.getKey().toAbsolutePath().normalize();
            long ts;
            try {
                ts = Files.getLastModifiedTime(file).toMillis();
            } catch (IOException ex) {
                continue;
            }
            List<String> imports = e.getValue().stream()
                    .map(p -> p.toAbsolutePath().normalize().toString())
                    .toList();
            entries.put(file.toString(), new CacheEntry(ts, imports));
        }
    }

    public void save(Path outputDir) {
        Path cacheFile = outputDir.resolve(CACHE_FILENAME);
        StringBuilder sb = new StringBuilder("# mira build cache\n");
        for (Map.Entry<String, CacheEntry> e : entries.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue().lastModifiedMs());
            for (String imp : e.getValue().imports()) {
                sb.append('\t').append(imp);
            }
            sb.append('\n');
        }
        try {
            Files.createDirectories(outputDir);
            Files.writeString(cacheFile, sb.toString());
        } catch (IOException ignored) {
        }
    }
}
