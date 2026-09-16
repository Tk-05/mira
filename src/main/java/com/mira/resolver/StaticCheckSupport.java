package com.mira.resolver;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import com.mira.lexer.token.TokenType;
import com.mira.lib.NativeType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.TypeAnnotation;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

/**
 * Pure, state-free helpers used throughout {@link StaticCheck} - literal/type
 * predicates, AST tree-walk utilities, and small value records. Every member
 * here takes all its inputs as parameters and touches no {@code StaticCheck}
 * instance state, so it's split out purely to keep that file's actual stateful
 * checks readable. Package-private: {@code StaticCheck} pulls these in with a
 * wildcard static import.
 */
final class StaticCheckSupport {

    private StaticCheckSupport() {
    }

    static final Set<String> STRING_UNSAFE_OPERATORS = Set.of("-", "*", "%", "\\%", "**", "&", "|", "^", "<<", ">>");

    static final Set<String> ARITHMETIC_TYPE_CHECKED_OPERATORS = Set.of("+", "-", "*", "/", "%", "\\%", "**");

    // "==" / "!=" are deliberately excluded: comparing an explicitly-typed value
    // against e.g. a nullable's `null` check is a common, legitimate pattern this
    // check must not flag - only ordering comparisons are unambiguously nonsensical
    // across mismatched named types.
    static final Set<String> COMPARISON_TYPE_CHECKED_OPERATORS = Set.of("<", ">", "<=", ">=");

    record NullCheckNarrowing(List<String> thenNarrowedVars, List<String> elseNarrowedVars) {

    }

    static final NullCheckNarrowing NO_NARROWING = new NullCheckNarrowing(List.of(), List.of());

    record NarrowSave(String varName, TypeAnnotation previous) {

    }

    record OperandTypes(TypeAnnotation left, TypeAnnotation right) {

    }

    static final TypeAnnotation ANY = new TypeAnnotation(NativeType.ANY.miraTypeName(), false);
    static final TypeAnnotation NUMBER = new TypeAnnotation(NativeType.NUMBER.miraTypeName(), false);
    static final TypeAnnotation STRING = new TypeAnnotation(NativeType.STRING.miraTypeName(), false);
    static final TypeAnnotation BOOL = new TypeAnnotation(NativeType.BOOL.miraTypeName(), false);
    static final TypeAnnotation LIST = new TypeAnnotation(NativeType.LIST.miraTypeName(), false);
    static final TypeAnnotation ARRAY = new TypeAnnotation(NativeType.ARRAY.miraTypeName(), false);
    static final TypeAnnotation MAP = new TypeAnnotation(NativeType.MAP.miraTypeName(), false);
    static final TypeAnnotation OBJECT = new TypeAnnotation(NativeType.OBJECT.miraTypeName(), false);
    static final TypeAnnotation VOID = new TypeAnnotation(NativeType.VOID.miraTypeName(), false);
    static final TypeAnnotation FN = new TypeAnnotation("Fn", false);
    static final TypeAnnotation NULL = new TypeAnnotation("Null", false);

    static TypeAnnotation namedType(String name) {
        return new TypeAnnotation(name, false);
    }

    static TypeAnnotation nullableOf(TypeAnnotation inner) {
        return new TypeAnnotation(inner.name(), true, inner.line(), inner.column(), inner.paramTypes(),
                inner.returnType());
    }

    static TypeAnnotation withoutNullable(TypeAnnotation t) {
        return new TypeAnnotation(t.name(), false, t.line(), t.column(), t.paramTypes(), t.returnType());
    }

    static TypeAnnotation functionType(List<TypeAnnotation> params, TypeAnnotation returnType) {
        return new TypeAnnotation("Fn", false, 0, 0, params, returnType);
    }

    static boolean isVoid(TypeAnnotation type) {
        return type != null && "Void".equals(type.name());
    }

    static boolean isAssignable(TypeAnnotation from, TypeAnnotation to) {
        if ("Any".equals(from.name()) || "Any".equals(to.name())) {
            return true;
        }
        if (to.nullable()) {
            if (!from.isFunctionType() && "Null".equals(from.name())) {
                return true;
            }
            TypeAnnotation toInner = withoutNullable(to);
            return from.nullable() ? isAssignable(withoutNullable(from), toInner) : isAssignable(from, toInner);
        }
        if (from.nullable()) {
            // a possibly-null value can't flow into a non-nullable target
            return false;
        }
        // a value of some specific function shape always fits the plain "Fn"
        // type, and a plain "Fn" value fits any specific shape too - it's an
        // unknown-shaped function, not a wrong-shaped one, so don't guess wrong
        if (from.isFunctionType() && !to.isFunctionType() && "Fn".equals(to.name())) {
            return true;
        }
        if (to.isFunctionType() && !from.isFunctionType() && "Fn".equals(from.name())) {
            return true;
        }
        if (from.isFunctionType() && to.isFunctionType()) {
            return isFunctionAssignable(from, to);
        }
        if (!from.isFunctionType() && !to.isFunctionType()) {
            return from.name().equals(to.name());
        }
        return false;
    }

