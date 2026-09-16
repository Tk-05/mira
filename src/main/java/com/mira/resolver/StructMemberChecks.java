package com.mira.resolver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.error.resolver.StaticCheckError.FieldAccessOnNonObjectError;
import com.mira.error.resolver.StaticCheckError.ImmutableCollectionStaticError;
import com.mira.error.resolver.StaticCheckError.StructFieldTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.UndefinedObjectFieldStaticError;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.TypeAnnotation;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import static com.mira.resolver.StaticCheckSupport.addChildrenNoFunctions;
import static com.mira.resolver.StaticCheckSupport.expressionColumn;
import static com.mira.resolver.StaticCheckSupport.expressionSpan;
import static com.mira.resolver.StaticCheckSupport.extractVarRef;
import static com.mira.resolver.StaticCheckSupport.isIdentifier;
import static com.mira.resolver.StaticCheckSupport.memberExists;

/**
 * Struct/object method- and field-existence and field-assignment-type checks,
 * plus the call-site-propagated parameter-field-access check
 * ({@code walkFuncWithParamTypes}). Delegates back to {@link StaticCheck} for
 * literal-base/type resolution (`resolveLiteralBase`, `resolveRhsLiteralType`,
 * `resolveTypeAnnotation`, `checkAssignable`, `checkArgumentTypes`) and its
 * shared state (`errors`, `userFuncDecls`, `scope`) - those stay centralized
 * there since nearly every other check in the file also depends on them.
 */
final class StructMemberChecks {

    private final StaticCheck owner;

    StructMemberChecks(StaticCheck owner) {
        this.owner = owner;
    }

    void checkMethodArgumentTypes(MethodCallExpression e) {
        Node literalBase = owner.resolveLiteralBase(e.getObject());
        List<FuncDecl> methods;
        if (literalBase instanceof StructExpression structExpr) {
            methods = structExpr.getMethods();
        } else if (literalBase instanceof ObjectExpression objExpr) {
            methods = objExpr.getMethods();
        } else {
            return;
        }
        String methodName = e.getMethod();
        FuncDecl method = methods.stream().filter(m -> methodName.equals(m.getName())).findFirst().orElse(null);
        DumbExpression varRef = extractVarRef(e.getObject());
        int fallbackLine = varRef != null ? varRef.getLine() : e.line;
        int fallbackColumn = varRef != null ? varRef.getColumn() + varRef.getValue().length() + 1 : 0;
        if (method == null) {
            if (!e.isOptional() && !memberExists(literalBase, methodName)) {
                String objectName = varRef != null
                        ? varRef.getValue()
                        : literalBase instanceof StructExpression ? "struct" : "object";
                owner.errors
                        .add(new UndefinedObjectFieldStaticError(methodName, objectName, fallbackLine, fallbackColumn));
            }
            return;
        }
        if (!e.getArguments().isEmpty()) {
            owner.checkArgumentTypes(methodName, method.getParameters(), e.getArguments(), fallbackLine,
                    fallbackColumn);
        }
    }

    void checkFieldAssignment(Expression reference, Expression rhsValue) {
        DumbExpression rootRef = null;
        if (reference instanceof AccessExpression ae) {
            rootRef = extractVarRef(ae.getReference());
        } else if (reference instanceof FieldAccessExpression fae) {
            rootRef = extractVarRef(fae.getObject());
        }
        if (rootRef != null && owner.scope.isDeclared(rootRef.getValue()) && owner.scope.isConst(rootRef.getValue())) {
            owner.errors.add(
                    new ImmutableCollectionStaticError(rootRef.getValue(), rootRef.getLine(), rootRef.getColumn()));
        }
        if (reference instanceof FieldAccessExpression fae) {
            checkFieldAssignmentType(fae, rhsValue);
        }
    }

    private void checkFieldAssignmentType(FieldAccessExpression fae, Expression rhsValue) {
        Node literalBase = owner.resolveLiteralBase(fae.getObject());
        String field = fae.getField();
        com.mira.parser.nodes.statement.Statement.VarDecl fieldDecl;
        if (literalBase instanceof StructExpression structExpr) {
            fieldDecl = structExpr.getVarDecls().stream().filter(v -> field.equals(v.getName())).findFirst()
                    .orElse(null);
        } else if (literalBase instanceof ObjectExpression objExpr) {
            fieldDecl = objExpr.getVarDecls().stream().filter(v -> field.equals(v.getName())).findFirst().orElse(null);
        } else {
            return;
        }
        if (fieldDecl == null || fieldDecl.getType() == null) {
            return;
        }
        TypeAnnotation expected = owner.resolveTypeAnnotation(fieldDecl.getType());
        DumbExpression varRef = extractVarRef(fae.getObject());
        String fieldOwner = varRef != null ? varRef.getValue() : "object";
        int line = varRef != null ? varRef.getLine() : fae.getObject().line;
        int column = expressionColumn(rhsValue, varRef != null ? varRef.getColumn() : 0);
        int span = expressionSpan(rhsValue, field.length());
        owner.checkAssignable(rhsValue, expected, (exp, actual) -> owner.errors
                .add(new StructFieldTypeMismatchError(field, fieldOwner, exp, actual, line, column, span)));
    }

