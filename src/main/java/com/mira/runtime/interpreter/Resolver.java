package com.mira.runtime.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;

/**
 * Static, one-time analysis pass (Crafting-Interpreters-style "Resolver") that
 * pre-computes, for every lexically-resolvable "$name" variable reference, how
 * many {@link Environment#getParent()} hops from wherever it executes reach the
 * {@link Environment} that declares it, and its slot index there - so the
 * interpreter can use {@link Environment#getAt}/{@code assignAt} (an array
 * index) instead of hashing the name on every access. It also records, for
 * every construct that gets its own runtime {@link Environment} frame, the full
 * ordered list of local names declared directly in that scope - so the
 * interpreter can allocate that frame in slot-array mode
 * ({@link Environment#Environment(Environment, String[])}) instead of HashMap
 * mode.
 */
public final class Resolver {

    private static final class Scope {

        final Scope enclosing;
        final Map<String, Integer> slots = new HashMap<>();
        final List<String> names = new ArrayList<>();

        Scope(Scope enclosing) {
            this.enclosing = enclosing;
        }

        int declare(String name) {
            int slot = names.size();
            slots.put(name, slot);
            names.add(name);
            return slot;
        }

        String[] toSlotNames() {
            return names.toArray(new String[0]);
        }
    }

    private Scope current;

    private Resolver() {
    }

    public static void resolve(List<Node> program) {
        new Resolver().resolveBody(program);
    }

    private void resolveBody(List<Node> body) {
        for (Node node : body) {
            resolveNode(node);
        }
    }

    /**
     * Pushes a fresh child scope, runs {@code action}, then pops - returning the
     * ordered names declared directly in that scope (never null, possibly empty),
     * for the caller to cache on whichever AST node owns this runtime frame.
     * {@code opaque} starts the new scope with no enclosing link at all - used for
     * struct/object method bodies and isolated exec blocks, whose defining
     * Environment is genuinely parent-less at runtime, not just "the current
     * lexical scope one level up".
     */
    private String[] withScope(boolean opaque, Runnable action) {
        Scope previous = current;
        Scope scope = new Scope(opaque ? null : current);
        current = scope;
        try {
            action.run();
        } finally {
            current = previous;
        }
        return scope.toSlotNames();
    }

    private void resolveNode(Node node) {
        switch (node) {
            case null -> {
            }
            case ComptimeBlock ignoredComptimeBlock -> {
                // Never executed by the normal interpreter
            }
            case VarDecl s -> {
                if (s.getInitializer() != null) {
                    resolveNode(s.getInitializer());
                }
                if (current != null) {
                    s.resolvedSlot = current.declare(s.getName());
                }
            }
            case VarDestructure s -> {
                if (s.getInitializer() != null) {
                    resolveNode(s.getInitializer());
                }
                if (current != null) {
                    int[] slotsArr = new int[s.getNames().size()];
                    for (int i = 0; i < slotsArr.length; i++) {
                        slotsArr[i] = current.declare(s.getNames().get(i));
                    }
                    s.resolvedSlots = slotsArr;
                }
            }
            case If s -> {
                resolveNode(s.getCondition());
                if (s.getThenBody() != null) {
                    s.thenSlotNames = withScope(false, () -> resolveBody(s.getThenBody()));
                }
                if (s.getElseBody() != null) {
                    s.elseSlotNames = withScope(false, () -> resolveBody(s.getElseBody()));
                }
            }
            case While s -> {
                resolveNode(s.getCondition());
                if (AstWalker.declaresBindings(s.getBody())) {
                    s.bodySlotNames = withScope(false, () -> resolveBody(s.getBody()));
                } else {
                    resolveBody(s.getBody());
                }
            }
            case Loop s -> resolveLoop(s);
            case Block s -> s.slotNames = withScope(false, () -> resolveBody(s.getBody()));
            case Switch s -> {
                resolveNode(s.getSubject());
                for (Switch.SwitchCase sc : s.getCases()) {
                    resolveNode(sc.getValue());
                    sc.slotNames = withScope(false, () -> resolveBody(sc.getBody()));
                }
                if (s.getDefaultBody() != null) {
                    s.defaultSlotNames = withScope(false, () -> resolveBody(s.getDefaultBody()));
                }
            }
            case TryCatch s -> {
                s.trySlotNames = withScope(false, () -> resolveBody(s.getTryBody()));
                for (CatchClause clause : s.getCatchClauses()) {
                    clause.slotNames = withScope(false, () -> {
                        if (clause.getParamName() != null) {
                            clause.resolvedSlot = current.declare(clause.getParamName());
                        }
                        resolveBody(clause.getBody());
                    });
                }
                if (s.getFinallyBody() != null && !s.getFinallyBody().isEmpty()) {
                    s.finallySlotNames = withScope(false, () -> resolveBody(s.getFinallyBody()));
                }
            }
            case Lock s -> {
                resolveNode(s.getMutex());
                s.slotNames = withScope(false, () -> resolveBody(s.getBody()));
            }
            case FuncDecl s -> s.resolvedSlotNames = withScope(false,
                    () -> resolveFunctionLike(s.getParameters(), s.getVariadicParam(), s.getBody()));
            case LambdaExpression e -> e.resolvedSlotNames = withScope(false,
                    () -> resolveFunctionLike(e.getParameters(), e.getVariadicParam(), e.getBody()));
            case ObjectExpression e -> resolveObjectOrStruct(e.getVarDecls(), e.getMethods());
            case StructExpression e -> resolveObjectOrStruct(e.getVarDecls(), e.getMethods());
            case ExecBlock e -> e.slotNames = withScope(e.isIsolated(), () -> resolveBody(e.getBody()));
            case UnaryExpression e -> resolveUnary(e);
            default -> {
                ArrayDeque<Node> queue = new ArrayDeque<>();
                AstWalker.children(node, queue);
                for (Node child : queue) {
                    resolveNode(child);
                }
            }
        }
    }

