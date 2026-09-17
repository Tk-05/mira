package com.mira.lsp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.TypeAnnotation;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.TypeAliasDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

public class SemanticTokenProvider {

    public static final List<String> TOKEN_TYPES = List.of("variable", "parameter", "function", "property", "type",
            "namespace");
    public static final List<String> TOKEN_MODIFIERS = List.of("declaration", "readonly");

    private static final int TYPE_VARIABLE = 0;
    private static final int TYPE_PARAMETER = 1;
    private static final int TYPE_FUNCTION = 2;
    private static final int TYPE_PROPERTY = 3;
    private static final int TYPE_TYPE = 4;
    private static final int TYPE_NAMESPACE = 5;
    private static final int MOD_DECLARATION = 1;
    private static final int MOD_READONLY = 2;

    private record SemToken(int line, int col, int length, int type, int modifiers) {

    }

    public static SemanticTokens provide(List<Node> ast) {
        return provide(ast, null);
    }

    public static SemanticTokens provide(List<Node> ast, Range range) {
        List<SemToken> tokens = new ArrayList<>();
        for (Node n : ast) {
            walkNode(n, tokens);
        }
        tokens.sort(Comparator.comparingInt(SemToken::line).thenComparingInt(SemToken::col));
        if (range != null) {
            int startLine = range.getStart().getLine();
            int endLine = range.getEnd().getLine();
            tokens = tokens.stream().filter(t -> t.line() >= startLine && t.line() <= endLine).toList();
        }
        return encode(tokens);
    }

