package com.mira.testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class CoverageTrackerTest {

    @BeforeEach
    void setup() {
        CoverageTracker.reset();
    }

    @AfterEach
    void teardown() {
        CoverageTracker.setEnabled(false);
        CoverageTracker.reset();
    }

    private static List<Node> parse(String source) {
        return new Parser().parseTokens(new Tokenizer().tokenize(source, false));
    }

    @Test
    void recordLineNoOpsWhenDisabled() {
        CoverageTracker.setEnabled(false);
        CoverageTracker.recordLine("m", 5);
        assertEquals(Set.of(), CoverageTracker.getHitLines("m"));
    }

    @Test
    void recordLineTracksWhenEnabled() {
        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("m", 5);
        assertEquals(Set.of(5), CoverageTracker.getHitLines("m"));
    }

    @Test
    void getHitLinesDoesNotBleedAcrossModules() {
        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("a", 1);
        CoverageTracker.recordLine("b", 1);
        CoverageTracker.recordLine("b", 2);
        assertEquals(Set.of(1), CoverageTracker.getHitLines("a"));
        assertEquals(Set.of(1, 2), CoverageTracker.getHitLines("b"));
    }

    @Test
    void resetClearsHitLines() {
        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("m", 5);
        CoverageTracker.reset();
        assertEquals(Set.of(), CoverageTracker.getHitLines("m"));
    }

    @Test
    void moduleNameOfReturnsDeclaredName() {
        assertEquals("mymodule", CoverageTracker.moduleNameOf(parse("module mymodule;\nvar x : 1;\n")));
    }

    @Test
    void moduleNameOfFallsBackToScriptWhenNoModuleDecl() {
        assertEquals("<script>", CoverageTracker.moduleNameOf(parse("var x : 1;\n")));
    }

    @Test
    void printReportComputesPercentageAndUncoveredLines() {
        String source = """
                module m;

                fn add(a, b) {
                    return a + b;
                }

                fn unused(a) {
                    return a;
                }
                """;
        List<Node> ast = parse(source);

        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("m", 4); // add()'s return statement executed

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        CoverageTracker.printReport(out, List.of(new CoverageTracker.FileEntry("m", "m.mira", ast)));
        String report = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(report.contains("m.mira"), report);
        assertTrue(report.contains("1/5"), report);
        assertTrue(report.contains("Uncovered lines:"), report);
        assertTrue(report.contains("7-8"), report);
    }

    @Test
    void printReportHandlesFullyCoveredFileWithoutUncoveredSection() {
        String source = """
                module m;

                fn add(a, b) {
                    return a + b;
                }
                """;
        List<Node> ast = parse(source);

        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("m", 1);
        CoverageTracker.recordLine("m", 3);
        CoverageTracker.recordLine("m", 4);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        CoverageTracker.printReport(out, List.of(new CoverageTracker.FileEntry("m", "m.mira", ast)));
        String report = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(report.contains("100.0%") || report.contains("100,0%"), report);
        assertTrue(!report.contains("Uncovered lines:"), report);
    }

    @Test
    void printReportFindsLambdaBodyLinesEvenAsCallArguments() {
        String source = """
                module m;

                test("does something", fn() {
                    assert(true);
                });
                """;
        List<Node> ast = parse(source);

        CoverageTracker.setEnabled(true);
        CoverageTracker.recordLine("m", 4);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        CoverageTracker.printReport(out, List.of(new CoverageTracker.FileEntry("m", "m.mira", ast)));
        String report = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(report.contains("1/3"), report);
    }
}
