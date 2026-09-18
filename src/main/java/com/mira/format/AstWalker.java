package com.mira.format;

import java.util.Deque;
import java.util.List;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AssignExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.StructInitExpression;
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

public final class AstWalker {

    private AstWalker() {
    }

    public static void children(Node node, Deque<Node> queue) {
        switch (node) {
            case FuncDecl s -> queue.addAll(s.getBody());
            case VarDecl s -> {
                if (s.getInitializer() != null) {
                    queue.add(s.getInitializer());
                }
            }
            case Assign s -> {
                queue.add(s.getReference());
                if (s.getExpression() != null) {
                    queue.add(s.getExpression());
                }
            }
            case Return s -> {
                if (s.getValue() != null) {
                    queue.add(s.getValue());
                }
            }
            case Throw s -> {
                if (s.getValue() != null) {
                    queue.add(s.getValue());
                }
            }
            case If s -> {
                queue.add(s.getCondition());
                queue.addAll(s.getThenBody());
                if (s.getElseBody() != null) {
                    queue.addAll(s.getElseBody());
                }
            }
            case While s -> {
                queue.add(s.getCondition());
                queue.addAll(s.getBody());
            }
            case Loop s -> {
                if (s.isForeach()) {
                    queue.add(s.getIterator());
                    queue.add(s.getCollection());
                } else {
                    queue.addAll(s.getVarDecls());
                    if (s.getCondition() != null) {
                        queue.add(s.getCondition());
                    }
                    queue.addAll(s.getPostExpressions());
                }
                queue.addAll(s.getBody());
            }
            case Block s -> queue.addAll(s.getBody());
            case Switch s -> {
                queue.add(s.getSubject());
                for (Switch.SwitchCase sc : s.getCases()) {
                    queue.add(sc.getValue());
                    queue.addAll(sc.getBody());
                }
                if (s.getDefaultBody() != null) {
                    queue.addAll(s.getDefaultBody());
                }
            }
            case TryCatch s -> {
                queue.addAll(s.getTryBody());
                for (TryCatch.CatchClause cc : s.getCatchClauses()) {
                    queue.addAll(cc.getBody());
                }
                if (s.getFinallyBody() != null) {
                    queue.addAll(s.getFinallyBody());
                }
            }
            case Lock s -> {
                queue.add(s.getMutex());
                queue.addAll(s.getBody());
            }
            case ComptimeBlock s -> queue.addAll(s.getBody());
            case VarDestructure s -> {
                if (s.getInitializer() != null) {
                    queue.add(s.getInitializer());
                }
            }
            case StaticAssert s -> {
                queue.add(s.getCondition());
                if (s.getMessage() != null) {
                    queue.add(s.getMessage());
                }
            }
            case TestCall s -> {
                queue.add(s.getName());
                queue.add(s.getTestFn());
            }
            case ObjectExpression e -> {
                queue.addAll(e.getVarDecls());
                queue.addAll(e.getMethods());
            }
            case StructExpression e -> {
                queue.addAll(e.getVarDecls());
                queue.addAll(e.getMethods());
            }
            case StructInitExpression e -> {
                queue.add(e.getTarget());
                queue.addAll(e.getOverrides().values());
            }
            case UnaryExpression e -> {
                if (e.getRight() != null) {
                    queue.add(e.getRight());
                }
            }
            case AssignExpression e -> {
                queue.add(e.getReference());
                queue.add(e.getValue());
            }
            case ComplexExpression e -> queue.addAll(e.getExpressions());
            case CallExpression e -> {
                queue.add(e.getCallee());
                queue.addAll(e.getArguments());
            }
            case ArrayExpression e -> queue.addAll(e.getMembers());
            case AccessExpression e -> {
                queue.add(e.getReference());
                queue.addAll(e.getIndecies());
            }
            case ListExpression e -> queue.addAll(e.getMembers());
            case MapExpression e -> queue.addAll(e.getEntries().values());
            case NamespaceCallExpression e -> queue.addAll(e.getArguments());
            case RangeExpression e -> {
                if (e.getStart() != null) {
                    queue.add(e.getStart());
                }
                if (e.getEnd() != null) {
                    queue.add(e.getEnd());
                }
            }
            case FieldAccessExpression e -> queue.add(e.getObject());
            case MethodCallExpression e -> {
                queue.add(e.getObject());
                queue.addAll(e.getArguments());
            }
            case BinaryExpression e -> {
                queue.add(e.getLeft());
                queue.add(e.getRight());
            }
            case TernaryExpression e -> {
                queue.add(e.getCondition());
                queue.add(e.getThenExpr());
                queue.add(e.getElseExpr());
            }
            case AwaitExpression e -> queue.add(e.getExpr());
            case TypeofExpression e -> queue.add(e.getExpr());
            case SwitchExpression e -> {
                queue.add(e.getSubject());
                for (SwitchExpression.SwitchExprCase sc : e.getCases()) {
                    queue.add(sc.value());
                    queue.add(sc.result());
                }
                if (e.getDefaultExpr() != null) {
                    queue.add(e.getDefaultExpr());
                }
            }
            case LambdaExpression e -> queue.addAll(e.getBody());
            case ExecBlock e -> queue.addAll(e.getBody());
            case ThrownException e -> {
                if (e.getValue() != null) {
                    queue.add(e.getValue());
                }
            }
            default -> {
            }
        }
    }

    /**
     * Whether any top-level statement in {@code body} introduces a new binding into
     * its enclosing scope (a variable, function, enum, module, or comptime
     * declaration). Shared by the interpreter (to decide whether a loop body needs
     * a fresh {@code Environment} frame per iteration) and the Resolver (to decide
     * whether that same body gets a resolver scope pushed for it) - extracted here
     * specifically so the two can never drift apart on this question; only checks
     * the immediate list, not nested blocks, since those get their own frame/scope
     * independently when they execute.
     */
    public static boolean declaresBindings(List<Node> body) {
        for (Node node : body) {
            if (node instanceof VarDecl || node instanceof VarDestructure || node instanceof FuncDecl
                    || node instanceof EnumDecl || node instanceof ModuleDecl || node instanceof ComptimeBlock) {
                return true;
            }
        }
        return false;
    }
}