    private static void walkNode(Node node, List<SemToken> out) {
        if (node instanceof FuncDecl f) {
            if (f.nameColumn > 0) {
                out.add(new SemToken(f.line - 1, f.nameColumn - 1, f.getName().length(), TYPE_FUNCTION,
                        MOD_DECLARATION));
            }
            for (Parameter p : f.getParameters()) {
                if (p.column() > 0) {
                    out.add(new SemToken(f.line - 1, p.column() - 1, p.name().length(), TYPE_PARAMETER,
                            MOD_DECLARATION));
                }
                emitTypeToken(p.type(), out);
            }
            emitTypeToken(f.getReturnType(), out);
            for (Node n : f.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof VarDecl v) {
            if (v.nameColumn > 0) {
                int mods = MOD_DECLARATION | (v.isConst() ? MOD_READONLY : 0);
                out.add(new SemToken(v.line - 1, v.nameColumn - 1, v.getName().length(), TYPE_VARIABLE, mods));
            }
            emitTypeToken(v.type, out);
            if (v.getInitializer() != null) {
                walkExpr(v.getInitializer(), out);
            }
        } else if (node instanceof TypeAliasDecl td) {
            emitTypeToken(td.getAliasedType(), out);
        } else if (node instanceof VarDestructure vd) {
            List<String> names = vd.getNames();
            List<Integer> cols = vd.getNameColumns();
            for (int i = 0; i < names.size(); i++) {
                if (cols.get(i) > 0) {
                    out.add(new SemToken(vd.line - 1, cols.get(i) - 1, names.get(i).length(), TYPE_VARIABLE,
                            MOD_DECLARATION));
                }
            }
            if (vd.getInitializer() != null) {
                walkExpr(vd.getInitializer(), out);
            }
        } else if (node instanceof Assign a) {
            walkExpr(a.getReference(), out);
            walkExpr(a.getExpression(), out);
        } else if (node instanceof Return r) {
            if (r.getValue() != null) {
                walkExpr(r.getValue(), out);
            }
        } else if (node instanceof Throw t) {
            walkExpr(t.getValue(), out);
        } else if (node instanceof If stmt) {
            walkExpr(stmt.getCondition(), out);
            for (Node n : stmt.getThenBody()) {
                walkNode(n, out);
            }
            if (stmt.getElseBody() != null) {
                for (Node n : stmt.getElseBody()) {
                    walkNode(n, out);
                }
            }
        } else if (node instanceof Loop stmt) {
            if (stmt.isForeach()) {
                walkNode(stmt.getIterator(), out);
                walkExpr(stmt.getCollection(), out);
            } else {
                for (Node n : stmt.getVarDecls()) {
                    walkNode(n, out);
                }
                if (stmt.getCondition() != null) {
                    walkExpr(stmt.getCondition(), out);
                }
                if (stmt.getPostExpressions() != null) {
                    for (Node n : stmt.getPostExpressions()) {
                        walkNode(n, out);
                    }
                }
            }
            for (Node n : stmt.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof While stmt) {
            walkExpr(stmt.getCondition(), out);
            for (Node n : stmt.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof Block stmt) {
            for (Node n : stmt.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof Switch stmt) {
            walkExpr(stmt.getSubject(), out);
            for (SwitchCase sc : stmt.getCases()) {
                walkExpr(sc.getValue(), out);
                for (Node n : sc.getBody()) {
                    walkNode(n, out);
                }
            }
            if (stmt.getDefaultBody() != null) {
                for (Node n : stmt.getDefaultBody()) {
                    walkNode(n, out);
                }
            }
        } else if (node instanceof TryCatch stmt) {
            for (Node n : stmt.getTryBody()) {
                walkNode(n, out);
            }
            for (Statement.CatchClause cc : stmt.getCatchClauses()) {
                for (Node n : cc.getBody()) {
                    walkNode(n, out);
                }
            }
            if (stmt.getFinallyBody() != null) {
                for (Node n : stmt.getFinallyBody()) {
                    walkNode(n, out);
                }
            }
        } else if (node instanceof Lock stmt) {
            walkExpr(stmt.getMutex(), out);
            for (Node n : stmt.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof ComptimeBlock stmt) {
            for (Node n : stmt.getBody()) {
                walkNode(n, out);
            }
        } else if (node instanceof TestCall stmt) {
            walkExpr(stmt.getName(), out);
            walkExpr(stmt.getTestFn(), out);
        } else if (node instanceof StaticAssert stmt) {
            walkExpr(stmt.getCondition(), out);
            if (stmt.getMessage() != null) {
                walkExpr(stmt.getMessage(), out);
            }
        } else if (node instanceof Expression expr) {
            walkExpr(expr, out);
        }
    }

    private static void emitPropertyToken(VarDecl v, List<SemToken> out) {
        if (v.nameColumn > 0) {
            int mods = MOD_DECLARATION | (v.isConst() ? MOD_READONLY : 0);
            out.add(new SemToken(v.line - 1, v.nameColumn - 1, v.getName().length(), TYPE_PROPERTY, mods));
        }
        if (v.getInitializer() != null) {
            walkExpr(v.getInitializer(), out);
        }
    }

    private static void walkExpr(Expression expr, List<SemToken> out) {
        if (expr == null) {
            return;
        }
        if (expr instanceof UnaryExpression u) {
            if (u.getOperation().getLexeme().equals("$") && u.getRight() instanceof DumbExpression d) {
                int line = u.getOperation().getLine() - 1;
                int col = u.getOperation().getColumn() - 1;
                if (line >= 0 && col >= 0) {
                    out.add(new SemToken(line, col, 1 + d.getValue().length(), TYPE_VARIABLE, 0));
                }
            } else {
                walkExpr(u.getRight(), out);
            }
        } else if (expr instanceof BinaryExpression b) {
            walkExpr(b.getLeft(), out);
            walkExpr(b.getRight(), out);
        } else if (expr instanceof ComplexExpression c) {
            for (Expression e : c.getExpressions()) {
                walkExpr(e, out);
            }
        } else if (expr instanceof CallExpression c) {
            walkExpr(c.getCallee(), out);
            for (Expression a : c.getArguments()) {
                walkExpr(a, out);
            }
        } else if (expr instanceof MethodCallExpression m) {
            walkExpr(m.getObject(), out);
            for (Expression a : m.getArguments()) {
                walkExpr(a, out);
            }
        } else if (expr instanceof FieldAccessExpression f) {
            walkExpr(f.getObject(), out);
        } else if (expr instanceof ArrayExpression a) {
            for (Expression e : a.getMembers()) {
                walkExpr(e, out);
            }
        } else if (expr instanceof AccessExpression a) {
            walkExpr(a.getReference(), out);
            for (Expression e : a.getIndecies()) {
                walkExpr(e, out);
            }
        } else if (expr instanceof ListExpression l) {
            for (Expression e : l.getMembers()) {
                walkExpr(e, out);
            }
        } else if (expr instanceof MapExpression m) {
            for (Expression v : m.getEntries().values()) {
                walkExpr(v, out);
            }
        } else if (expr instanceof TernaryExpression t) {
            walkExpr(t.getCondition(), out);
            walkExpr(t.getThenExpr(), out);
            walkExpr(t.getElseExpr(), out);
        } else if (expr instanceof LambdaExpression lam) {
            if (lam.line > 0) {
                for (Parameter p : lam.getParameters()) {
                    if (p.column() > 0) {
                        out.add(new SemToken(lam.line - 1, p.column() - 1, p.name().length(), TYPE_PARAMETER,
                                MOD_DECLARATION));
                    }
                    emitTypeToken(p.type(), out);
                }
            }
            for (Node n : lam.getBody()) {
                walkNode(n, out);
            }
        } else if (expr instanceof SwitchExpression sw) {
            walkExpr(sw.getSubject(), out);
            for (SwitchExpression.SwitchExprCase sc : sw.getCases()) {
                walkExpr(sc.value(), out);
                walkExpr(sc.result(), out);
            }
            if (sw.getDefaultExpr() != null) {
                walkExpr(sw.getDefaultExpr(), out);
            }
        } else if (expr instanceof AwaitExpression aw) {
            walkExpr(aw.getExpr(), out);
        } else if (expr instanceof TypeofExpression tw) {
            walkExpr(tw.getExpr(), out);
        } else if (expr instanceof ExecBlock eb) {
            for (Node n : eb.getBody()) {
                walkNode(n, out);
            }
        } else if (expr instanceof ObjectExpression obj) {
            for (VarDecl vd : obj.getVarDecls()) {
                emitPropertyToken(vd, out);
            }
            for (FuncDecl fd : obj.getMethods()) {
                walkNode(fd, out);
            }
        } else if (expr instanceof StructExpression st) {
            for (VarDecl vd : st.getVarDecls()) {
                emitPropertyToken(vd, out);
            }
            for (FuncDecl fd : st.getMethods()) {
                walkNode(fd, out);
            }
        } else if (expr instanceof RangeExpression rng) {
            walkExpr(rng.getStart(), out);
            walkExpr(rng.getEnd(), out);
        } else if (expr instanceof ThrownException th) {
            walkExpr(th.getValue(), out);
        } else if (expr instanceof NamespaceCallExpression ns) {
            // ns's own line/column point at the function name (see
            // Parser.parseNamespaceCallExpression) - the alias itself isn't
            // separately tracked, so only the function name gets a token here.
            if (ns.getLine() > 0 && ns.getColumn() > 0) {
                out.add(new SemToken(ns.getLine() - 1, ns.getColumn() - 1, ns.getFunctionName().length(), TYPE_FUNCTION,
                        0));
            }
            for (Expression a : ns.getArguments()) {
                walkExpr(a, out);
            }
        } else if (expr instanceof ImportExpression imp) {
            if (imp.getNamespace() != null && imp.namespaceColumn > 0) {
                out.add(new SemToken(imp.line - 1, imp.namespaceColumn - 1, imp.getNamespace().length(), TYPE_NAMESPACE,
                        MOD_DECLARATION));
            }
        }
    }

    private static void emitTypeToken(TypeAnnotation type, List<SemToken> out) {
        if (type == null) {
            return;
        }
        if (type.line() > 0 && type.column() > 0 && !type.name().isEmpty()) {
            out.add(new SemToken(type.line() - 1, type.column() - 1, type.name().length(), TYPE_TYPE, 0));
        }
        if (type.paramTypes() != null) {
            for (TypeAnnotation pt : type.paramTypes()) {
                emitTypeToken(pt, out);
            }
        }
        emitTypeToken(type.returnType(), out);
    }

    private static SemanticTokens encode(List<SemToken> sorted) {
        List<Integer> data = new ArrayList<>();
        int prevLine = 0, prevCol = 0;
        for (SemToken t : sorted) {
            if (t.line() < 0 || t.col() < 0 || t.length() <= 0) {
                continue;
            }
            int deltaLine = t.line() - prevLine;
            int deltaCol = deltaLine == 0 ? t.col() - prevCol : t.col();
            data.add(deltaLine);
            data.add(deltaCol);
            data.add(t.length());
            data.add(t.type());
            data.add(t.modifiers());
            prevLine = t.line();
            prevCol = t.col();
        }
        return new SemanticTokens(data);
    }
}
