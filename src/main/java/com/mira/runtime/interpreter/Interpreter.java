package com.mira.runtime.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.Flags;
import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.FieldAccessError;
import com.mira.error.runtime.RuntimeError.ImmutableCollectionError;
import com.mira.error.runtime.RuntimeError.NoModuleDeclarationError;
import com.mira.error.runtime.RuntimeError.NotANamespaceError;
import com.mira.error.runtime.RuntimeError.NotCallableError;
import com.mira.error.runtime.RuntimeError.NotIterableError;
import com.mira.error.runtime.RuntimeError.PostExprNaNError;
import com.mira.error.runtime.RuntimeError.PostUnaryError;
import com.mira.error.runtime.RuntimeError.RangeStepZeroError;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.TypeConversionError;
import com.mira.error.runtime.RuntimeError.UnknownOperatorError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.Mutability;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.ContinueSignal;
import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Object> {

    private static Interpreter instance;
    private static final ThreadLocal<Interpreter> activeInterpreter = new ThreadLocal<>();
    private Environment globalEnvironment = new Environment();
    private Environment localEnvironment;
    private final Map<String, Object> callCache = new HashMap<>();
    private Set<String> pureFunctions = Set.of();

    public Interpreter() {
        ImportResolver.loadInternal(globalEnvironment);
    }

    public static Interpreter getInstance() {
        Interpreter active = activeInterpreter.get();
        if (active != null) return active;
        if (instance == null) {
            instance = new Interpreter();
        }
        return instance;
    }

    private void loadGlobalContext(List<Node> asts, boolean enforceModule) {
        if (globalEnvironment.getSize() > 0) {
            globalEnvironment = new Environment();
        }

        if (enforceModule && !(asts.getFirst() instanceof ModuleDecl)) {
            throw new NoModuleDeclarationError();
        }

        List<ImportExpression> imports = new ArrayList<>();
        for (Node ast : asts) {
            if (ast instanceof ImportExpression importExpression) {
                imports.add(importExpression);
            }
        }
        ImportResolver.resolveImports(imports, globalEnvironment, this, true);

        pureFunctions = PurityAnalyzer.analyze(asts);
        callCache.clear();

        if (Flags.mainFunction) {
            for (Node ast : asts) {
                switch (ast) {
                    case FuncDecl funcDecl -> {
                        funcDecl.getBody().add(new Return(new DumbExpression(new Token(null, "0", 0, 0))));
                        funcDecl.accept(this);
                    }
                    case VarDecl varDecl ->
                        varDecl.accept(this);
                    default -> {
                    }
                }
            }
        }
    }

    private Expression getArgsTuple(String[] args) {
        List<Expression> argsList = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                argsList.add(new DumbExpression(new Token(null, arg, 0, 0)));
            }
        } else {
            return null;
        }

        return new TupleExpression(argsList);
    }

    public <T> T run(List<Node> asts, String[] args, boolean enforceModule) {
        Interpreter prev = activeInterpreter.get();
        activeInterpreter.set(this);
        try {
            loadGlobalContext(asts, enforceModule);
            Object lastResult = null;

            if (Flags.mainFunction) {
                Expression argsTuple = getArgsTuple(args);
                return (T) new CallExpression(new DumbExpression(
                        new Token(null, "main", 0, 0)),
                        argsTuple == null ? List.of() : List.of(argsTuple)).
                        accept(this);
            } else {
                globalEnvironment.define("args", getArgsTuple(args));
                for (Node ast : asts) {
                    lastResult = switch (ast) {
                        case Expression expression ->
                            expression.accept(this);
                        case Statement statement ->
                            statement.accept(this);
                        default -> {
                            throw new AssertionError();
                        }
                    };
                }
            }

            return (T) lastResult;
        } finally {
            activeInterpreter.set(prev);
        }
    }

    public <T> T run(List<Node> asts, boolean enforceModule) {
        Interpreter prev = activeInterpreter.get();
        activeInterpreter.set(this);
        try {
            loadGlobalContext(asts, enforceModule);
            Object lastResult = null;

            if (Flags.mainFunction) {
                return (T) new CallExpression(new DumbExpression(new Token(null, "main", 0, 0)), new ArrayList<>()).accept(this);
            } else {
                for (Node ast : asts) {
                    lastResult = switch (ast) {
                        case Expression expression ->
                            expression.accept(this);
                        case Statement statement ->
                            statement.accept(this);
                        default -> {
                            throw new AssertionError();
                        }
                    };
                }
            }

            return (T) lastResult;
        } finally {
            activeInterpreter.set(prev);
        }
    }

    public <T> T runWithoutLoadingNewContext(List<Node> asts) {
        Interpreter prev = activeInterpreter.get();
        activeInterpreter.set(this);
        try {
            Object lastResult = null;

            if (Flags.mainFunction) {
                return (T) new CallExpression(new DumbExpression(new Token(null, "main", 0, 0)), new ArrayList<>()).accept(this);
            } else {
                for (Node ast : asts) {
                    lastResult = switch (ast) {
                        case Expression expression ->
                            expression.accept(this);
                        case Statement statement ->
                            statement.accept(this);
                        default -> {
                            throw new AssertionError();
                        }
                    };
                }
            }

            return (T) lastResult;
        } finally {
            activeInterpreter.set(prev);
        }
    }

    @Override
    public Void visitVarDecl(VarDecl varDecl) {
        Object value = NullValue.INSTANCE;

        if (varDecl.getInitializer() != null) {
            value = varDecl.getInitializer().accept(this);
        }

        Environment env = localEnvironment == null ? globalEnvironment : localEnvironment;

        if (localEnvironment != null) {
            boolean shadowsLocal = localEnvironment.getParent() != null
                    && localEnvironment.getParent().existsInChain(varDecl.getName());
            boolean shadowsGlobal = globalEnvironment.existsInChain(varDecl.getName());
            if (shadowsLocal || shadowsGlobal) {
                WarningCollector.emit(WarningLevel.WARNING,
                        "Variable '" + varDecl.getName() + "' shadows an outer variable");
            }
        }

        if (varDecl.isConst()) {
            env.defineConst(varDecl.getName(), value);
        } else {
            env.define(varDecl.getName(), value);
        }

        return null;
    }

    @Override
    public <T> T visitDumbExpr(DumbExpression expression) {
        String value = expression.getValue();
        if (value.equals("true")) {
            return (T) Boolean.TRUE;
        }
        if (value.equals("false")) {
            return (T) Boolean.FALSE;
        }
        if (value.equals("null")) {
            return (T) NullValue.INSTANCE;
        }
        return (T) value;
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        String calleeName = (String) expression.getCallee().accept(this);

        Object callee;
        if (localEnvironment != null) {
            Object local = localEnvironment.getOrNull(calleeName);
            callee = local != null ? local : globalEnvironment.get(calleeName);
        } else {
            callee = globalEnvironment.get(calleeName);
        }

        if (!(callee instanceof Callable callable)) {
            throw new NotCallableError(calleeName);
        }

        List<Object> arguments = new ArrayList<>();

        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            throw new ArgMismatchError(calleeName, callable.getArity(), arguments.size());
        }

        if (pureFunctions.contains(calleeName)) {
            String cacheKey = calleeName + arguments;
            Object cached = callCache.get(cacheKey);
            if (cached != null) {
                return (T) cached;
            }
            Object result = callable.call(this, arguments);
            if (result != null) {
                callCache.put(cacheKey, result);
            }
            return (T) result;
        }

        return (T) callable.call(this, arguments);
    }

    @Override
    public Void visitFuncDecl(FuncDecl funcDecl) {
        globalEnvironment.define(funcDecl.getName(),
                new Function(localEnvironment,
                        funcDecl.getBody(),
                        funcDecl.getParameters(),
                        funcDecl.getArity(),
                        funcDecl.getVariadicParam()));

        return null;
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression lambda) {
        Environment capturedEnv = localEnvironment != null ? localEnvironment : globalEnvironment;
        return (T) new Function(capturedEnv, lambda.getBody(), lambda.getParameters(), lambda.getArity(), lambda.getVariadicParam());
    }

    @Override
    public Void visitReturn(Return ret) {
        Object value = null;

        if (ret.getValue() != null) {
            value = ret.getValue().accept(this);
        }

        throw new ReturnSignal(value);
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        List<Expression> expressions = expression.getExpressions();

        if (expressions.size() == 3
                && expressions.get(1) instanceof UnaryExpression unary
                && isComparisonOperator(unary.getOperation().getLexeme())) {

            Object left = expressions.get(0).accept(this);
            String op = unary.getOperation().getLexeme();
            Object right = expressions.get(2).accept(this);

            return (T) evaluateComparison(left, op, right);
        }

        if (expressions.size() == 3
                && expressions.get(1) instanceof UnaryExpression unary
                && isLogicalOperator(unary.getOperation().getLexeme())) {

            Object leftRaw = expressions.get(0).accept(this);
            String op = unary.getOperation().getLexeme();
            Object rightRaw = expressions.get(2).accept(this);

            boolean left = resolveBoolean(leftRaw);
            boolean right = resolveBoolean(rightRaw);

            return (T) Boolean.valueOf(switch (op) {
                case "&&" ->
                    left && right;
                case "||" ->
                    left || right;
                default ->
                    throw new UnknownOperatorError(op);
            });
        }

        Object arithmetic = tryEvaluateArithmetic(expressions);
        if (arithmetic != null) {
            return (T) arithmetic;
        }

        return (T) evaluateAsString(expressions);
    }

    @Override
    public <T> T visitBinaryExpr(BinaryExpression expression) {
        Object left = expression.getLeft().accept(this);
        Object right = expression.getRight().accept(this);
        String op = expression.getOperator().getLexeme();

        return (T) switch (op) {
            case "+" -> {
                try {
                    yield toNumber(left) + toNumber(right);
                } catch (NumberFormatException e) {
                    boolean leftIsStr = left instanceof String;
                    boolean rightIsStr = right instanceof String;
                    if (leftIsStr != rightIsStr) {
                        WarningCollector.emit(WarningLevel.HINT,
                                "Implicit string concatenation: mixed String and non-String operands",
                                expression.getOperator());
                    }
                    yield String.valueOf(left) + String.valueOf(right);
                }
            }
            case "-" ->
                toNumber(left) - toNumber(right);
            case "*" ->
                toNumber(left) * toNumber(right);
            case "/" -> {
                double divisor = toNumber(right);
                if (divisor == 0) {
                    WarningCollector.emit(WarningLevel.WARNING,
                            "Division by zero", expression.getOperator());
                }
                yield toNumber(left) / divisor;
            }
            case "%" ->
                toNumber(left) % toNumber(right);
            case "&" ->
                (double) ((long) toNumber(left) & (long) toNumber(right));
            case "|" ->
                (double) ((long) toNumber(left) | (long) toNumber(right));
            case "^" ->
                (double) ((long) toNumber(left) ^ (long) toNumber(right));
            case "<<" ->
                (double) ((long) toNumber(left) << (long) toNumber(right));
            case ">>" ->
                (double) ((long) toNumber(left) >> (long) toNumber(right));
            case "==" ->
                evaluateComparison(left, "==", right);
            case "!=" ->
                evaluateComparison(left, "!=", right);
            case "<" ->
                evaluateComparison(left, "<", right);
            case ">" ->
                evaluateComparison(left, ">", right);
            case "<=" ->
                evaluateComparison(left, "<=", right);
            case ">=" ->
                evaluateComparison(left, ">=", right);
            case "&&" ->
                resolveBoolean(left) && resolveBoolean(right);
            case "||" ->
                resolveBoolean(left) || resolveBoolean(right);
            default ->
                throw new UnknownOperatorError(op);
        };
    }

    private double toNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            return Double.parseDouble(s);
        }
        throw new TypeConversionError(value);
    }

    private Object tryEvaluateArithmetic(List<Expression> expressions) {
        List<Double> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        for (int i = 0; i < expressions.size(); i++) {
            if (i % 2 == 0) {
                Object val = expressions.get(i).accept(this);
                if (val instanceof Double d) {
                    operands.add(d);
                } else {
                    try {
                        operands.add(Double.valueOf(String.valueOf(val)));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else {
                if (!(expressions.get(i) instanceof UnaryExpression unary)) {
                    return null;
                }
                String op = unary.getOperation().getLexeme();
                if (!op.equals("+") && !op.equals("-") && !op.equals("*") && !op.equals("/")) {
                    return null;
                }
                operators.add(op);
            }
        }

        int i = 0;
        while (i < operators.size()) {
            String op = operators.get(i);
            if (op.equals("*") || op.equals("/")) {
                double left = operands.get(i);
                double right = operands.get(i + 1);
                double result = op.equals("*") ? left * right : left / right;
                operands.set(i, result);
                operands.remove(i + 1);
                operators.remove(i);
            } else {
                i++;
            }
        }

        double result = operands.get(0);
        for (int j = 0; j < operators.size(); j++) {
            double right = operands.get(j + 1);
            result = switch (operators.get(j)) {
                case "+" ->
                    result + right;
                case "-" ->
                    result - right;
                default ->
                    throw new UnknownOperatorError(operators.get(j));
            };
        }

        return result;
    }

    private String evaluateAsString(List<Expression> expressions) {
        StringBuilder builder = new StringBuilder();

        Object first = expressions.get(0).accept(this);
        if (expressions.get(0) instanceof ComplexExpression) {
            builder.append("(").append(first).append(")");
        } else {
            builder.append(first);
        }

        int i = 1;
        while (i < expressions.size()) {
            Expression expr = expressions.get(i);

            if (!(expr instanceof UnaryExpression unary)) {
                builder.append(expr.accept(this));
                i++;
                continue;
            }

            String operator = unary.getOperation().getLexeme();

            if (operator.equals("++") || operator.equals("--") || operator.equals("$")) {
                builder.append(unary.accept(this));
                i++;
                continue;
            }

            Object right = unary.getRight() != null ? unary.accept(this) : null;

            if (right == null) {
                builder.append(operator);
                i++;
                continue;
            }

            switch (operator) {
                case "+", "-", "*", "/" ->
                    builder.append(operator).append(right);
                default ->
                    throw new UnknownOperatorError("Unknown operator: " + operator);
            }

            i++;
        }

        return builder.toString();
    }

    private boolean isComparisonOperator(String op) {
        return switch (op) {
            case "==", "!=", "<", ">", "<=", ">=" ->
                true;
            default ->
                false;
        };
    }

    private boolean isLogicalOperator(String op) {
        return op.equals("&&") || op.equals("||");
    }

    private Boolean evaluateComparison(Object leftObj, String op, Object rightObj) {
        if (leftObj instanceof Number ln && rightObj instanceof Number rn) {
            double l = ln.doubleValue(), r = rn.doubleValue();
            return switch (op) {
                case "==" ->
                    l == r;
                case "!=" ->
                    l != r;
                case "<" ->
                    l < r;
                case ">" ->
                    l > r;
                case "<=" ->
                    l <= r;
                case ">=" ->
                    l >= r;
                default ->
                    throw new UnknownOperatorError(op);
            };
        }
        String left = String.valueOf(leftObj);
        String right = String.valueOf(rightObj);
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case "==" ->
                    l == r;
                case "!=" ->
                    l != r;
                case "<" ->
                    l < r;
                case ">" ->
                    l > r;
                case "<=" ->
                    l <= r;
                case ">=" ->
                    l >= r;
                default ->
                    throw new UnknownOperatorError(op);
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==" ->
                    left.equals(right);
                case "!=" ->
                    !left.equals(right);
                case "<" ->
                    left.compareTo(right) < 0;
                case ">" ->
                    left.compareTo(right) > 0;
                case "<=" ->
                    left.compareTo(right) <= 0;
                case ">=" ->
                    left.compareTo(right) >= 0;
                default ->
                    throw new UnknownOperatorError(op);
            };
        }
    }

    private boolean resolveBoolean(Object value) {
        return switch (value) {
            case Boolean b ->
                b;
            case NullValue n ->
                false;
            case Double d ->
                d != 0;
            case String s -> {
                if (s.equals("true")) {
                    yield true;
                }
                if (s.equals("false")) {
                    yield false;
                }
                try {
                    yield Double.parseDouble(s) != 0;
                } catch (NumberFormatException e) {
                    throw new AssertionError("Cannot resolve boolean from string: " + s);
                }
            }
            default ->
                throw new AssertionError("Cannot resolve boolean from: " + value.getClass());
        };
    }

    @Override
    public <T> T visitTernaryExpr(TernaryExpression expression) {
        Object condition = expression.getCondition().accept(this);
        if (resolveLoopCondition(condition)) {
            return (T) expression.getThenExpr().accept(this);
        } else {
            return (T) expression.getElseExpr().accept(this);
        }
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        String operator = expression.getOperation().getLexeme();

        switch (operator) {
            case "$" -> {
                String name = (String) expression.getRight().accept(this);

                if (localEnvironment != null) {
                    Object val = localEnvironment.getOrNull(name);
                    if (val != null) {
                        return (T) val;
                    }
                }

                return (T) globalEnvironment.get(name);
            }

            case "-" -> {
                Object right = expression.getRight() != null
                        ? expression.getRight().accept(this)
                        : null;

                if (right == null) {
                    return (T) "-";
                } else {
                    return (T) ("-" + right);
                }
            }

            case "!" -> {
                Object right = expression.getRight().accept(this);
                boolean val = resolveBoolean(right);
                return (T) Boolean.valueOf(!val);
            }

            case "++" -> {
                if (!(expression.getRight() instanceof UnaryExpression varExpr)
                        || !varExpr.getOperation().getLexeme().equals("$")) {
                    throw new PostUnaryError("++");
                }

                String name = (String) varExpr.getRight().accept(this);

                Environment env = (localEnvironment != null && localEnvironment.getOrNull(name) != null)
                        ? localEnvironment
                        : globalEnvironment;

                Object raw = env.get(name);
                double val;
                if (raw instanceof Double d) {
                    val = d;
                } else {
                    try {
                        val = Double.parseDouble(String.valueOf(raw));
                    } catch (NumberFormatException e) {
                        throw new PostExprNaNError(name);
                    }
                }
                Double newValue = val + 1;
                env.assign(name, newValue);
                return (T) newValue;
            }

            case "--" -> {
                if (!(expression.getRight() instanceof UnaryExpression varExpr)
                        || !varExpr.getOperation().getLexeme().equals("$")) {
                    throw new PostUnaryError("--");
                }

                String name = (String) varExpr.getRight().accept(this);

                Environment env = (localEnvironment != null && localEnvironment.getOrNull(name) != null)
                        ? localEnvironment
                        : globalEnvironment;

                Object raw = env.get(name);
                double val;
                if (raw instanceof Double d) {
                    val = d;
                } else {
                    try {
                        val = Double.parseDouble(String.valueOf(raw));
                    } catch (NumberFormatException e) {
                        throw new PostExprNaNError(name);
                    }
                }
                Double newValue = val - 1;
                env.assign(name, newValue);
                return (T) newValue;
            }

            case "~" -> {
                Object right = expression.getRight().accept(this);
                return (T) Double.valueOf(~(long) toNumber(right));
            }

            default ->
                throw new UnknownOperatorError(operator);
        }
    }

    @Override
    public <T> T visitTupleExpr(TupleExpression expression) {
        return (T) expression;
    }

    @Override
    public <T> T visitListExpr(ListExpression expression) {
        return (T) expression;
    }

    @Override
    public <T> T visitMapExpr(MapExpression expression) {
        return (T) expression;
    }

    @Override
    public <T> T visitAccessExpr(AccessExpression expression) {
        Object accessedObject = expression.getReference().accept(this);

        for (Expression index : expression.getIndecies()) {
            Object object = index.accept(this);

            switch (accessedObject) {
                case MapExpression map -> {
                    String key = String.valueOf(object);
                    Expression val = map.getEntries().get(key);
                    if (val == null) {
                        throw new RuntimeException("Map key not found: " + key);
                    }
                    accessedObject = val.accept(this);
                }
                default -> {
                    int i;
                    switch (object) {
                        case String s -> i = Integer.parseInt(s);
                        case Double d -> i = (int) d.doubleValue();
                        default -> throw new AssertionError();
                    }
                    switch (accessedObject) {
                        case TupleExpression tuple -> {
                            if (tuple.getMembers().get(i) instanceof TupleExpression innerTuple) {
                                accessedObject = innerTuple.accept(this);
                            } else {
                                accessedObject = tuple.getMembers().get(i);
                            }
                        }
                        case ListExpression list -> {
                            if (list.getMembers().get(i) instanceof ListExpression innerList) {
                                accessedObject = innerList.accept(this);
                            } else {
                                accessedObject = list.getMembers().get(i);
                            }
                        }
                        default -> throw new NotIterableError();
                    }
                }
            }
        }

        return (T) accessedObject;
    }

    @Override
    public Void visitAssign(Assign assign) {
        switch (assign.getReference()) {
            case AccessExpression accessExpression -> {
                Object referencedObject = accessExpression.getReference().accept(this);
                for (int k = 0; k < accessExpression.getIndecies().size() - 1; k++) {
                    Object object = accessExpression.getIndecies().get(k).accept(this);
                    int i;

                    switch (object) {
                        case String s -> {
                            i = Integer.parseInt(s);
                        }
                        case Double d -> {
                            i = (int) d.doubleValue();
                        }
                        default ->
                            throw new AssertionError();
                    }

                    switch (referencedObject) {
                        case TupleExpression tuple -> {
                            referencedObject = tuple.getMembers().get(i);
                            if (referencedObject instanceof ListExpression innerList) {
                                referencedObject = innerList.accept(this);
                            } else {
                                throw new ImmutableCollectionError();
                            }
                        }
                        case ListExpression list -> {
                            referencedObject = list.getMembers().get(i);
                            if (referencedObject instanceof ListExpression innerList) {
                                referencedObject = innerList.accept(this);
                            } else {
                                throw new ImmutableCollectionError();
                            }
                        }
                        default ->
                            throw new AssertionError("Reference is not a type of collection!");
                    }
                }

                if (referencedObject instanceof Mutability mutability) {
                    if (mutability.isMutable()) {
                        Expression assignment;
                        if (assign.getExpression() instanceof CallExpression callExpression) {
                            assignment = new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(callExpression.accept(this)), 0, 0));
                        } else {
                            assignment = assign.getExpression();
                        }

                        switch (referencedObject) {
                            case ListExpression list -> {
                                list.getMembers().set(Integer.parseInt((String) accessExpression.getIndecies().getLast().accept(this)),
                                        assignment);
                            }
                            case MapExpression map -> {
                                String key = String.valueOf(accessExpression.getIndecies().getLast().accept(this));
                                map.getEntries().put(key, assignment);
                            }
                            default ->
                                throw new ImmutableCollectionError();
                        }
                    } else {
                        throw new ImmutableCollectionError();
                    }
                } else {
                    throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
                }
            }
            case FieldAccessExpression fieldAccessExpression -> {
                Object object = fieldAccessExpression.getObject().accept(this);
                if (!(object instanceof Environment objectEnv)) {
                    throw new FieldAccessError(fieldAccessExpression.getField());
                }
                Object value = assign.getExpression().accept(this);
                objectEnv.assign(fieldAccessExpression.getField(), value);
            }
            default -> {
                if (assign.getReference() instanceof UnaryExpression unaryExpression) {
                    String name = String.valueOf(unaryExpression.getRight().accept(this));
                    Object expression = assign.getExpression().accept(this);

                    if (localEnvironment != null && localEnvironment.existsInChain(name)) {
                        localEnvironment.assign(name, expression);
                    } else {
                        globalEnvironment.assign(name, expression);
                    }
                } else {
                    throw new AssertionError();
                }
            }
        }

        return null;
    }

    @Override
    public Object visitIf(If stmt) {
        Object condition = stmt.getCondition().accept(this);
        boolean value = resolveLoopCondition(condition);
        List<Node> body = value ? stmt.getThenBody() : stmt.getElseBody();

        if (body == null) {
            return null;
        }

        runBody(body);

        return null;
    }

    @Override
    public Object visitFor(For stmt) {
        runBody(stmt.getVarDecls());

        try {
            while (true) {
                if (stmt.getCondition() != null) {
                    Object condition = stmt.getCondition().accept(this);
                    if (!resolveLoopCondition(condition)) {
                        return null;
                    }
                }

                try {
                    runBodyInFreshScope(stmt.getBody());
                } catch (ContinueSignal continueSignal) {
                }

                runBody(stmt.getPostExpressions());
            }
        } catch (BreakSignal breakSignal) {
            return null;
        }
    }

    @Override
    public Object visitWhile(While stmt) {
        try {
            while (true) {
                Object condition = stmt.getCondition().accept(this);
                if (!resolveLoopCondition(condition)) {
                    return null;
                }

                try {
                    runBodyInFreshScope(stmt.getBody());
                } catch (ContinueSignal continueSignal) {
                }
            }
        } catch (BreakSignal breakSignal) {
            return null;
        }
    }

    private boolean resolveLoopCondition(Object condition) {
        return switch (condition) {
            case Boolean b ->
                b;
            case NullValue n ->
                false;
            case Double d ->
                d != 0;
            case String s -> {
                if (s.equals("true")) {
                    yield true;
                }
                if (s.equals("false")) {
                    yield false;
                }
                try {
                    yield Double.parseDouble(s) != 0;
                } catch (NumberFormatException e) {
                    throw new AssertionError("Cannot resolve loop condition from string: " + s);
                }
            }
            default ->
                throw new AssertionError("Unexpected condition type: " + condition.getClass());
        };
    }

    @Override
    public Object visitBreak(Break stmt) {
        throw new BreakSignal();
    }

    @Override
    public Object visitContinue(Continue stmt) {
        throw new ContinueSignal();
    }

    @Override
    public Object visitBlock(Block stmt) {
        Environment previous = localEnvironment;
        localEnvironment = new Environment(previous);

        runBody(stmt.getBody());

        localEnvironment = previous;
        return null;
    }

    @Override
    public Object visitOverwrite(Overwrite stmt) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();

        List<Node> asts = parser.parseTokens(tokenizer.tokenize(stmt.getStmt(), false));
        Environment.setOverwriteMode(true);
        for (Node ast : asts) {
            switch (ast) {
                case Expression expression ->
                    expression.accept(this);
                case Statement statement ->
                    statement.accept(this);
                default -> {
                    throw new AssertionError();
                }
            }
        }
        Environment.setOverwriteMode(false);

        return null;
    }

    @Override
    public Object visitForeach(Foreach stmt) {
        if (localEnvironment != null && !localEnvironment.exists(stmt.getIterator().getName())) {
            localEnvironment.define(stmt.getIterator().getName(), null);
        } else if (localEnvironment == null && !globalEnvironment.exists(stmt.getIterator().getName())) {
            globalEnvironment.define(stmt.getIterator().getName(), null);
        }

        String iteratorName = stmt.getIterator().getName();

        try {
            if (stmt.getCollection() instanceof RangeExpression range) {
                double start = Double.parseDouble(String.valueOf(range.getStart().accept(this)));
                double end = Double.parseDouble(String.valueOf(range.getEnd().accept(this)));
                double step = range.getStepsize() != null
                        ? Double.parseDouble((String) range.getStepsize().accept(this))
                        : 1.0;

                if (step == 0) {
                    throw new RangeStepZeroError();
                }

                for (double i = start; step > 0 ? i < end : i > end; i += step) {
                    assignIterator(iteratorName, i);
                    try {
                        runBodyInFreshScope(stmt.getBody());
                    } catch (ContinueSignal continueSignal) {
                    }
                }
                return null;
            }

            Object iterable = stmt.getCollection().accept(this);

            switch (iterable) {
                case TupleExpression tuple -> {
                    for (Expression expr : tuple.getMembers()) {
                        Object value = expr.accept(this);
                        assignIterator(iteratorName, value);
                        try {
                            runBody(stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                case ListExpression list -> {
                    for (Expression expr : list.getMembers()) {
                        Object value = expr.accept(this);
                        assignIterator(iteratorName, value);
                        try {
                            runBody(stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                case String string -> {
                    for (char ch : string.toCharArray()) {
                        assignIterator(iteratorName, String.valueOf(ch));
                        try {
                            runBody(stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                default ->
                    throw new NotIterableError();
            }
        } catch (BreakSignal breakSignal) {
            return null;
        }

        return null;
    }

    private void assignIterator(String name, Object value) {
        if (localEnvironment != null) {
            if (localEnvironment.exists(name)) {
                localEnvironment.assign(name, value);
            } else {
                throw new AssertionError();
            }
        } else {
            if (globalEnvironment.exists(name)) {
                globalEnvironment.assign(name, value);
            } else {
                throw new AssertionError();
            }
        }
    }

    @Override
    public Object visitSwitch(Switch stmt) {
        Object subject = stmt.getSubject().accept(this);

        for (SwitchCase switchCase : stmt.getCases()) {
            Object caseValue = switchCase.getValue().accept(this);
            if (valuesEqual(subject, caseValue)) {
                runBody(switchCase.getBody());
                return null;
            }
        }

        if (stmt.getDefaultBody() != null) {
            runBody(stmt.getDefaultBody());
        }

        return null;
    }

    @Override
    public Object visitThrow(Throw stmt) {
        Object value = stmt.getValue().accept(this);
        throw new ThrowSignal(value);
    }

    @Override
    public Object visitTryCatch(TryCatch stmt) {
        try {
            runBodyInFreshScope(stmt.getTryBody());
        } catch (ThrowSignal signal) {
            Environment previous = localEnvironment;
            localEnvironment = new Environment(previous != null ? previous : globalEnvironment);
            localEnvironment.define(stmt.getCatchParam(), signal.getValue());
            try {
                runBody(stmt.getCatchBody());
            } finally {
                localEnvironment = previous;
            }
        }
        return null;
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Double da && b instanceof Double db) {
            return da.compareTo(db) == 0;
        }
        return a.equals(b);
    }

    private void runBody(List<Node> body) {
        for (Node node : body) {
            switch (node) {
                case Expression expression ->
                    expression.accept(this);
                case Statement statement ->
                    statement.accept(this);
                default -> {
                    throw new AssertionError();
                }
            }
        }
    }

    private void runBodyInFreshScope(List<Node> body) {
        Environment previous = localEnvironment;
        localEnvironment = new Environment(previous != null ? previous : globalEnvironment);
        try {
            runBody(body);
        } finally {
            localEnvironment = previous;
        }
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        Object namespaceObj = globalEnvironment.get(expression.getAlias());

        if (!(namespaceObj instanceof Namespace namespace)) {
            throw new NotANamespaceError(expression.getAlias());
        }

        Object callee = namespace.get(expression.getFunctionName());

        if (!(callee instanceof Callable callable)) {
            throw new NotCallableError(expression.getAlias() + "." + expression.getFunctionName());
        }

        List<Object> arguments = new ArrayList<>();
        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            throw new ArgMismatchError(expression.getFunctionName(), callable.getArity(), arguments.size());
        }

        return (T) callable.call(this, arguments);
    }

    @Override
    public <T> T visitRangeExpression(RangeExpression expression) {
        double start = Double.parseDouble((String) expression.getStart().accept(this));
        double end = Double.parseDouble((String) expression.getEnd().accept(this));
        double step = expression.getStepsize() != null
                ? Double.parseDouble((String) expression.getStepsize().accept(this))
                : 1.0;

        if (step == 0) {
            throw new RuntimeException("Range stepsize cannot be zero");
        }

        List<Expression> members = new ArrayList<>();
        for (double i = start; step > 0 ? i < end : i > end; i += step) {
            members.add(new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(i), 0, 0)));
        }

        return (T) new ListExpression(members);
    }

    public void loadASTIntoGlobalContext(Node ast) {
        loadASTIntoContext(ast, globalEnvironment);
    }

    public void loadASTIntoContext(Node ast, Environment targetEnv) {
        Environment previous = localEnvironment;
        localEnvironment = null;

        Environment previousGlobal = globalEnvironment;
        if (targetEnv instanceof Namespace) {
            globalEnvironment = targetEnv;
        }

        switch (ast) {
            case Expression expression ->
                expression.accept(this);
            case Statement statement ->
                statement.accept(this);
            default ->
                throw new AssertionError();
        }

        globalEnvironment = previousGlobal;
        localEnvironment = previous;
    }

    @Override
    public <T> T visitObjectExpression(ObjectExpression expression) {
        Environment objectEnv = new Environment();

        for (VarDecl field : expression.getVarDecls()) {
            Object value = field.getInitializer() != null
                    ? field.getInitializer().accept(this)
                    : null;

            if (field.isConst()) {
                objectEnv.defineConst(field.getName(), value);
            } else {
                objectEnv.define(field.getName(), value);
            }
        }

        return (T) objectEnv;
    }

    @Override
    public <T> T visitFieldAccessExpression(FieldAccessExpression expression) {
        Object object = expression.getObject().accept(this);

        if (object instanceof String name) {
            object = localEnvironment != null && localEnvironment.exists(name)
                    ? localEnvironment.get(name)
                    : globalEnvironment.get(name);
        }

        if (!(object instanceof Environment objectEnv)) {
            throw new FieldAccessError(expression.getField());
        }

        return (T) objectEnv.get(expression.getField());
    }

    @Override
    public Object visitEnum(EnumDecl stmt) {
        Environment enumEnv = new Environment(null, stmt.getValues().size());
        for (Map.Entry<String, Object> entry : stmt.getValues().entrySet()) {
            enumEnv.defineConst(entry.getKey(), entry.getValue());
        }
        globalEnvironment.defineConst(stmt.getIdentifier(), enumEnv);
        return null;
    }

    public Environment getLocalEnvironment() {
        return localEnvironment;
    }

    public void setLocalEnvironment(Environment localEnvironment) {
        this.localEnvironment = localEnvironment;
    }

    public Environment getGlobalEnvironment() {
        return globalEnvironment;
    }
}
