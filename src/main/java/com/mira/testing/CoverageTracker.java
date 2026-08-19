package com.mira.testing;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.While;

/**
 * Tracks which source lines actually executed during a test run, for
 * {@code --coverage}. Static/global by design - like {@link TestRunner}'s own
 * result list, this needs to survive across the throwaway {@code Interpreter}
 * instances {@link TestRunner#runPrePassCollecting} creates per test file, so
 * per-interpreter state (e.g. the existing {@code Profiler} used by
 * {@code --profile}) isn't usable here. Keyed by Mira module name (the identity
 * {@code Interpreter} itself already tracks per line) rather than file path, so
 * multi-file runs never collide.
 */
public final class CoverageTracker {

    private static boolean enabled = false;
    private static final Map<String, Set<Integer>> hitLines = new ConcurrentHashMap<>();

    private CoverageTracker() {
    }

    public static void setEnabled(boolean e) {
        enabled = e;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void recordLine(String module, int line) {
        if (!enabled) {
            return;
        }
        hitLines.computeIfAbsent(module, k -> ConcurrentHashMap.newKeySet()).add(line);
    }

    public static Set<Integer> getHitLines(String module) {
        return hitLines.getOrDefault(module, Set.of());
    }

    public static void reset() {
        hitLines.clear();
    }

    /**
     * One file to include in a coverage report: its module identity, a display
     * label, and its parsed AST.
     */
    public record FileEntry(String moduleName, String label, List<Node> ast) {

    }

    /**
     * A file's module identity, derived exactly the way
     * {@code Interpreter.loadGlobalContext} derives it (the first statement's
     * {@code module <name>;} declaration, or {@code "<script>"} if absent) - so
     * this always matches the key {@code recordLine} received at runtime for
     * that same file.
     */
    public static String moduleNameOf(List<Node> ast) {
        return !ast.isEmpty() && ast.get(0) instanceof ModuleDecl moduleDecl
                ? moduleDecl.getModuleName()
                : "<script>";
    }

    public static void printReport(PrintStream out, List<FileEntry> files) {
        record FileCoverage(String label, Set<Integer> executable, Set<Integer> covered) {

        }

        List<FileCoverage> coverages = new ArrayList<>();
        int totalExecutable = 0;
        int totalCovered = 0;
        for (FileEntry f : files) {
            Set<Integer> executable = executableLines(f.ast());
            Set<Integer> hit = getHitLines(f.moduleName());
            Set<Integer> covered = new TreeSet<>(executable);
            covered.retainAll(hit);
            coverages.add(new FileCoverage(f.label(), executable, covered));
            totalExecutable += executable.size();
            totalCovered += covered.size();
        }

        out.println();
        out.println("=== MIRA COVERAGE ===");
        out.println("Files: " + coverages.size());
        for (FileCoverage fc : coverages) {
            double pct = fc.executable().isEmpty() ? 100.0 : (100.0 * fc.covered().size() / fc.executable().size());
            out.printf("  %-20s %d/%d lines (%.1f%%)%n",
                    fc.label(), fc.covered().size(), fc.executable().size(), pct);
        }
        out.println();
        double totalPct = totalExecutable == 0 ? 100.0 : (100.0 * totalCovered / totalExecutable);
        out.printf("Totals: %d/%d lines covered (%.1f%%)%n", totalCovered, totalExecutable, totalPct);

        List<FileCoverage> withGaps = coverages.stream()
                .filter(fc -> fc.covered().size() < fc.executable().size())
                .toList();
        if (!withGaps.isEmpty()) {
            out.println();
            out.println("Uncovered lines:");
            for (FileCoverage fc : withGaps) {
                Set<Integer> uncovered = new TreeSet<>(fc.executable());
                uncovered.removeAll(fc.covered());
                out.println("  " + fc.label() + ": " + compressRanges(uncovered));
            }
        }
        out.println("=== END COVERAGE ===");
        out.println();
    }

    /**
     * Distinct lines that could ever be reported to {@link #recordLine},
     * mirroring {@code Interpreter.runBody}'s own traversal: every node sitting
     * directly in a body list counts (whether a {@code Statement} or a bare
     * expression-statement like a call - both get their own
     * {@code notifyDebugger} call at runtime), and container nodes
     * (if/loop/switch/try/lambda/...) recurse into their nested body lists the
     * same way. Plain expression operands (call arguments, binary operator
     * sides, etc.) are deliberately NOT counted individually - the interpreter
     * never tracks them as separate "lines", only the
     * statement/expression-statement that contains them.
     */
    private static Set<Integer> executableLines(List<Node> ast) {
        Set<Integer> lines = new TreeSet<>();
        collectBodyLines(ast, lines);
        return lines;
    }

    private static void collectBodyLines(List<Node> body, Set<Integer> out) {
        for (Node n : body) {
            collectFromBodyNode(n, out);
        }
    }

    private static void collectFromBodyNode(Node n, Set<Integer> out) {
        int line = switch (n) {
            case Statement s ->
                s.line;
            case Expression e ->
                e.line;
            default ->
                0;
        };
        if (line > 0) {
            out.add(line);
        }
        switch (n) {
            case FuncDecl f ->
                collectBodyLines(f.getBody(), out);
            case If s -> {
                collectBodyLines(s.getThenBody(), out);
                if (s.getElseBody() != null) {
                    collectBodyLines(s.getElseBody(), out);
                }
            }
            case Loop s ->
                collectBodyLines(s.getBody(), out);
            case While s ->
                collectBodyLines(s.getBody(), out);
            case Block s ->
                collectBodyLines(s.getBody(), out);
            case Switch s -> {
                for (Switch.SwitchCase sc : s.getCases()) {
                    collectBodyLines(sc.getBody(), out);
                }
                if (s.getDefaultBody() != null) {
                    collectBodyLines(s.getDefaultBody(), out);
                }
            }
            case TryCatch s -> {
                collectBodyLines(s.getTryBody(), out);
                for (TryCatch.CatchClause cc : s.getCatchClauses()) {
                    collectBodyLines(cc.getBody(), out);
                }
                if (s.getFinallyBody() != null) {
                    collectBodyLines(s.getFinallyBody(), out);
                }
            }
            case Lock s ->
                collectBodyLines(s.getBody(), out);
            case ComptimeBlock s ->
                collectBodyLines(s.getBody(), out);
            case LambdaExpression e ->
                collectBodyLines(e.getBody(), out);
            case ExecBlock e ->
                collectBodyLines(e.getBody(), out);
            default ->
                findNestedLambdas(n, out);
        }
    }

    /**
     * Hunts for {@code LambdaExpression}s buried anywhere inside an operand
     * tree (e.g. a callback passed as a call argument, like
     * {@code test("x", fn() {...})}'s second argument). The lambda's own line
     * isn't separately tracked, but its body is a fresh set of
     * statement-position lines that get tracked once it's invoked.
     */
    private static void findNestedLambdas(Node n, Set<Integer> out) {
        Deque<Node> queue = new ArrayDeque<>();
        AstWalker.children(n, queue);
        while (!queue.isEmpty()) {
            Node child = queue.poll();
            if (child == null) {
                continue;
            }
            if (child instanceof LambdaExpression lambda) {
                collectBodyLines(lambda.getBody(), out);
            } else {
                AstWalker.children(child, queue);
            }
        }
    }

    private static String compressRanges(Set<Integer> sortedLines) {
        List<String> parts = new ArrayList<>();
        Integer rangeStart = null;
        Integer rangeEnd = null;
        for (int line : sortedLines) {
            if (rangeStart == null) {
                rangeStart = line;
                rangeEnd = line;
            } else if (line == rangeEnd + 1) {
                rangeEnd = line;
            } else {
                parts.add(formatRange(rangeStart, rangeEnd));
                rangeStart = line;
                rangeEnd = line;
            }
        }
        if (rangeStart != null) {
            parts.add(formatRange(rangeStart, rangeEnd));
        }
        return String.join(", ", parts);
    }

    private static String formatRange(int start, int end) {
        return start == end ? String.valueOf(start) : start + "-" + end;
    }
}
