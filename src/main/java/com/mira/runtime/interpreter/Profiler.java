package com.mira.runtime.interpreter;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class Profiler {

    private static final class Stats {

        long calls;
        long totalNanos;
        long selfNanos;
    }

    private static final class LineStats {

        long hits;
        long totalNanos;
        String function;
        String module;
    }

    public boolean enabled = false;

    private final Map<String, Stats> functionStats = new LinkedHashMap<>();
    private final Deque<long[]> callTimingStack = new ArrayDeque<>();

    private final Map<Integer, LineStats> lineStats = new LinkedHashMap<>();
    private int lastLine = -1;
    private long lastLineStartNanos;
    private String lastFunction;
    private String lastModule;

    public void start() {
        callTimingStack.push(new long[]{System.nanoTime(), 0L});
    }

    public void stop(String name) {
        long[] frame = callTimingStack.pop();
        long total = System.nanoTime() - frame[0];
        long self = total - frame[1];

        Stats stats = functionStats.computeIfAbsent(name, n -> new Stats());
        stats.calls++;
        stats.totalNanos += total;
        stats.selfNanos += self;

        long[] parent = callTimingStack.peek();
        if (parent != null) {
            parent[1] += total;
        }
    }

    public void recordCacheHit(String name) {
        Stats stats = functionStats.computeIfAbsent(name, n -> new Stats());
        stats.calls++;
    }

    public long getCalls(String name) {
        Stats s = functionStats.get(name);
        return s == null ? 0 : s.calls;
    }

    public long getTotalNanos(String name) {
        Stats s = functionStats.get(name);
        return s == null ? 0 : s.totalNanos;
    }

    public long getSelfNanos(String name) {
        Stats s = functionStats.get(name);
        return s == null ? 0 : s.selfNanos;
    }

    public long getLineHits(int line) {
        LineStats s = lineStats.get(line);
        return s == null ? 0 : s.hits;
    }

    public long getLineTotalNanos(int line) {
        LineStats s = lineStats.get(line);
        return s == null ? 0 : s.totalNanos;
    }

    public String getLineFunction(int line) {
        LineStats s = lineStats.get(line);
        return s == null ? null : s.function;
    }

    public String getLineModule(int line) {
        LineStats s = lineStats.get(line);
        return s == null ? null : s.module;
    }

    public long getTotalLineNanos() {
        return lineStats.values().stream().mapToLong(s -> s.totalNanos).sum();
    }

    public void onLine(int line, String function, String module) {
        long now = System.nanoTime();
        if (lastLine >= 0) {
            LineStats stats = lineStats.computeIfAbsent(lastLine, l -> new LineStats());
            if (stats.function == null) {
                stats.function = lastFunction;
                stats.module = lastModule;
            }
            stats.hits++;
            stats.totalNanos += now - lastLineStartNanos;
        }
        lastLine = line;
        lastLineStartNanos = now;
        lastFunction = function;
        lastModule = module;
    }

    public void finishLineTracking() {
        if (lastLine >= 0) {
            LineStats stats = lineStats.computeIfAbsent(lastLine, l -> new LineStats());
            if (stats.function == null) {
                stats.function = lastFunction;
                stats.module = lastModule;
            }
            stats.hits++;
            stats.totalNanos += System.nanoTime() - lastLineStartNanos;
            lastLine = -1;
        }
    }

    public void printReport(PrintStream out) {
        out.println();
        out.println("Profile Report");
        out.println("--------------");
        out.println();
        out.printf("%-30s %10s %12s %12s %14s%n", "Function", "Calls", "Total(ms)", "Self(ms)", "Avg(us/call)");
        functionStats.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Stats> e) -> e.getValue().selfNanos).reversed())
                .forEach(e -> {
                    Stats s = e.getValue();
                    double totalMs = s.totalNanos / 1_000_000.0;
                    double selfMs = s.selfNanos / 1_000_000.0;
                    double avgUs = s.calls == 0 ? 0 : (s.totalNanos / 1000.0) / s.calls;
                    out.printf("%-30s %10d %12.3f %12.3f %14.3f%n", e.getKey(), s.calls, totalMs, selfMs, avgUs);
                });

        out.println();
        out.printf("%-8s %-25s %-20s %10s %12s%n", "Line", "Function", "Module", "Hits", "Total(ms)");
        lineStats.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Integer, LineStats> e) -> e.getValue().totalNanos).reversed())
                .forEach(e -> {
                    LineStats s = e.getValue();
                    double totalMs = s.totalNanos / 1_000_000.0;
                    out.printf("%-8d %-25s %-20s %10d %12.3f%n", e.getKey(), s.function, s.module, s.hits, totalMs);
                });
    }
}
