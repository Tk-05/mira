package com.mira.resolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.Flags;
import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.error.resolver.StaticCheckError.ArityMismatchError;
import com.mira.error.resolver.StaticCheckError.BreakOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.ConstReassignmentError;
import com.mira.error.resolver.StaticCheckError.ContinueOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.DuplicateDeclarationError;
import com.mira.error.resolver.StaticCheckError.MissingModuleDeclarationError;
import com.mira.error.resolver.StaticCheckError.ModuleNameMismatchError;
import com.mira.error.resolver.StaticCheckError.UndeclaredVariableError;
import com.mira.error.resolver.StaticCheckError.UndefinedFunctionError;
import com.mira.error.resolver.StaticCheckError.UnknownNamespaceError;
import com.mira.lexer.token.TokenType;
import com.mira.lib.LibIndex;
import com.mira.linter.LintScope;
import com.mira.linter.LintScope.VarInfo;
import com.mira.parser.nodes.Node;
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
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class StaticCheck {

    private final LintScope scope = new LintScope();
    private final Set<String> knownFunctions = new HashSet<>(LibIndex.GLOBAL_NAMES);
    private final Set<String> knownNamespaces = new HashSet<>();
    private final List<MiraError> errors = new ArrayList<>();
    private int loopDepth = 0;
    private final Map<String, int[]> knownArities = new HashMap<>();
    private boolean isModule = false;
    private final Set<String> externallyUsed;

    public StaticCheck() {
        this(Set.of());
    }

    public StaticCheck(Set<String> externallyUsed) {
        this.externallyUsed = externallyUsed;
        knownFunctions.addAll(LibIndex.INTERNAL_NAMES);
        LibIndex.GLOBAL_ARITIES.forEach((name, arity) -> {
            if (arity >= 0) {
                knownArities.put(name, new int[]{arity, arity});
            }
        });
    }

    public static Set<String> collectNamespaceCalls(List<Node> ast, String alias) {
        Set<String> result = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node == null) {
                continue;
            }
            if (node instanceof NamespaceCallExpression nce && alias.equals(nce.getAlias())) {
                result.add(nce.getFunctionName());
                queue.addAll(nce.getArguments());
            } else {
                addChildren(node, queue);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void addChildren(Node node, Deque<Node> queue) {
        switch (node) {
            case FuncDecl s ->
                queue.addAll(s.getBody());
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
            case Throw s ->
                queue.add(s.getValue());
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
            case Foreach s -> {
                queue.add(s.getCollection());
                queue.addAll(s.getBody());
            }
            case For s -> {
                queue.addAll(s.getVarDecls());
                if (s.getCondition() != null) {
                    queue.add(s.getCondition());

                }
                queue.addAll(s.getBody());
            }
            case Block s ->
                queue.addAll(s.getBody());
            case TryCatch s -> {
                queue.addAll(s.getTryBody());
                s.getCatchClauses().forEach(c -> queue.addAll(c.getBody()));
            }
            case Lock s -> {
                queue.add(s.getMutex());
                queue.addAll(s.getBody());
            }
            case ComptimeBlock s ->
                queue.addAll(s.getBody());
            case BinaryExpression e -> {
                queue.add(e.getLeft());
                queue.add(e.getRight());
            }
            case UnaryExpression e ->
                queue.add(e.getRight());
            case CallExpression e -> {
                queue.add(e.getCallee());
                queue.addAll(e.getArguments());
            }
            case NamespaceCallExpression e ->
                queue.addAll(e.getArguments());
            case AccessExpression e -> {
                queue.add(e.getReference());
                queue.addAll(e.getIndecies());
            }
            case FieldAccessExpression e ->
                queue.add(e.getObject());
            case MethodCallExpression e -> {
                queue.add(e.getObject());
                queue.addAll(e.getArguments());
            }
            case TernaryExpression e -> {
                queue.add(e.getCondition());
                queue.add(e.getThenExpr());
                queue.add(e.getElseExpr());
            }
            case ArrayExpression e ->
                queue.addAll(e.getMembers());
            case ListExpression e ->
                queue.addAll(e.getMembers());
            case LambdaExpression e ->
                queue.addAll(e.getBody());
            case ExecBlock e ->
                queue.addAll(e.getBody());
            default -> {
            }
        }
    }

    public void check(List<Node> ast) {
        if (ast.isEmpty() || !(ast.getFirst() instanceof com.mira.parser.nodes.statement.Statement.ModuleDecl moduleDecl)) {
            errors.add(new MissingModuleDeclarationError());
            throw new MultipleStaticCheckErrors(errors);
        }
        if (Flags.fileName != null) {
            String expectedName = Flags.fileName.replace(".mira", "");
            if (!moduleDecl.getModuleName().equals(expectedName)) {
                errors.add(new ModuleNameMismatchError(Flags.fileName, expectedName, moduleDecl.getModuleName(), moduleDecl.line));
                throw new MultipleStaticCheckErrors(errors);
            }
        }

        scope.push();
        isModule = true;

        for (String builtin : LibIndex.GLOBAL_NAMES) {
            scope.declare(builtin, 0, 0, false);
            scope.markUsed(builtin);
        }

        for (Node node : ast) {
            switch (node) {
                case FuncDecl f -> {
                    scope.declareFunction(f.getName(), f.line, f.nameColumn);
                    knownFunctions.add(f.getName());
                    if (f.getVariadicParam() == null) {
                        knownArities.put(f.getName(), new int[]{f.getArity(), f.getMaxArity()});
                    }
                }
                case EnumDecl e -> {
                    scope.declare(e.getIdentifier(), e.line, 0, true);
                    scope.markUsed(e.getIdentifier());
                }
                case ImportExpression imp ->
                    preDeclareImport(imp);
                default -> {
                }
            }
        }

        scope.markUsed("main");
        externallyUsed.forEach(scope::markUsed);

        resolveNodes(ast);
        popScope();

        if (!errors.isEmpty()) {
            throw new MultipleStaticCheckErrors(errors);
        }
    }

    private void resolveNodes(List<Node> nodes) {
        for (Node node : nodes) {
            resolveNode(node);
        }
    }

    private void resolveNode(Node node) {
        switch (node) {
            case VarDecl stmt ->
                resolveVarDecl(stmt);
            case FuncDecl stmt ->
                resolveFuncDecl(stmt);
            case Assign stmt ->
                resolveAssign(stmt);
            case Return stmt -> {
                if (stmt.getValue() != null) {
                    resolveExpr(stmt.getValue());
                }
            }
            case If stmt ->
                resolveIf(stmt);
            case For stmt ->
                resolveFor(stmt);
            case While stmt ->
                resolveWhile(stmt);
            case Foreach stmt ->
                resolveForeach(stmt);
            case Block stmt ->
                resolveBlock(stmt);
            case Switch stmt ->
                resolveSwitch(stmt);
            case TryCatch stmt ->
                resolveTryCatch(stmt);
            case Throw stmt ->
                resolveExpr(stmt.getValue());
            case EnumDecl stmt -> {
            }
            case VarDestructure stmt ->
                resolveVarDestructure(stmt);
            case Lock stmt -> {
                resolveExpr(stmt.getMutex());
                resolveNodes(stmt.getBody());
            }
            case ComptimeBlock stmt ->
                resolveNodes(stmt.getBody());
            case TestCall stmt -> {
                resolveExpr(stmt.getName());
                resolveExpr(stmt.getTestFn());
            }
            case Break stmt -> {
                if (loopDepth == 0) {
                    errors.add(new BreakOutsideLoopError(stmt.line));
                }
            }
            case Continue stmt -> {
                if (loopDepth == 0) {
                    errors.add(new ContinueOutsideLoopError(stmt.line));
                }
            }
            case CallExpression e ->
                resolveCallExpression(e);
            default ->
                resolveExpr(node);
        }
    }

    private void resolveExpr(Node node) {
        switch (node) {
            case UnaryExpression e when "$".equals(e.getOperation().getLexeme()) -> {
                if (e.getRight() instanceof DumbExpression d && isIdentifier(d)) {
                    String name = d.getValue();
                    if (!scope.isDeclared(name)) {
                        errors.add(new UndeclaredVariableError(name, d.getLine(), d.getColumn()));
                    } else {
                        scope.markUsed(name);
                    }
                } else if (e.getRight() != null) {
                    resolveExpr(e.getRight());
                }
            }
            case UnaryExpression e -> {
                if (e.getRight() != null) {
                    resolveExpr(e.getRight());
                }
            }
            case BinaryExpression e when "|>".equals(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                if (e.getRight() instanceof CallExpression call) {
                    resolveCallExpression(call, 1);
                } else {
                    resolveExpr(e.getRight());
                }
            }
            case BinaryExpression e when "+".equals(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
                boolean leftStr = isStringLiteral(e.getLeft());
                boolean rightStr = isStringLiteral(e.getRight());
                boolean leftLit = isNonStringLiteral(e.getLeft());
                boolean rightLit = isNonStringLiteral(e.getRight());
                if ((leftStr && rightLit) || (leftLit && rightStr)) {
                    WarningCollector.emit(WarningLevel.HINT,
                            "Implicit string concatenation: mixed String and non-String operands",
                            e.getOperator());
                }
            }
            case BinaryExpression e when "/".equals(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
                if (isZeroLiteral(e.getRight())) {
                    WarningCollector.emit(WarningLevel.WARNING, "Division by zero", e.getOperator());
                }
            }
            case BinaryExpression e -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
            }
            case CallExpression e ->
                resolveCallExpression(e);
            case NamespaceCallExpression e -> {
                String alias = e.getAlias();
                if (!knownNamespaces.contains(alias)) {
                    errors.add(new UnknownNamespaceError(alias, e.getLine(), 0));
                }
                scope.markUsed(alias);
                e.getArguments().forEach(this::resolveExpr);
            }
            case AccessExpression e -> {
                resolveExpr(e.getReference());
                e.getIndecies().forEach(this::resolveExpr);
            }
            case FieldAccessExpression e ->
                resolveExpr(e.getObject());
            case MethodCallExpression e -> {
                resolveExpr(e.getObject());
                e.getArguments().forEach(this::resolveExpr);
            }
            case ArrayExpression e ->
                e.getMembers().forEach(this::resolveExpr);
            case ListExpression e ->
                e.getMembers().forEach(this::resolveExpr);
            case MapExpression e ->
                e.getEntries().values().forEach(this::resolveExpr);
            case ObjectExpression e -> {
                e.getVarDecls().stream()
                        .filter(v -> v.getInitializer() != null)
                        .forEach(v -> resolveExpr(v.getInitializer()));
                for (var method : e.getMethods()) {
                    scope.push();
                    method.getParameters().forEach(p -> scope.declare(p.name(), method.line, 0, false));
                    if (method.getVariadicParam() != null) {
                        scope.declare(method.getVariadicParam(), method.line, 0, false);
                    }
                    scope.declare("this", 0, 0, false);
                    scope.markUsed("this");
                    e.getVarDecls().forEach(f -> {
                        scope.declare(f.getName(), 0, 0, false);
                        scope.markUsed(f.getName());
                    });
                    resolveBody(method.getBody());
                    popScope();
                }
            }
            case LambdaExpression e -> {
                scope.push();
                e.getParameters().forEach(p -> scope.declare(p.name(), 0, 0, false));
                if (e.getVariadicParam() != null) {
                    scope.declare(e.getVariadicParam(), 0, 0, false);
                }
                resolveBody(e.getBody());
                popScope();
            }
            case TernaryExpression e -> {
                resolveExpr(e.getCondition());
                resolveExpr(e.getThenExpr());
                resolveExpr(e.getElseExpr());
            }
            case ComplexExpression e ->
                e.getExpressions().forEach(this::resolveExpr);
            case TypeofExpression e ->
                resolveExpr(e.getExpr());
            case SwitchExpression e -> {
                resolveExpr(e.getSubject());
                for (var c : e.getCases()) {
                    resolveExpr(c.value());
                    resolveExpr(c.result());
                }
                if (e.getDefaultExpr() != null) {
                    resolveExpr(e.getDefaultExpr());
                }
            }
            case RangeExpression e -> {
                if (e.getStart() != null) {
                    resolveExpr(e.getStart());
                }
                if (e.getEnd() != null) {
                    resolveExpr(e.getEnd());
                }
                if (e.getStepsize() != null) {
                    resolveExpr(e.getStepsize());
                }
            }
            case ImportExpression e ->
                preDeclareImport(e);
            case AwaitExpression e ->
                resolveExpr(e.getExpr());
            case ThrownException e -> {
                if (e.getValue() != null) {
                    resolveExpr(e.getValue());
                }
            }
            case ExecBlock e -> {
                scope.push();
                resolveBody(e.getBody());
                popScope();
            }
            case DumbExpression e -> {
                if (isIdentifier(e)) {
                    scope.markUsed(e.getValue());
                }
            }
            default -> {
            }
        }
    }

    private void resolveCallExpression(CallExpression expr) {
        resolveCallExpression(expr, 0);
    }

    private void resolveCallExpression(CallExpression expr, int implicitArgs) {
        if (expr.getCallee() instanceof DumbExpression callee && isIdentifier(callee)) {
            String name = callee.getValue();

            boolean callable = knownFunctions.contains(name)
                    || (scope.isDeclared(name) && !knownNamespaces.contains(name));
            if (!callable) {
                errors.add(new UndefinedFunctionError(name, callee.getLine(), callee.getColumn()));
            } else {
                scope.markUsed(name);
                int[] arity = knownArities.get(name);
                if (arity != null) {
                    int actual = expr.getArguments().size() + implicitArgs;
                    int min = arity[0], max = arity[1];
                    if (min == max && actual != min) {
                        errors.add(new ArityMismatchError(name, min, actual,
                                callee.getLine(), callee.getColumn()));
                    } else if (min != max && (actual < min || (max >= 0 && actual > max))) {
                        errors.add(new ArityMismatchError(name, min, max, actual,
                                callee.getLine(), callee.getColumn()));
                    }
                }
            }
        } else {
            resolveExpr(expr.getCallee());
        }
        expr.getArguments().forEach(this::resolveExpr);
    }

    private void resolveVarDecl(VarDecl stmt) {
        if (stmt.getInitializer() != null) {
            resolveExpr(stmt.getInitializer());
        }
        if (scope.isDeclaredInCurrentScope(stmt.getName())) {
            errors.add(new DuplicateDeclarationError(stmt.getName(), stmt.line, stmt.nameColumn));
        }
        if (scope.isDeclared(stmt.getName())
                && !scope.isDeclaredInCurrentScope(stmt.getName())
                && !scope.isDeclaredInOutermostScope(stmt.getName())) {
            warn("Variable '" + stmt.getName() + "' shadows an outer declaration",
                    stmt.line, stmt.nameColumn, stmt.getName().length());
        }
        if (stmt.isConst() && stmt.getInitializer() == null) {
            warn("Const '" + stmt.getName() + "' declared without an initializer",
                    stmt.line, stmt.nameColumn, stmt.getName().length());
        }
        scope.declare(stmt.getName(), stmt.line, stmt.nameColumn, stmt.isConst());
    }

    private void resolveFuncDecl(FuncDecl stmt) {
        knownFunctions.add(stmt.getName());
        if (stmt.getVariadicParam() == null) {
            knownArities.put(stmt.getName(), new int[]{stmt.getArity(), stmt.getMaxArity()});
        }
        scope.push();
        stmt.getParameters().forEach(p -> scope.declare(p.name(), stmt.line, 0, false));
        if (stmt.getVariadicParam() != null) {
            scope.declare(stmt.getVariadicParam(), stmt.line, 0, false);
        }
        resolveBody(stmt.getBody());
        popScope();
    }

    private void resolveAssign(Assign stmt) {
        if (stmt.getReference() instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && isIdentifier(d)) {
            String name = d.getValue();
            if (!scope.isDeclared(name)) {
                errors.add(new UndeclaredVariableError(name, d.getLine(), d.getColumn()));
            } else if (scope.isConst(name)) {
                errors.add(new ConstReassignmentError(name, d.getLine(), d.getColumn()));
            } else {
                scope.markUsed(name);
            }
        } else {
            resolveExpr(stmt.getReference());
        }
        resolveExpr(stmt.getExpression());
    }

    private void resolveIf(If stmt) {
        resolveExpr(stmt.getCondition());
        scope.push();
        resolveBody(stmt.getThenBody());
        popScope();
        if (stmt.getElseBody() != null) {
            scope.push();
            resolveBody(stmt.getElseBody());
            popScope();
        }
    }

    private void resolveFor(For stmt) {
        scope.push();
        loopDepth++;
        resolveNodes(stmt.getVarDecls());
        for (Node n : stmt.getVarDecls()) {
            if (n instanceof VarDecl v && v.getInitializer() == null) {
                scope.markUsed(v.getName());
            }
        }
        if (stmt.getCondition() != null) {
            resolveExpr(stmt.getCondition());
        }
        stmt.getPostExpressions().forEach(this::resolveNode);
        resolveBody(stmt.getBody());
        loopDepth--;
        popScope();
    }

    private void resolveWhile(While stmt) {
        resolveExpr(stmt.getCondition());
        scope.push();
        loopDepth++;
        resolveBody(stmt.getBody());
        loopDepth--;
        popScope();
    }

    private void resolveForeach(Foreach stmt) {
        resolveExpr(stmt.getCollection());
        scope.push();
        loopDepth++;
        VarDecl iter = stmt.getIterator();
        scope.declare(iter.getName(), iter.line > 0 ? iter.line : stmt.line, iter.nameColumn, false);
        resolveBody(stmt.getBody());
        loopDepth--;
        popScope();
    }

    private void resolveBlock(Block stmt) {
        scope.push();
        resolveBody(stmt.getBody());
        popScope();
    }

    private void resolveSwitch(Switch stmt) {
        resolveExpr(stmt.getSubject());
        for (var c : stmt.getCases()) {
            scope.push();
            resolveBody(c.getBody());
            popScope();
        }
        if (stmt.getDefaultBody() != null) {
            scope.push();
            resolveBody(stmt.getDefaultBody());
            popScope();
        }
    }

    private void resolveTryCatch(TryCatch stmt) {
        scope.push();
        resolveBody(stmt.getTryBody());
        popScope();
        for (CatchClause clause : stmt.getCatchClauses()) {
            scope.push();
            if (clause.getParamName() != null) {
                scope.declare(clause.getParamName(), stmt.line, 0, false);
            }
            resolveBody(clause.getBody());
            popScope();
        }
    }

    private void resolveVarDestructure(VarDestructure stmt) {
        resolveExpr(stmt.getInitializer());
        for (String name : stmt.getNames()) {
            scope.declare(name, stmt.line, 0, false);
        }
    }

    private void preDeclareImport(ImportExpression expr) {
        if (expr.isSelective()) {
            for (String fn : expr.getSelectedFunctions()) {
                scope.declareImport(fn, expr.line);
                knownFunctions.add(fn);
            }
        } else if (expr.getNamespace() != null) {
            scope.declareImport(expr.getNamespace(), expr.line);
            knownNamespaces.add(expr.getNamespace());
        } else {
            String libName = expr.getModule().replace("\"", "");
            knownFunctions.addAll(LibIndex.getFunctionNames(libName));
            LibIndex.getFunctionArities(libName).forEach((name, arity) -> {
                if (arity >= 0) {
                    knownArities.put(name, new int[]{arity, arity});
                }
            });
        }
    }

    private void popScope() {
        checkUnused(scope.pop());
    }

    private void resolveBody(List<Node> body) {
        boolean terminated = false;
        for (Node node : body) {
            if (terminated) {
                WarningCollector.emit(WarningLevel.WARNING, "Unreachable code", lineOf(node), 0);
                break;
            }
            resolveNode(node);
            if (node instanceof Return || node instanceof Throw) {
                terminated = true;
            }
        }
    }

    private void checkUnused(Map<String, VarInfo> closedScope) {
        for (var entry : closedScope.entrySet()) {
            String name = entry.getKey();
            VarInfo info = entry.getValue();
            if (!info.used() && !name.startsWith("_")) {
                if (info.isImport()) {
                    WarningCollector.emit(WarningLevel.WARNING, "'" + name + "' is imported but never used",
                            info.line(), info.column(), name.length());
                } else if (info.isFunction() && !isModule) {
                    WarningCollector.emit(WarningLevel.HINT, "'" + name + "' is defined but never called",
                            info.line(), info.column(), name.length());
                } else {
                    WarningCollector.emit(WarningLevel.HINT, "'" + name + "' is declared but never used",
                            info.line(), info.column(), name.length());
                }
            }
        }
    }

    private static int lineOf(Node node) {
        return switch (node) {
            case VarDecl s ->
                s.line;
            case FuncDecl s ->
                s.line;
            case Return s ->
                s.line;
            case If s ->
                s.line;
            case For s ->
                s.line;
            case While s ->
                s.line;
            case Foreach s ->
                s.line;
            case Block s ->
                s.line;
            case Switch s ->
                s.line;
            case TryCatch s ->
                s.line;
            case Throw s ->
                s.line;
            case Assign s ->
                s.line;
            case CallExpression e when e.getCallee() instanceof DumbExpression d ->
                d.getLine();
            default ->
                0;
        };
    }

    private void warn(String message, int line, int column, int span) {
        WarningCollector.emit(WarningLevel.WARNING, message, line, column, span);
    }

    private static boolean isStringLiteral(Node n) {
        return n instanceof DumbExpression d && d.getTokenType() == TokenType.STRING_LITERAL;
    }

    private static boolean isNonStringLiteral(Node n) {
        if (!(n instanceof DumbExpression d)) {
            return false;
        }
        return d.getTokenType() != TokenType.STRING_LITERAL && !isIdentifier(d);
    }

    private static boolean isZeroLiteral(Node n) {
        return n instanceof DumbExpression d && "0".equals(d.getValue());
    }

    private static boolean isIdentifier(DumbExpression expr) {
        if (expr.getTokenType() != TokenType.EXPRESSION) {
            return false;
        }
        char first = expr.getValue().charAt(0);
        return Character.isLetter(first) || first == '_';
    }
}
