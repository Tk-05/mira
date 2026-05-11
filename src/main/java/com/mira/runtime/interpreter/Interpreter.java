package com.mira.runtime.interpreter;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.mira.Flags;
import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.FieldAccessError;
import com.mira.error.runtime.RuntimeError.ImmutableCollectionError;
import com.mira.error.runtime.RuntimeError.LocalCallableError;
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
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
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
import com.mira.parser.nodes.expression.Expression.Mutability;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.ContinueSignal;
import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.functions.Promise;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.runtime.values.MutexValue;
import com.mira.runtime.values.NullValue;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;
import com.mira.vocabulary.Vocabulary;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Object> {

    private static Interpreter instance;
    private static final ThreadLocal<Interpreter> activeInterpreter = new ThreadLocal<>();

    private record CacheKey(String name, List<Object> args) {

    }

    public interface DebugHook {

        void onStatement(Statement stmt, Environment env);
    }

    private Environment globalEnvironment = new Environment();
    private Environment localEnvironment;
    private final Map<CacheKey, Object> callCache = new HashMap<>();
    private Set<String> pureFunctions = Set.of();
    private final Deque<String> miraCallStack = new ArrayDeque<>();
    private DebugHook debugHook;

    public Interpreter() {
        ImportResolver.loadInternal(globalEnvironment);
    }

    public static Interpreter getInstance() {
        Interpreter active = activeInterpreter.get();
        if (active != null) {
            return active;
        }
        if (instance == null) {
            instance = new Interpreter();
        }
        return instance;
    }

    public Set<String> getPureFunctions() {
        return pureFunctions;
    }

    public Map<?, ?> getCallCache() {
        return callCache;
    }

    public Interpreter fork() {
        Interpreter forked = new Interpreter();
        forked.globalEnvironment = this.globalEnvironment;
        forked.pureFunctions = this.pureFunctions;
        return forked;
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

        Set<String> pure = PurityAnalyzer.analyze(asts);
        for (Node ast : asts) {
            if (ast instanceof FuncDecl fd && fd.isPure()) {
                pure.add(fd.getName());
            }
        }
        pureFunctions = pure;
        callCache.clear();

        for (Node ast : asts) {
            switch (ast) {
                case FuncDecl funcDecl when !(globalEnvironment.getOrNull(funcDecl.getName()) instanceof Namespace) -> {
                    if (Flags.mainFunction) {
                        funcDecl.getBody().add(new Return(new DumbExpression(new Token(null, "0", 0, 0))));
                    }
                    funcDecl.accept(this);
                }
                case EnumDecl enumDecl when !(globalEnvironment.getOrNull(enumDecl.getIdentifier()) instanceof Namespace) ->
                    enumDecl.accept(this);
                case VarDecl varDecl when varDecl.isConst() && !(globalEnvironment.getOrNull(varDecl.getName()) instanceof Namespace) ->
                    varDecl.accept(this);
                default -> {
                }
            }
        }

        if (Flags.mainFunction) {
            for (Node ast : asts) {
                if (ast instanceof VarDecl varDecl && !varDecl.isConst()) {
                    varDecl.accept(this);
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

        return new ArrayExpression(argsList);
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
                    if (isHoisted(ast)) {
                        continue;
                    }
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
                    if (isHoisted(ast)) {
                        continue;
                    }
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

    public void loadASTIntoGlobalContext(Node ast) {
        loadASTIntoContext(ast, globalEnvironment);
    }

    public void loadASTIntoContext(Node ast, Environment targetEnv) {
        Environment previous = localEnvironment;
        Environment previousGlobal = globalEnvironment;
        if (targetEnv instanceof Namespace) {
            globalEnvironment = targetEnv;
            localEnvironment = targetEnv;
        } else {
            localEnvironment = null;
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

    public void setDebugHook(DebugHook hook) {
        this.debugHook = hook;
    }

    private void notifyDebugger(Statement stmt) {
        if (debugHook != null) {
            debugHook.onStatement(stmt, localEnvironment != null ? localEnvironment : globalEnvironment);
        }
    }

    public void dumpState(Throwable cause, PrintStream out) {
        out.println("\n=== MIRA CRASH DUMP ===");
        out.println("Cause: " + cause);
        out.println();

        out.println("--- Mira Call Stack ---");
        if (miraCallStack.isEmpty()) {
            out.println("  <top level>");
        } else {
            for (String frame : miraCallStack) {
                out.println("  at " + frame + "()");
            }
        }
        out.println();

        if (Flags.crashDumpFull) {
            out.println("--- Java Stack Trace ---");
            cause.printStackTrace(out);
            out.println();
        }

        out.println("--- Memory Dump ---");
        Environment env = localEnvironment != null ? localEnvironment : globalEnvironment;
        int depth = 0;
        while (env != null) {
            String label = (env.getParent() == null) ? "global" : "scope[" + depth + "]";
            out.println("  [" + label + "]");
            for (String key : env.keySet()) {
                Object val = env.get(key);
                String repr = formatValue(val);
                out.println("    " + key + " = " + repr);
            }
            env = env.getParent();
            depth++;
        }
        out.println("=== END CRASH DUMP ===");
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
        if (expression.getTokenType() == TokenType.EXPRESSION
                && !value.isEmpty() && Character.isDigit(value.charAt(0))) {
            return (T) parseNumber(value);
        }
        return (T) value;
    }

    @Override
    public <T> T visitBinaryExpr(BinaryExpression expression) {
        String op = expression.getOperator().getLexeme();

        if (op.equals("|>")) {
            return visitPipeExpr(expression);
        }

        if (op.equals("??")) {
            Object left = expression.getLeft().accept(this);
            if (left != null && !(left instanceof NullValue)) {
                return (T) left;
            }
            return (T) expression.getRight().accept(this);
        }

        if (op.equals("&&")) {
            if (!resolveBoolean(expression.getLeft().accept(this))) {
                return (T) Boolean.FALSE;
            }
            if (!resolveBoolean(expression.getRight().accept(this))) {
                return (T) Boolean.FALSE;
            }
            return (T) Boolean.TRUE;
        }
        if (op.equals("||")) {
            if (resolveBoolean(expression.getLeft().accept(this))) {
                return (T) Boolean.TRUE;
            }
            if (resolveBoolean(expression.getRight().accept(this))) {
                return (T) Boolean.TRUE;
            }
            return (T) Boolean.FALSE;
        }

        Object left = expression.getLeft().accept(this);
        Object right = expression.getRight().accept(this);

        return (T) switch (op) {
            case "+" -> {
                try {
                    yield numericAdd(left, right);
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
                numericSub(left, right);
            case "*" ->
                numericMul(left, right);
            case "**" ->
                Math.pow(toNumber(left), toNumber(right));
            case "/" -> {
                double divisor = toNumber(right);
                if (divisor == 0) {
                    WarningCollector.emit(WarningLevel.WARNING,
                            "Division by zero", expression.getOperator());
                }
                yield toNumber(left) / divisor;
            }
            case "%" -> {
                if (left instanceof Long la && right instanceof Long lb) {
                    yield la % lb;
                }
                yield toNumber(left) % toNumber(right);
            }
            case "\\%" -> {
                if (left instanceof Long la && right instanceof Long lb) {
                    yield la / lb;
                }
                yield Math.floor(toNumber(left) / toNumber(right));
            }
            case "&" ->
                (long) toNumber(left) & (long) toNumber(right);
            case "|" ->
                (long) toNumber(left) | (long) toNumber(right);
            case "^" ->
                (long) toNumber(left) ^ (long) toNumber(right);
            case "<<" ->
                (long) toNumber(left) << (long) toNumber(right);
            case ">>" ->
                (long) toNumber(left) >> (long) toNumber(right);
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
            default ->
                throw new UnknownOperatorError(op);
        };
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
                    return (T) Double.valueOf(-toNumber(right));
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
                Number numVal;
                if (raw instanceof Number n) {
                    numVal = n;
                } else {
                    try {
                        numVal = parseNumber(String.valueOf(raw));
                    } catch (NumberFormatException e) {
                        throw new PostExprNaNError(name);
                    }
                }
                Object newValue = numericAdd(numVal, 1L);
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
                Number numVal;
                if (raw instanceof Number n) {
                    numVal = n;
                } else {
                    try {
                        numVal = parseNumber(String.valueOf(raw));
                    } catch (NumberFormatException e) {
                        throw new PostExprNaNError(name);
                    }
                }
                Object newValue = numericSub(numVal, 1L);
                env.assign(name, newValue);
                return (T) newValue;
            }

            case "~" -> {
                Object right = expression.getRight().accept(this);
                return (T) Long.valueOf(~(long) toNumber(right));
            }

            default ->
                throw new UnknownOperatorError(operator);
        }
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
    public <T> T visitCallExpr(CallExpression expression) {
        Object calleeResult = expression.getCallee().accept(this);
        String calleeName;
        Object callee;

        if (calleeResult instanceof String name) {
            calleeName = name;
            callee = globalEnvironment.getOrNull(calleeName);
            if (callee == null && localEnvironment != null) {
                Object local = localEnvironment.getOrNull(calleeName);
                if (local instanceof Callable) {
                    if (!localEnvironment.isDeclaredFunction(calleeName)) {
                        throw new LocalCallableError(calleeName);
                    }
                    callee = local;
                }
            }
            if (callee == null) {
                callee = globalEnvironment.get(calleeName);
            }
        } else if (calleeResult instanceof Callable) {
            calleeName = "<lambda>";
            callee = calleeResult;
        } else {
            throw new NotCallableError(String.valueOf(calleeResult));
        }

        if (!(callee instanceof Callable callable)) {
            throw new NotCallableError(calleeName);
        }

        List<Object> arguments = new ArrayList<>();

        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (callable instanceof Function f) {
            int min = f.getArity();
            int max = f.getMaxArity();
            if (arguments.size() < min || (max != -1 && arguments.size() > max)) {
                throw new ArgMismatchError(calleeName, min, arguments.size());
            }
        } else if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            throw new ArgMismatchError(calleeName, callable.getArity(), arguments.size());
        }

        miraCallStack.push(calleeName);
        try {
            if (pureFunctions.contains(calleeName)) {
                CacheKey cacheKey = new CacheKey(calleeName, arguments);
                if (callCache.containsKey(cacheKey)) {
                    return (T) callCache.get(cacheKey);
                }
                Object result = callable.call(this, arguments);
                callCache.put(cacheKey, result);
                return (T) result;
            }
            return (T) callable.call(this, arguments);
        } finally {
            miraCallStack.poll();
        }
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        Object namespaceObj = localEnvironment != null
                ? localEnvironment.getOrNull(expression.getAlias())
                : null;
        if (namespaceObj == null) {
            namespaceObj = globalEnvironment.get(expression.getAlias());
        }

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
    public Void visitFuncDecl(FuncDecl funcDecl) {
        globalEnvironment.defineFunction(funcDecl.getName(),
                new Function(localEnvironment,
                        funcDecl.getBody(),
                        funcDecl.getParameters(),
                        funcDecl.getArity(),
                        funcDecl.getMaxArity(),
                        funcDecl.getVariadicParam(),
                        funcDecl.isAsync()));

        return null;
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression lambda) {
        Environment capturedEnv = localEnvironment != null
                ? localEnvironment.snapshot(globalEnvironment)
                : globalEnvironment;
        return (T) new Function(capturedEnv, lambda.getBody(), lambda.getParameters(), lambda.getArity(), lambda.getMaxArity(), lambda.getVariadicParam(), lambda.isAsync());
    }

    @Override
    public <T> T visitArrayExpr(ArrayExpression expression) {
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
                        case String s ->
                            i = Integer.parseInt(s);
                        case Number n ->
                            i = (int) n.longValue();
                        default ->
                            throw new AssertionError();
                    }
                    switch (accessedObject) {
                        case ArrayExpression array -> {
                            Expression ae = array.getMembers().get(i);
                            accessedObject = ae instanceof ArrayExpression inner ? inner.accept(this) : ae.accept(this);
                        }
                        case ListExpression list -> {
                            Expression le = list.getMembers().get(i);
                            accessedObject = le instanceof ListExpression inner ? inner.accept(this) : le.accept(this);
                        }
                        default ->
                            throw new NotIterableError();
                    }
                }
            }
        }

        return (T) accessedObject;
    }

    @Override
    public <T> T visitFieldAccessExpression(FieldAccessExpression expression) {
        Object object = expression.getObject().accept(this);

        if (object instanceof String name) {
            Object fromLocal = localEnvironment != null ? localEnvironment.getOrNull(name) : null;
            object = fromLocal != null ? fromLocal : globalEnvironment.get(name);
        }

        if (!(object instanceof Environment objectEnv)) {
            if (expression.isOptional()) {
                return (T) NullValue.INSTANCE;
            }
            throw new FieldAccessError(expression.getField());
        }

        return (T) objectEnv.get(expression.getField());
    }

    @Override
    public <T> T visitMethodCallExpression(MethodCallExpression expression) {
        Object objectValue = expression.getObject().accept(this);

        if (objectValue instanceof String name) {
            objectValue = localEnvironment != null && localEnvironment.existsInChain(name)
                    ? localEnvironment.get(name)
                    : globalEnvironment.get(name);
        }

        if (expression.isOptional() && (objectValue == null || objectValue instanceof NullValue)) {
            return (T) NullValue.INSTANCE;
        }

        if (!(objectValue instanceof Environment objectEnv)) {
            throw new FieldAccessError(expression.getMethod());
        }

        Object methodValue = objectEnv.get(expression.getMethod());
        if (!(methodValue instanceof Callable callable)) {
            throw new NotCallableError(expression.getMethod());
        }

        List<Object> arguments = new ArrayList<>();
        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (callable instanceof Function f) {
            int min = f.getArity(), max = f.getMaxArity();
            if (arguments.size() < min || (max != -1 && arguments.size() > max)) {
                throw new ArgMismatchError(expression.getMethod(), min, arguments.size());
            }
        } else if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            throw new ArgMismatchError(expression.getMethod(), callable.getArity(), arguments.size());
        }

        return (T) callable.call(this, arguments);
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

            String op = unary.getOperation().getLexeme();
            boolean left = resolveBoolean(expressions.get(0).accept(this));

            if (op.equals("&&") && !left) {
                return (T) Boolean.FALSE;
            }
            if (op.equals("||") && left) {
                return (T) Boolean.TRUE;
            }

            boolean right = resolveBoolean(expressions.get(2).accept(this));
            if (right) {
                return (T) Boolean.TRUE;
            }
            return (T) Boolean.FALSE;
        }

        Object arithmetic = tryEvaluateArithmetic(expressions);
        if (arithmetic != null) {
            return (T) arithmetic;
        }

        return (T) evaluateAsString(expressions);
    }

    @Override
    public <T> T visitRangeExpression(RangeExpression expression) {
        Number startN = parseNumber(String.valueOf(expression.getStart().accept(this)));
        Number endN = parseNumber(String.valueOf(expression.getEnd().accept(this)));
        Number stepN = expression.getStepsize() != null
                ? parseNumber(String.valueOf(expression.getStepsize().accept(this)))
                : 1L;

        List<Expression> members = new ArrayList<>();
        if (startN instanceof Long ls && endN instanceof Long le && stepN instanceof Long lStep) {
            if (lStep == 0) {
                throw new RuntimeException("Range stepsize cannot be zero");
            }
            for (long i = ls; lStep > 0 ? i < le : i > le; i += lStep) {
                members.add(new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(i), 0, 0)));
            }
        } else {
            double start = startN.doubleValue(), end = endN.doubleValue(), step = stepN.doubleValue();
            if (step == 0) {
                throw new RuntimeException("Range stepsize cannot be zero");
            }
            for (double i = start; step > 0 ? i < end : i > end; i += step) {
                members.add(new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(i), 0, 0)));
            }
        }

        return (T) new ListExpression(members);
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

        for (FuncDecl method : expression.getMethods()) {
            Function fn = new Function(objectEnv, method.getBody(), method.getParameters(),
                    method.getArity(), method.getMaxArity(), method.getVariadicParam());
            objectEnv.define(method.getName(), fn);
        }

        if (!objectEnv.exists("this")) {
            objectEnv.define("this", objectEnv);
        }

        return (T) objectEnv;
    }

    private <T> T visitPipeExpr(BinaryExpression expr) {
        Object piped = expr.getLeft().accept(this);

        if (!(expr.getRight() instanceof CallExpression call)) {
            throw new NotCallableError("right-hand side of |> must be a call expression");
        }

        String calleeName = (String) call.getCallee().accept(this);
        Object fn;
        if (localEnvironment != null) {
            Object local = localEnvironment.getOrNull(calleeName);
            fn = local != null ? local : globalEnvironment.get(calleeName);
        } else {
            fn = globalEnvironment.get(calleeName);
        }
        if (!(fn instanceof Callable callable)) {
            throw new NotCallableError(calleeName);
        }

        List<Object> arguments = new ArrayList<>();
        arguments.add(piped);
        for (Expression arg : call.getArguments()) {
            arguments.add(arg.accept(this));
        }

        return (T) callable.call(this, arguments);
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

    private Number parseNumber(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        if (s.contains(".")) {
            return Double.parseDouble(s);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Double.parseDouble(s);
        }
    }

    private Object numericAdd(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.addExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la + (double) lb;
            }
        }
        return toNumber(a) + toNumber(b);
    }

    private Object numericSub(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.subtractExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la - (double) lb;
            }
        }
        return toNumber(a) - toNumber(b);
    }

    private Object numericMul(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.multiplyExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la * (double) lb;
            }
        }
        return toNumber(a) * toNumber(b);
    }

    private Object tryEvaluateArithmetic(List<Expression> expressions) {
        List<Number> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        for (int i = 0; i < expressions.size(); i++) {
            if (i % 2 == 0) {
                Object val = expressions.get(i).accept(this);
                if (val instanceof Number n) {
                    operands.add(n);
                } else {
                    try {
                        operands.add(parseNumber(String.valueOf(val)));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else {
                if (!(expressions.get(i) instanceof UnaryExpression unary)) {
                    return null;
                }
                String op = unary.getOperation().getLexeme();
                if (!Vocabulary.ARITHMETIC_OPERATORS.contains(op) && !Vocabulary.BITWISE_OPERATORS.contains(op)) {
                    return null;
                }
                operators.add(op);
            }
        }

        int i = 0;
        while (i < operators.size()) {
            String op = operators.get(i);
            if (op.equals("*") || op.equals("/") || op.equals("%") || op.equals("**")
                    || op.equals("\\%") || Vocabulary.BITWISE_OPERATORS.contains(op)) {
                Number left = operands.get(i);
                Number right = operands.get(i + 1);
                operands.set(i, evaluateNumericOp(op, left, right));
                operands.remove(i + 1);
                operators.remove(i);
            } else {
                i++;
            }
        }

        Object result = operands.get(0);
        for (int j = 0; j < operators.size(); j++) {
            result = evaluateNumericOp(operators.get(j), result, operands.get(j + 1));
        }

        return result;
    }

    private Number evaluateNumericOp(String op, Object left, Object right) {
        return switch (op) {
            case "+" ->
                (Number) numericAdd(left, right);
            case "-" ->
                (Number) numericSub(left, right);
            case "*" ->
                (Number) numericMul(left, right);
            case "**" ->
                Math.pow(toNumber(left), toNumber(right));
            case "/" ->
                toNumber(left) / toNumber(right);
            case "%" -> {
                if (left instanceof Long la && right instanceof Long lb) {
                    yield la % lb;
                }
                yield toNumber(left) % toNumber(right);
            }
            case "\\%" -> {
                if (left instanceof Long la && right instanceof Long lb) {
                    yield la / lb;
                }
                yield Math.floor(toNumber(left) / toNumber(right));
            }
            case "&" ->
                (long) toNumber(left) & (long) toNumber(right);
            case "|" ->
                (long) toNumber(left) | (long) toNumber(right);
            case "^" ->
                (long) toNumber(left) ^ (long) toNumber(right);
            case "<<" ->
                (long) toNumber(left) << (long) toNumber(right);
            case ">>" ->
                (long) toNumber(left) >> (long) toNumber(right);
            default ->
                throw new UnknownOperatorError(op);
        };
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
        return Vocabulary.COMPARISON_OPERATORS.contains(op);
    }

    private boolean isLogicalOperator(String op) {
        return Vocabulary.LOGICAL_OPERATORS.contains(op);
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
        if (leftObj instanceof Boolean lb && rightObj instanceof Boolean rb) {
            return switch (op) {
                case "==" ->
                    lb.equals(rb);
                case "!=" ->
                    !lb.equals(rb);
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
            case Number n ->
                n.doubleValue() != 0;
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
    public Void visitVarDecl(VarDecl varDecl) {
        notifyDebugger(varDecl);
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
    public Object visitEnum(EnumDecl stmt) {
        Environment enumEnv = new Environment(null, stmt.getValues().size());
        for (Map.Entry<String, Object> entry : stmt.getValues().entrySet()) {
            enumEnv.defineConst(entry.getKey(), entry.getValue());
        }
        globalEnvironment.defineConst(stmt.getIdentifier(), enumEnv);
        return null;
    }

    @Override
    public Void visitVarDestructure(VarDestructure stmt) {
        notifyDebugger(stmt);
        Object value = stmt.getInitializer().accept(this);
        List<Expression> members = switch (value) {
            case ListExpression l ->
                l.getMembers();
            case ArrayExpression a ->
                a.getMembers();
            default ->
                throw new RuntimeException("Cannot destructure value of type: " + value.getClass().getSimpleName());
        };
        Environment env = localEnvironment == null ? globalEnvironment : localEnvironment;
        List<String> names = stmt.getNames();
        for (int i = 0; i < names.size(); i++) {
            Object element = i < members.size() ? members.get(i).accept(this) : NullValue.INSTANCE;
            env.define(names.get(i), element);
        }
        return null;
    }

    @Override
    public Object visitLock(Lock stmt) {
        notifyDebugger(stmt);
        Object val = stmt.getMutex().accept(this);
        if (!(val instanceof MutexValue mutex)) {
            throw new TypeConversionError(val);
        }
        synchronized (mutex) {
            runBodyInFreshScope(stmt.getBody());
        }
        return null;
    }

    @Override
    public Void visitAssign(Assign assign) {
        notifyDebugger(assign);
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
                        case Number n -> {
                            i = (int) n.longValue();
                        }
                        default ->
                            throw new AssertionError();
                    }

                    switch (referencedObject) {
                        case ArrayExpression array -> {
                            referencedObject = array.getMembers().get(i);
                            if (referencedObject instanceof ArrayExpression innerArray) {
                                referencedObject = innerArray.accept(this);
                            } else if (referencedObject instanceof ListExpression innerList) {
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
                            case ArrayExpression array -> {
                                Object lastIdx = accessExpression.getIndecies().getLast().accept(this);
                                int arrayIdx = lastIdx instanceof Number n ? (int) n.longValue() : Integer.parseInt((String) lastIdx);
                                array.getMembers().set(arrayIdx, assignment);
                            }
                            case ListExpression list -> {
                                Object lastIdx = accessExpression.getIndecies().getLast().accept(this);
                                int listIdx = lastIdx instanceof Number n ? (int) n.longValue() : Integer.parseInt((String) lastIdx);
                                list.getMembers().set(listIdx, assignment);
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
    public Void visitReturn(Return ret) {
        notifyDebugger(ret);
        Object value = null;

        if (ret.getValue() != null) {
            value = ret.getValue().accept(this);
        }

        throw new ReturnSignal(value);
    }

    @Override
    public Object visitIf(If stmt) {
        notifyDebugger(stmt);
        Object condition = stmt.getCondition().accept(this);
        boolean value = resolveLoopCondition(condition);
        List<Node> body = value ? stmt.getThenBody() : stmt.getElseBody();

        if (body == null) {
            return null;
        }

        runBodyInFreshScope(body);

        return null;
    }

    @Override
    public Object visitFor(For stmt) {
        notifyDebugger(stmt);

        Environment outer = localEnvironment;
        Environment forScope = new Environment(outer != null ? outer : globalEnvironment);
        localEnvironment = forScope;
        runBody(stmt.getVarDecls());

        try {
            while (true) {
                if (stmt.getCondition() != null) {
                    localEnvironment = forScope;
                    Object condition = stmt.getCondition().accept(this);
                    if (!resolveLoopCondition(condition)) {
                        break;
                    }
                }

                Environment iterScope = forScope.snapshot(outer != null ? outer : globalEnvironment);
                localEnvironment = iterScope;
                try {
                    runBodyInFreshScope(stmt.getBody());
                } catch (ContinueSignal continueSignal) {
                }

                localEnvironment = forScope;
                runBody(stmt.getPostExpressions());
            }
        } catch (BreakSignal breakSignal) {
        }

        localEnvironment = outer;
        return null;
    }

    @Override
    public Object visitWhile(While stmt) {
        notifyDebugger(stmt);
        if (!stmt.getDoModifier()) {
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
        } else {
            try {
                while (true) {
                    try {
                        runBodyInFreshScope(stmt.getBody());
                    } catch (ContinueSignal continueSignal) {
                    }

                    Object condition = stmt.getCondition().accept(this);
                    if (!resolveLoopCondition(condition)) {
                        return null;
                    }
                }
            } catch (BreakSignal breakSignal) {
                return null;
            }
        }
    }

    @Override
    public Object visitForeach(Foreach stmt) {
        notifyDebugger(stmt);

        String iteratorName = stmt.getIterator().getName();

        try {
            if (stmt.getCollection() instanceof RangeExpression range) {
                Number startN = parseNumber(String.valueOf(range.getStart().accept(this)));
                Number endN = parseNumber(String.valueOf(range.getEnd().accept(this)));
                Number stepN = range.getStepsize() != null
                        ? parseNumber(String.valueOf(range.getStepsize().accept(this)))
                        : 1L;

                if (startN instanceof Long ls && endN instanceof Long le && stepN instanceof Long lStep) {
                    if (lStep == 0) {
                        throw new RangeStepZeroError();
                    }
                    for (long i = ls; lStep > 0 ? i < le : i > le; i += lStep) {
                        try {
                            runBodyWithIterator(iteratorName, i, stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                } else {
                    double start = startN.doubleValue(), end = endN.doubleValue(), step = stepN.doubleValue();
                    if (step == 0) {
                        throw new RangeStepZeroError();
                    }
                    for (double i = start; step > 0 ? i < end : i > end; i += step) {
                        try {
                            runBodyWithIterator(iteratorName, i, stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                return null;
            }

            Object iterable = stmt.getCollection().accept(this);

            switch (iterable) {
                case ArrayExpression array -> {
                    for (Expression expr : array.getMembers()) {
                        Object value = expr.accept(this);
                        try {
                            runBodyWithIterator(iteratorName, value, stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                case ListExpression list -> {
                    for (Expression expr : list.getMembers()) {
                        Object value = expr.accept(this);
                        try {
                            runBodyWithIterator(iteratorName, value, stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                case String string -> {
                    for (int i = 0; i < string.length(); i++) {
                        try {
                            runBodyWithIterator(iteratorName, String.valueOf(string.charAt(i)), stmt.getBody());
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
    public Object visitSwitch(Switch stmt) {
        Object subject = stmt.getSubject().accept(this);

        try {
            List<Node> matched = null;
            for (SwitchCase switchCase : stmt.getCases()) {
                Object caseValue = switchCase.getValue().accept(this);
                if (evaluateComparison(subject, "==", caseValue)) {
                    matched = switchCase.getBody();
                    break;
                }
            }
            if (matched != null) {
                runBodyInFreshScope(matched);
            } else if (stmt.getDefaultBody() != null) {
                runBodyInFreshScope(stmt.getDefaultBody());
            }
        } catch (BreakSignal ignored) {
        }

        return null;
    }

    @Override
    public Object visitThrow(Throw stmt) {
        notifyDebugger(stmt);
        Expression value = stmt.getValue();
        if (value instanceof ThrownException exception) {
            throw new ThrowSignal(exception.getIdentifier(), exception.accept(this));
        } else {
            throw new ThrowSignal(null, value.accept(this));
        }
    }

    @Override
    public Object visitThrownExpection(ThrownException expression) {
        return expression.getValue().accept(this);
    }

    @Override
    public Object visitTryCatch(TryCatch stmt) {
        notifyDebugger(stmt);
        try {
            runBodyInFreshScope(stmt.getTryBody());
        } catch (ThrowSignal signal) {
            boolean caught = false;
            for (CatchClause clause : stmt.getCatchClauses()) {
                String filter = clause.getTypeFilter();
                if (filter == null || filter.equals(signal.getExceptionType())) {
                    caught = true;
                    Environment previous = localEnvironment;
                    localEnvironment = new Environment(previous != null ? previous : globalEnvironment);
                    if (clause.getParamName() != null) {
                        localEnvironment.define(clause.getParamName(), signal.getValue());
                    }
                    try {
                        runBody(clause.getBody());
                    } finally {
                        localEnvironment = previous;
                    }
                    break;
                }
            }
            if (!caught) {
                throw signal;
            }
        } finally {
            if (!stmt.getFinallyBody().isEmpty()) {
                runBodyInFreshScope(stmt.getFinallyBody());
            }
        }
        return null;
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

    private void runBodyWithIterator(String iteratorName, Object value, List<Node> body) {
        Environment outer = localEnvironment;
        Environment iterEnv = new Environment(outer != null ? outer : globalEnvironment);
        iterEnv.define(iteratorName, value);
        localEnvironment = iterEnv;
        try {
            runBodyInFreshScope(body);
        } finally {
            localEnvironment = outer;
        }
    }

    private boolean resolveLoopCondition(Object condition) {
        return switch (condition) {
            case Boolean b ->
                b;
            case NullValue n ->
                false;
            case Number n ->
                n.doubleValue() != 0;
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

    private boolean isHoisted(Node ast) {
        return ast instanceof FuncDecl
                || ast instanceof EnumDecl
                || (ast instanceof VarDecl vd && vd.isConst());
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
    public <T> T visitTypeofExpr(TypeofExpression expression) {
        Object val = expression.getExpr().accept(this);
        return (T) switch (val) {
            case null ->
                "null";
            case NullValue n ->
                "null";
            case Boolean b ->
                "bool";
            case Number n ->
                "number";
            case String s ->
                "string";
            case Promise p ->
                "promise";
            case Callable c ->
                "fn";
            case ListExpression l ->
                "list";
            case ArrayExpression a ->
                "array";
            case MapExpression m ->
                "map";
            case Environment e ->
                "object";
            default ->
                "unknown";
        };
    }

    @Override
    public <T> T visitSwitchExpr(SwitchExpression expression) {
        Object subject = expression.getSubject().accept(this);
        for (SwitchExpression.SwitchExprCase c : expression.getCases()) {
            Object caseVal = c.value().accept(this);
            if (evaluateComparison(subject, "==", caseVal)) {
                return (T) c.result().accept(this);
            }
        }
        if (expression.getDefaultExpr() != null) {
            return (T) expression.getDefaultExpr().accept(this);
        }
        return (T) NullValue.INSTANCE;
    }

    @Override
    public <T> T visitAwaitExpr(AwaitExpression expression) {
        Object value = expression.getExpr().accept(this);
        if (value instanceof Promise promise) {
            try {
                return (T) promise.getFuture().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ThrowSignal("InterruptedError", e.getMessage());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ThrowSignal ts) {
                    throw ts;
                }
                throw new ThrowSignal("AsyncError", cause != null ? cause.getMessage() : "async error");
            }
        }
        return (T) value;
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

    private String formatValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return "\"" + s + "\"";
        }
        if (val instanceof List<?> list) {
            return "List[" + list.size() + "]";
        }
        if (val instanceof java.util.Map<?, ?> map) {
            return "Map{" + map.size() + "}";
        }
        if (val instanceof Function) {
            return "<fn>";
        }
        if (val instanceof NativeFunction) {
            return "<native fn>";
        }
        if (val instanceof Promise p) {
            return p.toString();
        }
        return val.toString();
    }
}
