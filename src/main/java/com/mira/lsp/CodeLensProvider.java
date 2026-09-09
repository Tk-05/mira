package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.TestCall;

/**
 * Two kinds of lens: "N references" above each plain function declaration
 * (clickable - opens the standard references peek view via the client-side
 * {@code mira.showReferences} command, not {@code editor.action.showReferences}
 * directly - that built-in requires real vscode.Uri/Position/Location instances
 * and rejects the plain JSON a Command's arguments actually carry over LSP, so
 * the client wraps it and converts first), and "Run Tests" above each
 * {@code test(...)} call.
 *
 * {@code test(...)} calls always run the *whole file's* test suite when clicked
 * - Mira's own {@code --test} flag / TestRunner has no per-test filter to run
 * just one, so the lens can't promise more than the CLI actually supports.
 */
public class CodeLensProvider {

    public static List<CodeLens> provide(List<Node> ast, String content, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        List<CodeLens> lenses = new ArrayList<>();
        collectReferenceLenses(ast, ast, content, uri, docPath, workspaceIndex, workspaceRoot, openDocumentsByUri,
                lenses);
        collectTestLenses(ast, uri, lenses);
        return lenses;
    }

    private record Frame(Node node, boolean insideMethod) {

    }

    private static void collectReferenceLenses(List<Node> fullAst, List<Node> nodes, String content, String uri,
            Path docPath, WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri,
            List<CodeLens> out) {
        Deque<Frame> stack = new ArrayDeque<>();
        for (Node n : nodes) {
            stack.push(new Frame(n, false));
        }
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            Node node = frame.node();
            if (node == null) {
                continue;
            }
            if (!frame.insideMethod() && node instanceof FuncDecl f) {
                addReferenceLens(fullAst, f, content, uri, docPath, workspaceIndex, workspaceRoot,
                        openDocumentsByUri, out);
            }
            boolean nextInsideMethod = frame.insideMethod()
                    || node instanceof ObjectExpression || node instanceof StructExpression;
            Deque<Node> children = new ArrayDeque<>();
            AstWalker.children(node, children);
            for (Node c : children) {
                stack.push(new Frame(c, nextInsideMethod));
            }
        }
    }

    private static void addReferenceLens(List<Node> fullAst, FuncDecl f, String content, String uri, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri,
            List<CodeLens> out) {
        if (f.nameColumn <= 0 || f.line <= 0) {
            return;
        }
        Range nameRange = LspPositions.nameRange(content, f.line, f.nameColumn, f.getName());
        List<Location> refs = ReferenceProvider.provide(fullAst, content, nameRange.getStart(), uri, docPath,
                workspaceIndex, workspaceRoot, openDocumentsByUri, false);
        String label = refs.size() == 1 ? "1 reference" : refs.size() + " references";
        Command command = new Command(label, "mira.showReferences",
                List.of(uri, nameRange.getStart(), refs));
        out.add(new CodeLens(nameRange, command, null));
    }

    private static void collectTestLenses(List<Node> ast, String uri, List<CodeLens> out) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof TestCall tc && tc.line > 0) {
                Range range = new Range(
                        new org.eclipse.lsp4j.Position(tc.line - 1, 0),
                        new org.eclipse.lsp4j.Position(tc.line - 1, 0));
                Command command = new Command("▶ Run Tests", "mira.runTests", List.of(uri));
                out.add(new CodeLens(range, command, null));
            }
            AstWalker.children(n, queue);
        }
    }
}