    void checkCallParamFieldAccesses(FuncDecl fn, List<Expression> args) {
        walkFuncWithParamTypes(fn, args, Map.of(), new HashSet<>(), 0, 0);
    }

    void walkFuncWithParamTypes(FuncDecl fn, List<Expression> args, Map<String, Node> callerParamTypes,
            Set<String> visited, int callSiteLine, int callSiteCol) {
        if (visited.contains(fn.getName())) {
            return;
        }
        List<com.mira.parser.nodes.Parameter> params = fn.getParameters();
        Map<String, Node> paramTypes = new HashMap<>();
        for (int i = 0; i < Math.min(params.size(), args.size()); i++) {
            Node type = resolveTypeWithParams(args.get(i), callerParamTypes);
            if (type != null) {
                paramTypes.put(params.get(i).name(), type);
            }
        }
        if (paramTypes.isEmpty()) {
            return;
        }

        Set<String> nextVisited = new HashSet<>(visited);
        nextVisited.add(fn.getName());

        Deque<Node> queue = new ArrayDeque<>(fn.getBody());
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof FuncDecl || n instanceof LambdaExpression) {
                continue;
            }
            if (n instanceof FieldAccessExpression fae && !fae.isOptional()) {
                DumbExpression varRef = extractVarRef(fae.getObject());
                if (varRef != null && paramTypes.containsKey(varRef.getValue())) {
                    Node type = paramTypes.get(varRef.getValue());
                    String field = fae.getField();
                    int errLine = callSiteLine > 0 ? callSiteLine : varRef.getLine();
                    int errCol = callSiteLine > 0 ? callSiteCol : varRef.getColumn() + varRef.getValue().length() + 1;
                    switch (type) {
                        case ObjectExpression objExpr -> {
                            boolean exists = objExpr.getVarDecls().stream().anyMatch(v -> field.equals(v.getName()))
                                    || objExpr.getMethods().stream().anyMatch(m -> field.equals(m.getName()));
                            if (!exists) {
                                owner.errors.add(
                                        new UndefinedObjectFieldStaticError(field, varRef.getValue(), errLine, errCol));
                            }
                        }
                        case StructExpression structExpr -> {
                            boolean exists = structExpr.getVarDecls().stream().anyMatch(v -> field.equals(v.getName()))
                                    || structExpr.getMethods().stream().anyMatch(m -> field.equals(m.getName()));
                            if (!exists) {
                                owner.errors.add(
                                        new UndefinedObjectFieldStaticError(field, varRef.getValue(), errLine, errCol));
                            }
                        }
                        default -> {
                            String typeName = type instanceof ListExpression
                                    ? "list"
                                    : type instanceof ArrayExpression
                                            ? "array"
                                            : type instanceof MapExpression ? "map" : "non-object value";
                            owner.errors.add(new FieldAccessOnNonObjectError(field, typeName, errLine, errCol));
                        }
                    }
                }
                queue.add(fae.getObject());
            } else if (n instanceof MethodCallExpression mce && !mce.isOptional()) {
                DumbExpression varRef = extractVarRef(mce.getObject());
                if (varRef != null && paramTypes.containsKey(varRef.getValue())) {
                    Node type = paramTypes.get(varRef.getValue());
                    if ((type instanceof ObjectExpression || type instanceof StructExpression)
                            && !memberExists(type, mce.getMethod())) {
                        int errLine = callSiteLine > 0 ? callSiteLine : varRef.getLine();
                        int errCol = callSiteLine > 0
                                ? callSiteCol
                                : varRef.getColumn() + varRef.getValue().length() + 1;
                        owner.errors.add(new UndefinedObjectFieldStaticError(mce.getMethod(), varRef.getValue(),
                                errLine, errCol));
                    }
                }
                queue.add(mce.getObject());
                queue.addAll(mce.getArguments());
            } else {
                if (n instanceof CallExpression ce && ce.getCallee() instanceof DumbExpression callee
                        && isIdentifier(callee)) {
                    FuncDecl calledFn = owner.userFuncDecls.get(callee.getValue());
                    if (calledFn != null && !ce.getArguments().isEmpty()) {
                        walkFuncWithParamTypes(calledFn, ce.getArguments(), paramTypes, nextVisited, callSiteLine,
                                callSiteCol);
                    }
                }
                addChildrenNoFunctions(n, queue);
            }
        }
    }

    private Node resolveTypeWithParams(Expression arg, Map<String, Node> paramTypes) {
        DumbExpression varRef = extractVarRef(arg);
        if (varRef != null) {
            Node fromParams = paramTypes.get(varRef.getValue());
            if (fromParams != null) {
                return fromParams;
            }
        }
        return owner.resolveRhsLiteralType(arg);
    }
}