    private static boolean isFunctionAssignable(TypeAnnotation from, TypeAnnotation to) {
        List<TypeAnnotation> fromParams = from.paramTypes();
        List<TypeAnnotation> toParams = to.paramTypes();
        if (fromParams.size() != toParams.size()) {
            return false;
        }
        for (int i = 0; i < fromParams.size(); i++) {
            TypeAnnotation a = fromParams.get(i);
            TypeAnnotation b = toParams.get(i);
            if (!isAssignable(a, b) && !isAssignable(b, a)) {
                return false;
            }
        }
        return isAssignable(from.returnType(), to.returnType()) || isAssignable(to.returnType(), from.returnType());
    }

    static void addChildren(Node node, Deque<Node> queue) {
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
            case Throw s -> queue.add(s.getValue());
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
                    queue.add(s.getCollection());
                } else {
                    queue.addAll(s.getVarDecls());
                    if (s.getCondition() != null) {
                        queue.add(s.getCondition());

                    }
                }
                queue.addAll(s.getBody());
            }
            case Block s -> queue.addAll(s.getBody());
            case TryCatch s -> {
                queue.addAll(s.getTryBody());
                s.getCatchClauses().forEach(c -> queue.addAll(c.getBody()));
            }
            case Lock s -> {
                queue.add(s.getMutex());
                queue.addAll(s.getBody());
            }
            case ComptimeBlock s -> queue.addAll(s.getBody());
            case BinaryExpression e -> {
                queue.add(e.getLeft());
                queue.add(e.getRight());
            }
            case UnaryExpression e -> queue.add(e.getRight());
            case CallExpression e -> {
                queue.add(e.getCallee());
                queue.addAll(e.getArguments());
            }
            case NamespaceCallExpression e -> queue.addAll(e.getArguments());
            case AccessExpression e -> {
                queue.add(e.getReference());
                queue.addAll(e.getIndecies());
            }
            case FieldAccessExpression e -> queue.add(e.getObject());
            case MethodCallExpression e -> {
                queue.add(e.getObject());
                queue.addAll(e.getArguments());
            }
            case TernaryExpression e -> {
                queue.add(e.getCondition());
                queue.add(e.getThenExpr());
                queue.add(e.getElseExpr());
            }
            case ArrayExpression e -> queue.addAll(e.getMembers());
            case ListExpression e -> queue.addAll(e.getMembers());
            case ComplexExpression e -> queue.addAll(e.getExpressions());
            case LambdaExpression e -> queue.addAll(e.getBody());
            case ExecBlock e -> queue.addAll(e.getBody());
            default -> {
            }
        }
    }

    static void addChildrenNoFunctions(Node node, Deque<Node> queue) {
        if (node instanceof FuncDecl || node instanceof LambdaExpression) {
            return;
        }
        addChildren(node, queue);
    }

    static DumbExpression extractVarRef(Node expr) {
        if (expr instanceof UnaryExpression u && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d && isIdentifier(d)) {
            return d;
        }
        return null;
    }

    static int lineOf(Node node) {
        return switch (node) {
            case VarDecl s -> s.line;
            case FuncDecl s -> s.line;
            case Return s -> s.line;
            case If s -> s.line;
            case Loop s -> s.line;
            case While s -> s.line;
            case Block s -> s.line;
            case Switch s -> s.line;
            case TryCatch s -> s.line;
            case Throw s -> s.line;
            case Assign s -> s.line;
            case CallExpression e when e.getCallee() instanceof DumbExpression d -> d.getLine();
            default -> 0;
        };
    }

    static int columnOf(Node node) {
        return switch (node) {
            case VarDecl s -> s.column;
            case FuncDecl s -> s.column;
            case Return s -> s.column;
            case If s -> s.column;
            case Loop s -> s.column;
            case While s -> s.column;
            case Block s -> s.column;
            case Switch s -> s.column;
            case TryCatch s -> s.column;
            case Throw s -> s.column;
            case Assign s -> s.column;
            case CallExpression e when e.getCallee() instanceof DumbExpression d -> d.getColumn();
            default -> 0;
        };
    }

    static int spanOf(Node node) {
        return switch (node) {
            case VarDecl s -> s.getName().length();
            case FuncDecl s -> s.getName().length();
            case Return ignored -> "return".length();
            case Throw ignored -> "throw".length();
            case If ignored -> "if".length();
            case Loop ignored -> "for".length();
            case While ignored -> "while".length();
            case Switch ignored -> "switch".length();
            case TryCatch ignored -> "try".length();
            case CallExpression e when e.getCallee() instanceof DumbExpression d -> d.getValue().length();
            default -> 1;
        };
    }

    static int expressionColumn(Expression expr, int fallback) {
        if (expr instanceof DumbExpression d) {
            return d.getColumn();
        }
        if (expr instanceof UnaryExpression u) {
            return u.getOperation().getColumn();
        }
        return fallback;
    }

    static int expressionSpan(Expression expr, int fallback) {
        if (expr instanceof DumbExpression d) {
            return Math.max(1, d.getValue().length());
        }
        if (expr instanceof UnaryExpression u && u.getRight() instanceof DumbExpression d) {
            return 1 + Math.max(1, d.getValue().length());
        }
        return Math.max(1, fallback);
    }

    static java.nio.file.Path resolveModuleFile(java.nio.file.Path base, String rawPath) {
        java.nio.file.Path modulePath = base.resolve(rawPath).normalize();
        if (!java.nio.file.Files.exists(modulePath) && !com.mira.cli.Flags.dependencyRoots.isEmpty()) {
            java.nio.file.Path candidate = java.nio.file.Paths.get(rawPath);
            for (java.nio.file.Path depRoot : com.mira.cli.Flags.dependencyRoots) {
                java.nio.file.Path depCandidate = depRoot.resolve(candidate).normalize();
                if (java.nio.file.Files.exists(depCandidate)) {
                    return depCandidate;
                }
            }
        }
        return modulePath;
    }

    static List<String> union(List<String> a, List<String> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        List<String> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
    }

    static String nullCheckVarName(Expression left, Expression right) {
        if (isNullLiteral(right)) {
            DumbExpression ref = extractVarRef(left);
            return ref != null ? ref.getValue() : null;
        }
        if (isNullLiteral(left)) {
            DumbExpression ref = extractVarRef(right);
            return ref != null ? ref.getValue() : null;
        }
        return null;
    }

    static boolean isNullLiteral(Node n) {
        return n instanceof DumbExpression d && "null".equals(d.getValue());
    }

    static boolean isStringLiteral(Node n) {
        return n instanceof DumbExpression d && d.getTokenType() == TokenType.STRING_LITERAL;
    }

    static boolean isNonStringLiteral(Node n) {
        if (!(n instanceof DumbExpression d)) {
            return false;
        }
        return d.getTokenType() != TokenType.STRING_LITERAL && !isIdentifier(d);
    }

    static boolean isZeroLiteral(Node n) {
        return n instanceof DumbExpression d && "0".equals(d.getValue());
    }

    static boolean isIdentifier(DumbExpression expr) {
        if (expr.getTokenType() != TokenType.EXPRESSION) {
            return false;
        }
        char first = expr.getValue().charAt(0);
        return Character.isLetter(first) || first == '_';
    }

    /**
     * A bare {@code return;} isn't represented as a {@code null} value in the AST -
     * {@code Parser.parseReturn} fills in a synthetic {@code 0.0} literal token at
     * line/column {@code -1} so downstream code always has an {@code Expression} to
     * work with. This tells that sentinel apart from a real, user-written return
     * value (including a genuine {@code return 0.0;}).
     */
    static boolean isBareReturn(Expression value) {
        return value instanceof DumbExpression d && d.getLine() == -1;
    }

    static boolean isKnownLiteral(Node n) {
        return n instanceof ListExpression || n instanceof ArrayExpression || n instanceof MapExpression
                || n instanceof ObjectExpression || n instanceof StructExpression || n instanceof LambdaExpression
                || (n instanceof DumbExpression d && !isIdentifier(d))
                // `-1`/`~1`/`!true` are each a UnaryExpression wrapping the literal
                // token, not themselves a DumbExpression - without this, e.g. `var x :
                // -1;` was invisible to every varLiteralTypes-based check (E332 calling
                // it, or the division-by-zero/bareword warnings elsewhere)
                || isInvertedLiteral(n);
    }

    static boolean isInvertedLiteral(Node n) {
        return n instanceof UnaryExpression u && u.getRight() instanceof DumbExpression d
                && switch (u.getOperation().getLexeme()) {
                    case "-", "~" -> isNumericLiteralToken(d);
                    case "!" -> isBooleanLiteralToken(d);
                    default -> false;
                };
    }

    static boolean isNumericLiteralToken(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL || isIdentifier(d)) {
            return false;
        }
        String value = d.getValue();
        return !value.isEmpty() && Character.isDigit(value.charAt(0));
    }

    static boolean isBooleanLiteralToken(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL || isIdentifier(d)) {
            return false;
        }
        return "true".equals(d.getValue()) || "false".equals(d.getValue());
    }

    static boolean isNonNumericLiteral(Node n) {
        if (n instanceof ListExpression || n instanceof ArrayExpression || n instanceof MapExpression
                || n instanceof ObjectExpression) {
            return true;
        }
        return n instanceof DumbExpression d && d.getTokenType() == TokenType.STRING_LITERAL;
    }

    static boolean isNonIterableLiteral(Node n) {
        if (!(n instanceof DumbExpression d)) {
            return false;
        }
        return !isIdentifier(d);
    }

    static boolean sameNamedType(TypeAnnotation a, TypeAnnotation b) {
        return !a.isFunctionType() && !b.isFunctionType() && a.name().equals(b.name());
    }

    static boolean isNumberType(TypeAnnotation t) {
        return "Number".equals(t.name());
    }

    static boolean isStringType(TypeAnnotation t) {
        return "String".equals(t.name());
    }

    static boolean memberExists(Node literalBase, String name) {
        if (literalBase instanceof ObjectExpression objExpr) {
            return objExpr.getVarDecls().stream().anyMatch(v -> name.equals(v.getName()))
                    || objExpr.getMethods().stream().anyMatch(m -> name.equals(m.getName()));
        }
        if (literalBase instanceof StructExpression structExpr) {
            return structExpr.getVarDecls().stream().anyMatch(v -> name.equals(v.getName()))
                    || structExpr.getMethods().stream().anyMatch(m -> name.equals(m.getName()));
        }
        return false;
    }

    static boolean alwaysReturns(List<Node> body) {
        for (Node node : body) {
            switch (node) {
                case Return ignored -> {
                    return true;
                }
                case Throw ignored -> {
                    return true;
                }
                case If ifStmt -> {
                    if (ifStmt.getElseBody() != null && alwaysReturns(ifStmt.getThenBody())
                            && alwaysReturns(ifStmt.getElseBody())) {
                        return true;
                    }
                }
                case Switch sw -> {
                    if (sw.getDefaultBody() != null && sw.getCases().stream().allMatch(c -> alwaysReturns(c.getBody()))
                            && alwaysReturns(sw.getDefaultBody())) {
                        return true;
                    }
                }
                case TryCatch tc -> {
                    if (alwaysReturns(tc.getTryBody()) && !tc.getCatchClauses().isEmpty()
                            && tc.getCatchClauses().stream().allMatch(c -> alwaysReturns(c.getBody()))) {
                        return true;
                    }
                }
                case Block b -> {
                    if (alwaysReturns(b.getBody())) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    static boolean hasAnyReturn(List<Node> body) {
        for (Node node : body) {
            switch (node) {
                case Return ignored -> {
                    return true;
                }
                case If ifStmt -> {
                    if (hasAnyReturn(ifStmt.getThenBody())) {
                        return true;
                    }
                    if (ifStmt.getElseBody() != null && hasAnyReturn(ifStmt.getElseBody())) {
                        return true;
                    }
                }
                case While w -> {
                    if (hasAnyReturn(w.getBody())) {
                        return true;

                    }
                }
                case Loop l -> {
                    if (hasAnyReturn(l.getBody())) {
                        return true;

                    }
                }
                case Block b -> {
                    if (hasAnyReturn(b.getBody())) {
                        return true;

                    }
                }
                case Switch sw -> {
                    if (sw.getCases().stream().anyMatch(c -> hasAnyReturn(c.getBody()))) {
                        return true;
                    }
                    if (sw.getDefaultBody() != null && hasAnyReturn(sw.getDefaultBody())) {
                        return true;
                    }
                }
                case TryCatch tc -> {
                    if (hasAnyReturn(tc.getTryBody())) {
                        return true;
                    }
                    if (tc.getCatchClauses().stream().anyMatch(c -> hasAnyReturn(c.getBody()))) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }
}
