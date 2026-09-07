package com.mira.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.FoldingRange;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

/**
 * One foldable region per body-bearing construct (function, if/else, loops,
 * try/catch, switch, struct/object literals and their methods, ...), derived
 * from each statement's own line/endLine - the same fields
 * {@link DocumentSymbolProvider} already relies on for its own ranges.
 * Multi-branch constructs (if/else, try/catch, switch) fold as a single region
 * covering the whole construct rather than one region per branch, since
 * individual branch line spans aren't tracked separately in the AST.
 */
public class FoldingRangeProvider {

    public static List<FoldingRange> provide(List<Node> ast) {
        List<FoldingRange> out = new ArrayList<>();
        walk(ast, out);
        return out;
    }

    private static void walk(List<Node> nodes, List<FoldingRange> out) {
        for (Node n : nodes) {
            collect(n, out);
        }
    }

    private static void collect(Node n, List<FoldingRange> out) {
        switch (n) {
            case FuncDecl f -> {
                addRange(f.line, f.endLine, out);
                walk(f.getBody(), out);
            }
            case VarDecl v -> {
                if (v.getInitializer() instanceof ObjectExpression obj) {
                    addRange(v.line, v.endLine, out);
                    for (FuncDecl m : obj.getMethods()) {
                        addRange(m.line, m.endLine, out);
                        walk(m.getBody(), out);
                    }
                } else if (v.getInitializer() instanceof StructExpression st) {
                    addRange(v.line, v.endLine, out);
                    for (FuncDecl m : st.getMethods()) {
                        addRange(m.line, m.endLine, out);
                        walk(m.getBody(), out);
                    }
                }
            }
            case If s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getThenBody(), out);
                if (s.getElseBody() != null) {
                    walk(s.getElseBody(), out);
                }
            }
            case Loop s -> {
                addRange(s.line, s.endLine, out);
                if (!s.isForeach()) {
                    walk(s.getVarDecls(), out);
                }
                walk(s.getBody(), out);
            }
            case While s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getBody(), out);
            }
            case Block s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getBody(), out);
            }
            case Switch s -> {
                addRange(s.line, s.endLine, out);
                for (Switch.SwitchCase sc : s.getCases()) {
                    walk(sc.getBody(), out);
                }
                if (s.getDefaultBody() != null) {
                    walk(s.getDefaultBody(), out);
                }
            }
            case TryCatch s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getTryBody(), out);
                for (TryCatch.CatchClause cc : s.getCatchClauses()) {
                    walk(cc.getBody(), out);
                }
                if (s.getFinallyBody() != null) {
                    walk(s.getFinallyBody(), out);
                }
            }
            case Lock s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getBody(), out);
            }
            case ComptimeBlock s -> {
                addRange(s.line, s.endLine, out);
                walk(s.getBody(), out);
            }
            default -> {
            }
        }
    }

    private static void addRange(int line, int endLine, List<FoldingRange> out) {
        int startLine = Math.max(line - 1, 0);
        int lspEndLine = Math.max(endLine - 1, startLine);
        if (lspEndLine <= startLine) {
            // Nothing to fold - the construct fits on one line (or its
            // endLine wasn't tracked), and a zero/negative-length fold
            // region is meaningless to a client.
            return;
        }
        out.add(new FoldingRange(startLine, lspEndLine));
    }
}
