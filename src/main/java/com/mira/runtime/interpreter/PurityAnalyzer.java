package com.mira.runtime.interpreter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

public class PurityAnalyzer {

    private static final Set<String> IMPURE_GLOBALS = Set.of(
            "print", "exec", "exit", "readFile", "writeFile"
    );

    private static final Set<String> IMPURE_NAMESPACES = Set.of(
            "io", "shell", "net", "process", "dateTime", "collection"
    );

    private static final Set<String> PURE_GLOBALS = Set.of(
            "eval", "length", "assert"
    );

    public static Set<String> analyze(List<Node> asts) {
        Map<String, FuncDecl> functions = new HashMap<>();
        for (Node ast : asts) {
            if (ast instanceof FuncDecl f) {
                functions.put(f.getName(), f);
            }
        }

        Set<String> pure = new HashSet<>(functions.keySet());

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, FuncDecl> entry : functions.entrySet()) {
                if (pure.contains(entry.getKey())
                        && !isBodyPure(entry.getValue().getBody(), pure)) {
                    pure.remove(entry.getKey());
                    changed = true;
                }
            }
        }

        return pure;
    }

    private static boolean isBodyPure(List<Node> body, Set<String> pure) {
        for (Node node : body) {
            if (!isNodePure(node, pure)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNodePure(Node node, Set<String> pure) {
        return switch (node) {
            case CallExpression call ->
                isCallPure(call, pure);
            case NamespaceCallExpression ns ->
                !IMPURE_NAMESPACES.contains(ns.getAlias());
            case BinaryExpression bin ->
                isNodePure(bin.getLeft(), pure)
                && isNodePure(bin.getRight(), pure);
            case UnaryExpression u ->
                u.getRight() == null || isNodePure(u.getRight(), pure);
            case ComplexExpression c ->
                c.getExpressions().stream().allMatch(e -> isNodePure(e, pure));
            case ArrayExpression a ->
                a.getMembers().stream().allMatch(e -> isNodePure(e, pure));
            case TupleExpression t ->
                t.getMembers().stream().allMatch(e -> isNodePure(e, pure));
            case ListExpression l ->
                l.getMembers().stream().allMatch(e -> isNodePure(e, pure));
            case AccessExpression a ->
                isNodePure(a.getReference(), pure)
                && a.getIndecies().stream().allMatch(i -> isNodePure(i, pure));
            case FieldAccessExpression f ->
                isNodePure(f.getObject(), pure);
            case ObjectExpression o ->
                o.getVarDecls().stream()
                .allMatch(v -> v.getInitializer() == null || isNodePure(v.getInitializer(), pure));
            case LambdaExpression lam ->
                isBodyPure(lam.getBody(), pure);
            case RangeExpression r ->
                true;
            case DumbExpression d2 ->
                true;

            case Overwrite o2 ->
                false;
            case If stmt ->
                isNodePure(stmt.getCondition(), pure)
                && isBodyPure(stmt.getThenBody(), pure)
                && (stmt.getElseBody() == null || isBodyPure(stmt.getElseBody(), pure));
            case For stmt ->
                (stmt.getCondition() == null || isNodePure(stmt.getCondition(), pure))
                && isBodyPure(stmt.getVarDecls(), pure)
                && isBodyPure(stmt.getPostExpressions(), pure)
                && isBodyPure(stmt.getBody(), pure);
            case While stmt ->
                isNodePure(stmt.getCondition(), pure)
                && isBodyPure(stmt.getBody(), pure);
            case Foreach stmt ->
                isBodyPure(stmt.getBody(), pure);
            case Block stmt ->
                isBodyPure(stmt.getBody(), pure);
            case TryCatch stmt ->
                isBodyPure(stmt.getTryBody(), pure)
                && isBodyPure(stmt.getCatchBody(), pure);
            case Switch stmt ->
                isNodePure(stmt.getSubject(), pure)
                && stmt.getCases().stream().allMatch(c -> isBodyPure(c.getBody(), pure))
                && (stmt.getDefaultBody() == null || isBodyPure(stmt.getDefaultBody(), pure));
            case Return ret ->
                ret.getValue() == null || isNodePure(ret.getValue(), pure);
            case VarDecl v ->
                v.getInitializer() == null || isNodePure(v.getInitializer(), pure);
            case Assign a ->
                isNodePure(a.getExpression(), pure);
            case Throw t ->
                isNodePure(t.getValue(), pure);

            default ->
                true;
        };
    }

    private static boolean isCallPure(CallExpression call, Set<String> pure) {
        if (!(call.getCallee() instanceof DumbExpression d)) {
            return false;
        }
        String name = d.getValue();
        if (IMPURE_GLOBALS.contains(name)) {
            return false;
        }
        if (PURE_GLOBALS.contains(name)) {
            return true;
        }

        return pure.contains(name);
    }
}
