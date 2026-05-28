package com.mira.testing;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mira.Flags;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.NullValue;

public class TestRunner {

    public record TestResult(String name, boolean passed, String error) {

    }

    private static final List<TestResult> results = Collections.synchronizedList(new ArrayList<>());

    public static void register(String name, Callable fn, Interpreter interpreter) {
        try {
            fn.call(interpreter, List.of());
            results.add(new TestResult(name, true, null));
            System.out.println("  PASS " + name);
        } catch (ThrowSignal ts) {
            String msg = ts.getValue() != null ? String.valueOf(ts.getValue()) : ts.getExceptionType();
            results.add(new TestResult(name, false, msg));
            System.out.println("  FAIL " + name + " — " + msg);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            results.add(new TestResult(name, false, msg));
            System.out.println("  FAIL " + name + " — " + msg);
        }
    }

    public static void printSummary(PrintStream out) {
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = results.size() - passed;
        out.println("\n─── Test Summary ───");
        out.println("  Passed : " + passed);
        out.println("  Failed : " + failed);
        out.println("  Total  : " + results.size());
        if (failed > 0) {
            out.println("  Status : FAILED");
        } else {
            out.println("  Status : OK");
        }
    }

    public static boolean hasFailures() {
        return results.stream().anyMatch(r -> !r.passed());
    }

    public static void reset() {
        results.clear();
    }

    public static Object nullValue() {
        return NullValue.INSTANCE;
    }

    public static void runPrePass(List<Node> asts, String[] args) {
        List<Node> prePassNodes = asts.stream()
                .filter(n -> isDeclaration(n) || isTestCall(n))
                .toList();

        try {
            new Interpreter().run(prePassNodes, args, true);
        } catch (ReturnSignal ignored) {
        }

        printSummary(System.out);
        boolean failed = hasFailures();
        reset();

        if (failed) {
            System.exit(1);
        }

        Flags.testsDone = true;
    }

    private static boolean isDeclaration(Node n) {
        return n instanceof FuncDecl
                || n instanceof VarDecl
                || n instanceof EnumDecl
                || n instanceof ImportExpression
                || n instanceof ModuleDecl;
    }

    private static boolean isTestCall(Node n) {
        return n instanceof TestCall;
    }
}
