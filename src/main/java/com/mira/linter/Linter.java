package com.mira.linter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.lexer.token.TokenType;
import com.mira.lib.LibIndex;
import com.mira.linter.LintScope.VarInfo;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
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
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class Linter {

    private final LintScope scope = new LintScope();

    private final Map<String, Integer> knownFunctions = new HashMap<>(LibIndex.GLOBAL_ARITIES);

    public void lint(List<Node> ast) {
        scope.push();

        for (String builtin : LibIndex.GLOBAL_NAMES) {
            scope.declare(builtin, 0, 0, false);
            scope.markUsed(builtin);
        }

        for (Node node : ast) {
            switch (node) {
                case FuncDecl f -> {
                    scope.declare(f.getName(), f.line, 0, false);
                    scope.markUsed(f.getName());
                    knownFunctions.put(f.getName(), f.getArity());
                }
                case VarDecl v ->
                    scope.declare(v.getName(), v.line, 0, v.isConst());
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

        lintNodes(ast);

        checkUnused(scope.pop());
    }

    private void lintNodes(List<Node> nodes) {
        for (Node node : nodes) {
            lintNode(node);
        }
    }

    private void lintNode(Node node) {
        switch (node) {
            case VarDecl stmt ->
                lintVarDecl(stmt);
            case FuncDecl stmt ->
                lintFuncDecl(stmt);
            case Assign stmt ->
                lintAssign(stmt);
            case Overwrite stmt ->
                lintOverwrite(stmt);
            case Return stmt ->
                lintReturn(stmt);
            case If stmt ->
                lintIf(stmt);
            case For stmt ->
                lintFor(stmt);
            case While stmt ->
                lintWhile(stmt);
            case Foreach stmt ->
                lintForeach(stmt);
            case Block stmt ->
                lintBlock(stmt);
            case Switch stmt ->
                lintSwitch(stmt);
            case TryCatch stmt ->
                lintTryCatch(stmt);
            case Throw stmt ->
                lintThrow(stmt);
            case EnumDecl stmt ->
                lintEnum(stmt);
            case VarDestructure stmt ->
                lintVarDestructure(stmt);
            case CallExpression e ->
                lintCallExpression(e);
            default ->
                lintExpr(node);
        }
    }

    private void lintExpr(Node node) {
        switch (node) {
            case DumbExpression e ->
                lintIdentifier(e);
            case BinaryExpression e when "|>".equals(e.getOperator().getLexeme()) -> {
                lintExpr(e.getLeft());
                if (e.getRight() instanceof CallExpression call) {
                    lintCallExpression(call, 1);
                } else {
                    lintExpr(e.getRight());
                }
            }
            case BinaryExpression e -> {
                lintExpr(e.getLeft());
                lintExpr(e.getRight());
            }
            case UnaryExpression e when "$".equals(e.getOperation().getLexeme()) -> {
                if (e.getRight() instanceof DumbExpression d && isIdentifier(d)) {
                    String name = d.getValue();
                    if (!scope.isDeclared(name)) {
                        hint("Use of undeclared variable '$" + name + "'",
                                d.getLine(), d.getColumn());
                    }
                    scope.markUsed(name);
                } else if (e.getRight() != null) {
                    lintExpr(e.getRight());
                }
            }
            case UnaryExpression e -> {
                if (e.getRight() != null) {
                    lintExpr(e.getRight());
                }
            }
            case CallExpression e ->
                lintCallExpression(e);
            case AccessExpression e -> {
                lintExpr(e.getReference());
                e.getIndecies().forEach(this::lintExpr);
            }
            case FieldAccessExpression e ->
                lintExpr(e.getObject());
            case MethodCallExpression e -> {
                lintExpr(e.getObject());
                e.getArguments().forEach(this::lintExpr);
            }
            case ArrayExpression e ->
                e.getMembers().forEach(this::lintExpr);
            case ListExpression e ->
                e.getMembers().forEach(this::lintExpr);
            case MapExpression e ->
                e.getEntries().values().forEach(this::lintExpr);
            case ObjectExpression e -> {
                e.getVarDecls().stream()
                        .filter(v -> v.getInitializer() != null)
                        .forEach(v -> lintExpr(v.getInitializer()));
                for (var method : e.getMethods()) {
                    scope.push();
                    method.getParameters().forEach(p -> scope.declare(p.name(), 0, 0, false));
                    if (method.getVariadicParam() != null) {
                        scope.declare(method.getVariadicParam(), 0, 0, false);
                    }
                    scope.declare("this", 0, 0, false);
                    scope.markUsed("this");
                    e.getVarDecls().forEach(f -> {
                        scope.declare(f.getName(), 0, 0, false);
                        scope.markUsed(f.getName());
                    });
                    lintBodyWithDeadCodeCheck(method.getBody());
                    checkUnused(scope.pop());
                }
            }
            case LambdaExpression e ->
                lintLambda(e);
            case TernaryExpression e -> {
                lintExpr(e.getCondition());
                lintExpr(e.getThenExpr());
                lintExpr(e.getElseExpr());
            }
            case ComplexExpression e ->
                e.getExpressions().forEach(this::lintExpr);
            case TypeofExpression e ->
                lintExpr(e.getExpr());
            case SwitchExpression e -> {
                lintExpr(e.getSubject());
                for (var c : e.getCases()) {
                    lintExpr(c.value());
                    lintExpr(c.result());
                }
                if (e.getDefaultExpr() != null) {
                    lintExpr(e.getDefaultExpr());
                }
            }
            case RangeExpression e -> {
                if (e.getStart() != null) {
                    lintExpr(e.getStart());
                }
                if (e.getEnd() != null) {
                    lintExpr(e.getEnd());
                }
                if (e.getStepsize() != null) {
                    lintExpr(e.getStepsize());
                }
            }
            case NamespaceCallExpression e ->
                e.getArguments().forEach(this::lintExpr);
            case ImportExpression e ->
                preDeclareImport(e);
            default -> {
            }
        }
    }

    private void lintIdentifier(DumbExpression expr) {
        if (!isIdentifier(expr)) {
            return;
        }
        scope.markUsed(expr.getValue());
    }

    private void lintCallExpression(CallExpression expr) {
        lintCallExpression(expr, 0);
    }

    private void lintCallExpression(CallExpression expr, int implicitArgs) {
        lintExpr(expr.getCallee());
        expr.getArguments().forEach(this::lintExpr);

        if (!(expr.getCallee() instanceof DumbExpression callee) || !isIdentifier(callee)) {
            return;
        }
        String name = callee.getValue();
        Integer expectedArity = knownFunctions.get(name);
        if (expectedArity == null || expectedArity == -1) {
            return;
        }
        int actual = expr.getArguments().size() + implicitArgs;
        if (actual != expectedArity) {
            warn("'" + name + "' expects " + expectedArity
                    + " argument(s) but was called with " + actual,
                    callee.getLine(), callee.getColumn());
        }
    }

    private void lintLambda(LambdaExpression expr) {
        scope.push();
        expr.getParameters().forEach(p -> scope.declare(p.name(), 0, 0, false));
        lintBodyWithDeadCodeCheck(expr.getBody());
        checkUnused(scope.pop());
    }

    private void lintVarDecl(VarDecl stmt) {
        if (stmt.getInitializer() != null) {
            lintExpr(stmt.getInitializer());
        }

        if (scope.isDeclared(stmt.getName()) && !scope.isDeclaredInCurrentScope(stmt.getName())
                && !scope.isDeclaredInOutermostScope(stmt.getName())) {
            warn("Variable '" + stmt.getName() + "' shadows an outer declaration", stmt.line, 0);
        }

        if (stmt.isConst() && stmt.getInitializer() == null) {
            warn("Const '" + stmt.getName() + "' declared without an initializer", stmt.line, 0);
        }

        scope.declare(stmt.getName(), stmt.line, 0, stmt.isConst());
    }

    private void lintFuncDecl(FuncDecl stmt) {
        knownFunctions.put(stmt.getName(), stmt.getArity());

        scope.push();
        for (var p : stmt.getParameters()) {
            scope.declare(p.name(), stmt.line, 0, false);
        }
        if (stmt.getVariadicParam() != null) {
            scope.declare(stmt.getVariadicParam(), stmt.line, 0, false);
        }
        lintBodyWithDeadCodeCheck(stmt.getBody());
        checkUnused(scope.pop());
    }

    private void lintVarDestructure(VarDestructure stmt) {
        lintExpr(stmt.getInitializer());
        for (String name : stmt.getNames()) {
            scope.declare(name, stmt.line, 0, false);
        }
    }

    private void lintAssign(Assign stmt) {
        lintExpr(stmt.getReference());
        lintExpr(stmt.getExpression());
    }

    private void lintOverwrite(Overwrite stmt) {
        String name = stmt.getStmt();
        if (scope.isConst(name)) {
            warn("Cannot reassign const '" + name + "'", stmt.line, 0);
        }
        if (!scope.isDeclared(name)) {
            warn("Assignment to undeclared variable '" + name + "'", stmt.line, 0);
        }
    }

    private void lintReturn(Return stmt) {
        if (stmt.getValue() != null) {
            lintExpr(stmt.getValue());
        }
    }

    private void lintIf(If stmt) {
        lintExpr(stmt.getCondition());

        scope.push();
        lintNodes(stmt.getThenBody());
        checkUnused(scope.pop());

        if (stmt.getElseBody() != null) {
            scope.push();
            lintNodes(stmt.getElseBody());
            checkUnused(scope.pop());
        }
    }

    private void lintFor(For stmt) {
        scope.push();
        lintNodes(stmt.getVarDecls());
        for (Node n : stmt.getVarDecls()) {
            if (n instanceof VarDecl v && v.getInitializer() == null) {
                scope.markUsed(v.getName());
            }
        }
        if (stmt.getCondition() != null) {
            lintExpr(stmt.getCondition());
        }
        stmt.getPostExpressions().forEach(this::lintNode);
        lintBodyWithDeadCodeCheck(stmt.getBody());
        checkUnused(scope.pop());
    }

    private void lintWhile(While stmt) {
        lintExpr(stmt.getCondition());
        scope.push();
        lintBodyWithDeadCodeCheck(stmt.getBody());
        checkUnused(scope.pop());
    }

    private void lintForeach(Foreach stmt) {
        lintExpr(stmt.getCollection());
        scope.push();
        scope.declare(stmt.getIterator().getName(), stmt.line, 0, false);
        lintBodyWithDeadCodeCheck(stmt.getBody());
        checkUnused(scope.pop());
    }

    private void lintBlock(Block stmt) {
        scope.push();
        lintBodyWithDeadCodeCheck(stmt.getBody());
        checkUnused(scope.pop());
    }

    private void lintBodyWithDeadCodeCheck(List<Node> body) {
        boolean terminated = false;
        for (Node node : body) {
            if (terminated) {
                warn("Unreachable code", lineOf(node), 0);
                break;
            }
            lintNode(node);
            if (node instanceof Return || node instanceof Throw) {
                terminated = true;
            }
        }
    }

    private void lintSwitch(Switch stmt) {
        lintExpr(stmt.getSubject());
        for (var c : stmt.getCases()) {
            scope.push();
            lintBodyWithDeadCodeCheck(c.getBody());
            checkUnused(scope.pop());
        }
        if (stmt.getDefaultBody() != null) {
            scope.push();
            lintBodyWithDeadCodeCheck(stmt.getDefaultBody());
            checkUnused(scope.pop());
        }
    }

    private void lintTryCatch(TryCatch stmt) {
        scope.push();
        lintBodyWithDeadCodeCheck(stmt.getTryBody());
        checkUnused(scope.pop());

        for (CatchClause clause : stmt.getCatchClauses()) {
            scope.push();
            if (clause.getParamName() != null) {
                scope.declare(clause.getParamName(), stmt.line, 0, false);
            }
            lintBodyWithDeadCodeCheck(clause.getBody());
            checkUnused(scope.pop());
        }
    }

    private void lintThrow(Throw stmt) {
        lintExpr(stmt.getValue());
    }

    private void lintEnum(EnumDecl stmt) {

    }

    private void preDeclareImport(ImportExpression expr) {
        if (expr.isSelective()) {
            for (String fn : expr.getSelectedFunctions()) {
                scope.declare(fn, 0, 0, false);
                scope.markUsed(fn);
            }
        } else if (expr.getNamespace() != null) {
            scope.declare(expr.getNamespace(), 0, 0, false);
            scope.markUsed(expr.getNamespace());
        }
    }

    private void checkUnused(Map<String, VarInfo> closedScope) {
        for (var entry : closedScope.entrySet()) {
            String name = entry.getKey();
            VarInfo info = entry.getValue();
            if (!info.used() && !name.startsWith("_")) {
                hint("'" + name + "' is declared but never used", info.line(), info.column());
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
            case Overwrite s ->
                s.line;
            case CallExpression e when e.getCallee() instanceof DumbExpression d ->
                d.getLine();
            default ->
                0;
        };
    }

    private static boolean isIdentifier(DumbExpression expr) {
        if (expr.getTokenType() != TokenType.EXPRESSION) {
            return false;
        }
        char first = expr.getValue().charAt(0);
        return Character.isLetter(first) || first == '_';
    }

    private static void warn(String message, int line, int column) {
        WarningCollector.emit(WarningLevel.WARNING, message, line, column);
    }

    private static void hint(String message, int line, int column) {
        WarningCollector.emit(WarningLevel.HINT, message, line, column);
    }
}
