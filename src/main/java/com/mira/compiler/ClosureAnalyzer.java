package com.mira.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public class ClosureAnalyzer {

    private final Map<List<Node>, Set<String>> capturedByBody = new HashMap<>();

    public Map<List<Node>, Set<String>> analyze(List<Node> program) {
        Deque<Set<String>> scopeStack = new ArrayDeque<>();
        scopeStack.push(new HashSet<>());
        walkBody(program, scopeStack);
        return capturedByBody;
    }

    public Set<String> getCaptured(List<Node> body) {
        return capturedByBody.getOrDefault(body, Set.of());
    }

    private void walkBody(List<Node> body, Deque<Set<String>> scopeStack) {
        for (Node node : body) {
            walkNode(node, scopeStack);
        }
    }

    private void walkNode(Node node, Deque<Set<String>> scopeStack) {
        switch (node) {
            case VarDecl vd ->
                scopeStack.peek().add(vd.getName());
            case FuncDecl fd -> {
                scopeStack.peek().add(fd.getName());
                walkFunction(fd.getBody(), fd.getParameters().stream()
                        .map(p -> p.name()).toList(), scopeStack);
            }
            case LambdaExpression le -> {
                walkFunction(le.getBody(), le.getParameters().stream()
                        .map(p -> p.name()).toList(), scopeStack);
            }
            case Expression expr -> {
            }
            case Statement stmt ->
                walkStmt(stmt, scopeStack);
            default -> {
            }
        }
    }

    private void walkFunction(List<Node> body, List<String> params, Deque<Set<String>> outerScopes) {
        Set<String> outerDeclared = new HashSet<>();
        for (Set<String> scope : outerScopes) {
            outerDeclared.addAll(scope);
        }

        Set<String> reads = new HashSet<>();
        Set<String> localDecls = new HashSet<>(params);
        collectReadsAndDecls(body, reads, localDecls);

        Set<String> captured = new HashSet<>(reads);
        captured.retainAll(outerDeclared);
        captured.removeAll(localDecls);
        capturedByBody.put(body, captured);

        Deque<Set<String>> inner = new ArrayDeque<>(outerScopes);
        inner.push(new HashSet<>(params));
        walkBody(body, inner);
    }

    private void collectReadsAndDecls(List<Node> body, Set<String> reads, Set<String> decls) {
        for (Node node : body) {
            collectNode(node, reads, decls);
        }
    }

    private void collectNode(Node node, Set<String> reads, Set<String> decls) {
        switch (node) {
            case VarDecl vd -> {
                decls.add(vd.getName());
                if (vd.getInitializer() != null) {
                    collectExpr(vd.getInitializer(), reads, decls);
                }
            }
            case FuncDecl fd ->
                decls.add(fd.getName());
            case LambdaExpression le -> {
            }
            case UnaryExpression ue when ue.getOperation().getLexeme().equals("$") -> {
                if (ue.getRight() instanceof Expression.DumbExpression de) {
                    reads.add(de.getValue());
                }
            }
            case Expression expr ->
                collectExpr(expr, reads, decls);
            case Statement stmt ->
                collectStmt(stmt, reads, decls);
            default -> {
            }
        }
    }

    private void collectExpr(Expression expr, Set<String> reads, Set<String> decls) {
        collectNode(expr, reads, decls);
    }

    private void collectStmt(Statement stmt, Set<String> reads, Set<String> decls) {
        collectNode(stmt, reads, decls);
    }

    private void walkExpr(Expression expr, Deque<Set<String>> scopeStack) {
    }

    private void walkStmt(Statement stmt, Deque<Set<String>> scopeStack) {
        switch (stmt) {
            case Statement.Block b -> {
                scopeStack.push(new HashSet<>());
                walkBody(b.getBody(), scopeStack);
                scopeStack.pop();
            }
            case Statement.If i -> {
                walkBody(i.getThenBody(), scopeStack);
                if (i.getElseBody() != null) {
                    walkBody(i.getElseBody(), scopeStack);
                }
            }
            case Statement.For f -> {
                scopeStack.push(new HashSet<>());
                walkBody(f.getVarDecls(), scopeStack);
                walkBody(f.getBody(), scopeStack);
                walkBody(f.getPostExpressions(), scopeStack);
                scopeStack.pop();
            }
            case Statement.While w ->
                walkBody(w.getBody(), scopeStack);
            case Statement.Foreach fe -> {
                scopeStack.push(new HashSet<>());
                scopeStack.peek().add(fe.getIterator().getName());
                walkBody(fe.getBody(), scopeStack);
                scopeStack.pop();
            }
            default -> {
            }
        }
    }
}