    private void resolveLoop(Loop s) {
        if (s.isForeach()) {
            resolveNode(s.getCollection());
            s.iteratorSlotNames = withScope(false, () -> {
                if (current != null && s.getIterator() != null) {
                    s.getIterator().resolvedSlot = current.declare(s.getIterator().getName());
                }
                s.foreachBodySlotNames = withScope(false, () -> resolveBody(s.getBody()));
            });
            return;
        }

        Runnable headerAndBody = () -> {
            resolveBody(s.getVarDecls());
            if (s.getCondition() != null) {
                resolveNode(s.getCondition());
            }
            if (AstWalker.declaresBindings(s.getBody())) {
                s.forBodySlotNames = withScope(false, () -> resolveBody(s.getBody()));
            } else {
                resolveBody(s.getBody());
            }
            resolveBody(s.getPostExpressions());
        };

        if (!s.getVarDecls().isEmpty()) {
            s.headerSlotNames = withScope(false, headerAndBody);
        } else {
            headerAndBody.run();
        }
    }

    private void resolveFunctionLike(List<Parameter> parameters, String variadicParam, List<Node> body) {
        for (Parameter p : parameters) {
            current.declare(p.name());
        }
        if (variadicParam != null) {
            current.declare(variadicParam);
        }
        resolveBody(body);
    }

    private void resolveObjectOrStruct(List<VarDecl> fields, List<FuncDecl> methods) {
        for (VarDecl field : fields) {
            // Fields are never resolver-managed slots - they live in the dynamically
            // named instance Environment, accessed by name at runtime (this.field /
            // bare-name fallback inside a method body), which is exactly the
            // UNRESOLVED behavior a method body's own field references already get
            // below (methods are opaque scopes, so a bare field reference never
            // matches anything in the method's own resolver scope and stays -1).
            if (field.getInitializer() != null) {
                resolveNode(field.getInitializer());
            }
        }
        for (FuncDecl method : methods) {
            // Opaque: a struct/object method's defining Environment is parent-less at
            // runtime (Interpreter#visitObjectExpression constructs it with `new
            // Environment()`), so it can never see an enclosing function's locals -
            // only its own params/locals, its own fields (dynamically, by name), and
            // globals, all via today's fallback.
            method.resolvedSlotNames = withScope(true,
                    () -> resolveFunctionLike(method.getParameters(), method.getVariadicParam(), method.getBody()));
        }
    }

    private void resolveUnary(UnaryExpression e) {
        if ("$".equals(e.getOperation().getLexeme())) {
            if (e.getRight() instanceof DumbExpression name) {
                resolveVariableReference(e, name.getValue());
            } else if (e.getRight() != null) {
                // Computed/dynamic variable name (e.g. a name built from an expression) -
                // can't resolve statically, but still recurse in case that expression
                // itself references a resolvable local.
                resolveNode(e.getRight());
            }
            return;
        }
        if (e.getRight() != null) {
            resolveNode(e.getRight());
        }
    }

    private void resolveVariableReference(UnaryExpression varExpr, String name) {
        int distance = 0;
        Scope scope = current;
        while (scope != null) {
            Integer slot = scope.slots.get(name);
            if (slot != null) {
                varExpr.resolvedDistance = distance;
                varExpr.resolvedSlot = slot;
                return;
            }
            distance++;
            scope = scope.enclosing;
        }
        // Not found in any resolver-managed scope: stays UNRESOLVED (-1, the
        // default) - correctly covers globals, dynamically-injected names, and
        // anything else today's fallback chain walk already handles.
    }
}
