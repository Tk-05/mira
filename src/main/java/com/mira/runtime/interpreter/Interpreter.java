package com.mira.runtime.interpreter;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.mira.cli.Flags;
import com.mira.error.resolver.StaticCheckError.StaticAssertFailedError;
import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.FieldAccessError;
import com.mira.error.runtime.RuntimeError.ImmutableCollectionError;
import com.mira.error.runtime.RuntimeError.IndexOutOfBoundsError;
import com.mira.error.runtime.RuntimeError.NotANamespaceError;
import com.mira.error.runtime.RuntimeError.NotAStructTemplateError;
import com.mira.error.runtime.RuntimeError.NotCallableError;
import com.mira.error.runtime.RuntimeError.NotIterableError;
import com.mira.error.runtime.RuntimeError.PostExprNaNError;
import com.mira.error.runtime.RuntimeError.RangeStepZeroError;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.TypeConversionError;
import com.mira.error.runtime.RuntimeError.UnknownOperatorError;
import com.mira.error.runtime.RuntimeError.UnknownStructFieldError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AssignExpression;
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
import com.mira.parser.nodes.expression.Expression.Mutability;
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
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.ComptimeExecutor;
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
import com.mira.testing.CoverageTracker;
import com.mira.testing.TestRunner;

@SuppressWarnings("unchecked")
public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Object> {

    private static Interpreter instance;
    private static final ThreadLocal<Interpreter> activeInterpreter = new ThreadLocal<>();

    public record StackFrame(String name, int line) {

    }

    private record CacheKey(String name, List<Object> args) {

        CacheKey(String name, List<Object> args) {
            this.name = name;
            this.args = List.copyOf(args);
        }
    }

    public interface DebugHook {

        void onLine(int line, Environment env);
    }

    private Environment globalEnvironment = new Environment();
    private Environment localEnvironment;
    private Map<String, Object> presetComptimeConsts = null;
    private static final int PURE_CACHE_MAX = 512;
    private final Map<CacheKey, Object> callCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Object> eldest) {
            return size() > PURE_CACHE_MAX;
        }
    };
    private Set<String> pureFunctions = Set.of();
    private final Deque<StackFrame> miraCallStack = new ArrayDeque<>();
    private DebugHook debugHook;
    private final Profiler profiler = new Profiler();
    // Populated from ImportResolver's parallel aliased-import resolution
    // (multiple imports resolved concurrently on separate throwaway
    // Interpreter instances), which registers directly onto this real
    // instance's map rather than the throwaway one doing the resolving -
    // must be thread-safe.
    private final Map<String, String> functionModule = new ConcurrentHashMap<>();
    private String entryModuleName = "<script>";

    public Interpreter() {
        ImportResolver.loadInternal(globalEnvironment);
    }

    /**
     * Wraps an already-initialized global environment instead of creating a fresh
     * one - used to bind {@link #getInstance()} to a compiled program's own static
     * {@code GLOBALS} field (see {@link #adoptAsActive(Environment)}), so native
     * functions like {@code eval}/ {@code exec}/{@code importDynamic} operate on
     * the environment the compiled program can actually see, instead of a
     * disconnected throwaway one.
     */
    public Interpreter(Environment existingGlobalEnvironment) {
        this.globalEnvironment = existingGlobalEnvironment;
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

    public static void adoptAsActive(Environment env) {
        activeInterpreter.set(new Interpreter(env));
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

        entryModuleName = !asts.isEmpty() && asts.get(0) instanceof ModuleDecl moduleDecl
                ? moduleDecl.getModuleName()
                : "<script>";

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
                    functionModule.put(funcDecl.getName(), entryModuleName);
                    if (Flags.mainFunction) {
                        funcDecl.getBody().add(new Return(new DumbExpression(new Token(null, "0", 0, 0))));
                    }
                    funcDecl.accept(this);
                }
                case EnumDecl enumDecl when !(globalEnvironment
                        .getOrNull(enumDecl.getIdentifier()) instanceof Namespace) ->
                    enumDecl.accept(this);
                case VarDecl varDecl when varDecl.isConst()
                        && !(globalEnvironment.getOrNull(varDecl.getName()) instanceof Namespace) ->
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

        Map<String, Object> comptimeConsts = presetComptimeConsts != null
                ? presetComptimeConsts
                : new ComptimeExecutor().execute(asts);
        comptimeConsts.forEach((name, value) -> {
            if (!globalEnvironment.existsInChain(name)) {
                globalEnvironment.defineConst(name, value);
            }
        });
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
                int mainArity = asts.stream().filter(n -> n instanceof FuncDecl fd && "main".equals(fd.getName()))
                        .mapToInt(n -> ((FuncDecl) n).getParameters().size()).findFirst().orElse(0);
                Expression argsTuple = mainArity > 0 ? getArgsTuple(args != null ? args : new String[0]) : null;
                return (T) new CallExpression(new DumbExpression(new Token(null, "main", 0, 0)),
                        argsTuple == null ? List.of() : List.of(argsTuple)).accept(this);
            } else {
                globalEnvironment.define("args", getArgsTuple(args));
                for (Node ast : asts) {
                    if (isHoisted(ast)) {
                        continue;
                    }
                    lastResult = switch (ast) {
                        case Expression expression -> expression.accept(this);
                        case Statement statement -> statement.accept(this);
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
                return (T) new CallExpression(new DumbExpression(new Token(null, "main", 0, 0)), new ArrayList<>())
                        .accept(this);
            } else {
                for (Node ast : asts) {
                    if (isHoisted(ast)) {
                        continue;
                    }
                    lastResult = switch (ast) {
                        case Expression expression -> expression.accept(this);
                        case Statement statement -> statement.accept(this);
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
                return (T) new CallExpression(new DumbExpression(new Token(null, "main", 0, 0)), new ArrayList<>())
                        .accept(this);
            } else {
                List<ImportExpression> imports = new ArrayList<>();
                for (Node ast : asts) {
                    if (ast instanceof ImportExpression imp) {
                        imports.add(imp);
                    }
                }
                if (!imports.isEmpty()) {
                    ImportResolver.resolveImports(imports, globalEnvironment, this, false);
                }
                for (Node ast : asts) {
                    if (ast instanceof ImportExpression) {
                        continue;
                    }
                    lastResult = switch (ast) {
                        case Expression expression -> expression.accept(this);
                        case Statement statement -> statement.accept(this);
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
            case Expression expression -> expression.accept(this);
            case Statement statement -> statement.accept(this);
            default -> throw new AssertionError();
        }

        globalEnvironment = previousGlobal;
        localEnvironment = previous;
    }

    public void setDebugHook(DebugHook hook) {
        this.debugHook = hook;
    }

    public List<StackFrame> getCallStack() {
        return new ArrayList<>(miraCallStack);
    }

    public Profiler getProfiler() {
        return profiler;
    }

    public void setProfilingEnabled(boolean enabled) {
        profiler.enabled = enabled;
    }

    public void registerFunctionModule(String name, String module) {
        functionModule.put(name, module);
    }

    private void notifyDebugger(int line) {
        if (line > 0 && (profiler.enabled || CoverageTracker.isEnabled())) {
            StackFrame frame = miraCallStack.peek();
            String functionName = frame == null ? "<script>" : frame.name();
            String bareName = functionName.contains(".")
                    ? functionName.substring(functionName.lastIndexOf('.') + 1)
                    : functionName;
            String moduleName = functionModule.getOrDefault(bareName, entryModuleName);
            if (profiler.enabled) {
                profiler.onLine(line, functionName, moduleName);
            }
            if (CoverageTracker.isEnabled()) {
                CoverageTracker.recordLine(moduleName, line);
            }
        }
        if (debugHook != null && line > 0) {
            debugHook.onLine(line, localEnvironment != null ? localEnvironment : globalEnvironment);
        }
    }

    private String typeName(Object val) {
        return switch (val) {
            case null -> "null";
            case com.mira.runtime.values.NullValue n -> "null";
            case Boolean b -> "bool";
            case Number n -> "number";
            case String s -> "string";
            case com.mira.runtime.functions.Promise p -> "promise";
            case Callable c -> "fn";
            case ListExpression l -> "list";
            case ArrayExpression a -> "array";
            case Expression.MapExpression m -> "map";
            case Environment e -> "object";
            default -> val.getClass().getSimpleName();
        };
    }

    private String buildSignature(String name, com.mira.runtime.functions.Function f) {
        String params = f.getParameters().stream().map(p -> "$" + p.name())
                .collect(java.util.stream.Collectors.joining(", "));
        return "fn " + name + "(" + params + ")";
    }

    public void dumpState(Throwable cause, PrintStream out) {
        out.println("\n=== MIRA CRASH DUMP ===");
        out.println("Cause: " + cause);
        out.println();

        out.println("--- Mira Call Stack ---");
        if (miraCallStack.isEmpty()) {
            out.println("  <top level>");
        } else {
            for (StackFrame frame : miraCallStack) {
                String loc = frame.line() > 0 ? " (line " + frame.line() + ")" : "";
                out.println("  at " + frame.name() + "()" + loc);
            }
        }
        out.println();

        out.println("--- Java Stack Trace ---");
        cause.printStackTrace(out);
        out.println();

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
        // A pre-set cached value (e.g. a struct/Environment or other runtime object
        // wrapped by assignToAccess when storing an already-evaluated value into a
        // collection) is authoritative and must win over re-interpreting the token
        // text, which is only ever a human-readable label in that case.
        Object cached = expression.getCachedValue();
        if (cached != null) {
            return (T) cached;
        }
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
        if (expression.getTokenType() == TokenType.EXPRESSION && !value.isEmpty()
                && Character.isDigit(value.charAt(0))) {
            try {
                Number parsed = parseNumber(value);
                expression.setCachedValue(parsed);
                return (T) parsed;
            } catch (NumberFormatException e) {
                return (T) value;
            }
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
                } catch (NumberFormatException | TypeConversionError e) {
                    yield String.valueOf(left) + String.valueOf(right);
                }
            }
            case "-" -> numericSub(left, right);
            case "*" -> numericMul(left, right);
            case "**" -> Math.pow(toNumber(left), toNumber(right));
            case "/" -> {
                double divisor = toNumber(right);
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
            case "&" -> (long) toNumber(left) & (long) toNumber(right);
            case "|" -> (long) toNumber(left) | (long) toNumber(right);
            case "^" -> (long) toNumber(left) ^ (long) toNumber(right);
            case "<<" -> (long) toNumber(left) << (long) toNumber(right);
            case ">>" -> (long) toNumber(left) >> (long) toNumber(right);
            case "==" -> evaluateComparison(left, "==", right);
            case "!=" -> evaluateComparison(left, "!=", right);
            case "<" -> evaluateComparison(left, "<", right);
            case ">" -> evaluateComparison(left, ">", right);
            case "<=" -> evaluateComparison(left, "<=", right);
            case ">=" -> evaluateComparison(left, ">=", right);
            default -> throw new UnknownOperatorError(op, typeName(left), typeName(right))
                    .withLocation(expression.getOperator().getLine(), expression.getOperator().getColumn());
        };
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        String operator = expression.getOperation().getLexeme();

        switch (operator) {
            case "$" -> {
                String name = expression.getRight() instanceof DumbExpression d
                        ? d.getValue()
                        : (String) expression.getRight().accept(this);

                if (localEnvironment != null) {
                    Object val = localEnvironment.getOrNull(name);
                    if (val != null) {
                        return (T) val;
                    }
                }

                try {
                    return (T) globalEnvironment.get(name);
                } catch (com.mira.error.MiraError e) {
                    if (e.getLine() < 0 && expression.getRight() instanceof DumbExpression de) {
                        e.withLocation(de.getLine(), de.getColumn());
                    }
                    throw e;
                }
            }

            case "-" -> {
                Object right = expression.getRight() != null ? expression.getRight().accept(this) : null;

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
                return doIncDec(expression, true);
            }

            case "--" -> {
                return doIncDec(expression, false);
            }

            case "~" -> {
                Object right = expression.getRight().accept(this);
                return (T) Long.valueOf(~(long) toNumber(right));
            }

            default -> throw new UnknownOperatorError(operator).withLocation(expression.getOperation().getLine(),
                    expression.getOperation().getColumn());
        }
    }

    private <T> T doIncDec(UnaryExpression expression, boolean inc) {
        Expression target = expression.getRight();
        Object raw = target.accept(this);

        Number numVal;
        if (raw instanceof Number n) {
            numVal = n;
        } else {
            try {
                numVal = parseNumber(String.valueOf(raw));
            } catch (NumberFormatException e) {
                String description = target instanceof UnaryExpression varExpr
                        && varExpr.getOperation().getLexeme().equals("$")
                                ? (String) varExpr.getRight().accept(this)
                                : target.toString();
                throw new PostExprNaNError(description).withLocation(expression.getOperation().getLine(),
                        expression.getOperation().getColumn());
            }
        }
        Object newValue = inc ? numericAdd(numVal, 1L) : numericSub(numVal, 1L);

        if (target instanceof UnaryExpression varExpr && varExpr.getOperation().getLexeme().equals("$")) {
            String name = (String) varExpr.getRight().accept(this);
            Environment env = (localEnvironment != null && localEnvironment.getOrNull(name) != null)
                    ? localEnvironment
                    : globalEnvironment;
            env.assign(name, newValue);
        } else if (target instanceof AccessExpression accessExpression) {
            assignToAccess(accessExpression, () -> newValue);
        } else if (target instanceof FieldAccessExpression fieldAccessExpression) {
            assignToField(fieldAccessExpression, () -> newValue);
        }

        return (T) newValue;
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

        int calleeLine = expression.getCallee() instanceof DumbExpression de ? de.getLine() : -1;
        int calleeCol = expression.getCallee() instanceof DumbExpression de2 ? de2.getColumn() : -1;

        if (calleeResult instanceof String name) {
            calleeName = name;
            callee = globalEnvironment.getOrNull(calleeName);
            if (callee == null && localEnvironment != null) {
                Object local = localEnvironment.getOrNull(calleeName);
                if (local instanceof Callable) {
                    callee = local;
                }
            }
            if (callee == null) {
                try {
                    callee = globalEnvironment.get(calleeName);
                } catch (com.mira.error.MiraError e) {
                    if (e.getLine() < 0) {
                        e.withLocation(calleeLine, calleeCol);
                    }
                    throw e;
                }
            }
        } else if (calleeResult instanceof Callable) {
            calleeName = "<lambda>";
            callee = calleeResult;
        } else {
            throw new NotCallableError(String.valueOf(calleeResult) + " (got: " + typeName(calleeResult) + ")")
                    .withLocation(calleeLine, calleeCol);
        }

        if (!(callee instanceof Callable callable)) {
            throw new NotCallableError(calleeName + " (got: " + typeName(callee) + ")").withLocation(calleeLine,
                    calleeCol);
        }

        List<Object> arguments = new ArrayList<>();

        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (callable instanceof Function f) {
            int min = f.getArity();
            int max = f.getMaxArity();
            if (arguments.size() < min || (max != -1 && arguments.size() > max)) {
                String sig = buildSignature(calleeName, f);
                ArgMismatchError amErr = new ArgMismatchError(calleeName, min, arguments.size(), sig);
                amErr.withLocation(calleeLine, calleeCol);
                throw amErr;
            }
        } else if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            ArgMismatchError amErr = new ArgMismatchError(calleeName, callable.getArity(), arguments.size());
            amErr.withLocation(calleeLine, calleeCol);
            throw amErr;
        }

        miraCallStack.push(new StackFrame(calleeName, calleeLine));
        boolean threw = false;
        boolean profiling = profiler.enabled;
        try {
            if (pureFunctions.contains(calleeName)) {
                CacheKey cacheKey = new CacheKey(calleeName, arguments);
                if (callCache.containsKey(cacheKey)) {
                    if (profiling) {
                        profiler.recordCacheHit(calleeName);
                    }
                    return (T) callCache.get(cacheKey);
                }
                if (profiling) {
                    profiler.start();
                }
                Object result = callable.call(this, arguments);
                if (profiling) {
                    profiler.stop(calleeName);
                }
                callCache.put(cacheKey, result);
                return (T) result;
            }
            if (profiling) {
                profiler.start();
            }
            Object result = callable.call(this, arguments);
            if (profiling) {
                profiler.stop(calleeName);
            }
            return (T) result;
        } catch (Throwable t) {
            threw = true;
            if (profiling) {
                profiler.stop(calleeName);
            }
            throw t;
        } finally {
            if (!threw) {
                miraCallStack.poll();
            }
        }
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        Object namespaceObj = localEnvironment != null ? localEnvironment.getOrNull(expression.getAlias()) : null;
        if (!(namespaceObj instanceof Namespace)) {
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

        if (callable instanceof Function f) {
            int min = f.getArity();
            int max = f.getMaxArity();
            if (arguments.size() < min || (max != -1 && arguments.size() > max)) {
                throw new ArgMismatchError(expression.getFunctionName(), min, arguments.size());
            }
        } else if (callable.getArity() != -1 && arguments.size() != callable.getArity()) {
            throw new ArgMismatchError(expression.getFunctionName(), callable.getArity(), arguments.size());
        }

        String nsFrame = expression.getAlias() + "." + expression.getFunctionName();
        miraCallStack.push(new StackFrame(nsFrame, expression.getLine()));
        boolean threw = false;
        boolean profiling = profiler.enabled;
        if (profiling) {
            profiler.start();
        }
        try {
            Object result = callable.call(this, arguments);
            if (profiling) {
                profiler.stop(nsFrame);
            }
            return (T) result;
        } catch (Throwable t) {
            threw = true;
            if (profiling) {
                profiler.stop(nsFrame);
            }
            throw t;
        } finally {
            if (!threw) {
                miraCallStack.poll();
            }
        }
    }

    @Override
    public Void visitFuncDecl(FuncDecl funcDecl) {
        globalEnvironment.defineFunction(funcDecl.getName(),
                new Function(localEnvironment, funcDecl.getBody(), funcDecl.getParameters(), funcDecl.getArity(),
                        funcDecl.getMaxArity(), funcDecl.getVariadicParam(), funcDecl.isAsync(), globalEnvironment));

        if (funcDecl.isPublic()) {
            globalEnvironment.markPublic(funcDecl.getName());
        }

        return null;
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression lambda) {
        Environment capturedEnv = localEnvironment != null
                ? localEnvironment.snapshot(globalEnvironment)
                : globalEnvironment;
        return (T) new Function(capturedEnv, lambda.getBody(), lambda.getParameters(), lambda.getArity(),
                lambda.getMaxArity(), lambda.getVariadicParam(), lambda.isAsync(), globalEnvironment);
    }

    @Override
    public <T> T visitArrayExpr(ArrayExpression expression) {
        return (T) expression;
    }

    @Override
    public <T> T visitListExpr(ListExpression expression) {
        List<Expression> evaluated = new ArrayList<>();
        for (Expression member : expression.getMembers()) {
            Object result = member.accept(this);
            if (result instanceof Expression e) {
                evaluated.add(e);
            } else {
                final Object captured = result;
                evaluated.add(new Expression() {
                    @Override
                    public <T2> T2 accept(ExprVisitor<T2> visitor) {
                        return (T2) captured;
                    }

                    @Override
                    public String toString() {
                        return String.valueOf(captured);
                    }
                });
            }
        }
        return (T) new ListExpression(evaluated);
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
                        throw new FieldAccessError(key, "map");
                    }
                    accessedObject = val.accept(this);
                }
                default -> {
                    int i;
                    switch (object) {
                        case String s -> i = Integer.parseInt(s);
                        case Number n -> i = (int) n.longValue();
                        default -> throw new AssertionError();
                    }
                    switch (accessedObject) {
                        case ArrayExpression array -> {
                            var members = array.getMembers();
                            int size = members.size();
                            if (i < 0 || i >= size) {
                                throw new IndexOutOfBoundsError(i, size);
                            }
                            Expression ae = members.get(i);
                            if (ae instanceof ArrayExpression inner) {
                                accessedObject = inner.accept(this);
                            } else if (ae != null) {
                                accessedObject = ae.accept(this);
                            } else {
                                accessedObject = NullValue.INSTANCE;
                            }
                        }
                        case ListExpression list -> {
                            var members = list.getMembers();
                            int size = members.size();
                            if (i < 0 || i >= size) {
                                throw new IndexOutOfBoundsError(i, size);
                            }
                            Expression le = members.get(i);
                            if (le instanceof ListExpression inner) {
                                accessedObject = inner.accept(this);
                            } else if (le != null) {
                                accessedObject = le.accept(this);
                            } else {
                                accessedObject = NullValue.INSTANCE;
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
            int faLine = expression.getObject() instanceof UnaryExpression ue ? ue.getOperation().getLine() : -1;
            int faCol = expression.getObject() instanceof UnaryExpression ue2 ? ue2.getOperation().getColumn() : -1;
            throw new FieldAccessError(expression.getField(), typeName(object)).withLocation(faLine, faCol);
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
            int mcLine = expression.getObject() instanceof UnaryExpression ue ? ue.getOperation().getLine() : -1;
            int mcCol = expression.getObject() instanceof UnaryExpression ue2 ? ue2.getOperation().getColumn() : -1;
            throw new FieldAccessError(expression.getMethod(), typeName(objectValue)).withLocation(mcLine, mcCol);
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

        if (!profiler.enabled) {
            return (T) callable.call(this, arguments);
        }
        profiler.start();
        try {
            Object result = callable.call(this, arguments);
            profiler.stop(expression.getMethod());
            return (T) result;
        } catch (Throwable t) {
            profiler.stop(expression.getMethod());
            throw t;
        }
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        return (T) evaluateAsString(expression.getExpressions());
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
                throw new RangeStepZeroError();
            }
            for (long i = ls; lStep > 0 ? i < le : i > le; i += lStep) {
                members.add(new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(i), 0, 0)));
            }
        } else {
            double start = startN.doubleValue(), end = endN.doubleValue(), step = stepN.doubleValue();
            if (step == 0) {
                throw new RangeStepZeroError();
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
            Object value = field.getInitializer() != null ? field.getInitializer().accept(this) : null;

            if (field.isConst()) {
                objectEnv.defineConst(field.getName(), value);
            } else {
                objectEnv.define(field.getName(), value);
            }
        }

        for (FuncDecl method : expression.getMethods()) {
            Function fn = new Function(objectEnv, method.getBody(), method.getParameters(), method.getArity(),
                    method.getMaxArity(), method.getVariadicParam(), globalEnvironment);
            objectEnv.define(method.getName(), fn);
        }

        if (!objectEnv.exists("this")) {
            objectEnv.define("this", objectEnv);
        }

        return (T) objectEnv;
    }

    @Override
    public <T> T visitStructExpression(StructExpression expression) {
        Environment defaults = new Environment();

        for (VarDecl field : expression.getVarDecls()) {
            Object value = field.getInitializer() != null ? field.getInitializer().accept(this) : null;

            if (field.isConst()) {
                defaults.defineConst(field.getName(), value);
            } else {
                defaults.define(field.getName(), value);
            }
        }

        for (FuncDecl method : expression.getMethods()) {
            Function fn = new Function(defaults, method.getBody(), method.getParameters(), method.getArity(),
                    method.getMaxArity(), method.getVariadicParam(), globalEnvironment);
            defaults.define(method.getName(), fn);
        }

        return (T) new StructTemplate(defaults, expression.getMethods());
    }

    @Override
    public <T> T visitStructInitExpression(StructInitExpression expression) {
        Object targetValue = expression.getTarget().accept(this);
        if (!(targetValue instanceof StructTemplate template)) {
            throw new NotAStructTemplateError();
        }

        Environment instanceEnv = template.getDefaults().copyShallow();

        for (FuncDecl method : template.getMethods()) {
            Function fn = new Function(instanceEnv, method.getBody(), method.getParameters(), method.getArity(),
                    method.getMaxArity(), method.getVariadicParam(), globalEnvironment);
            instanceEnv.forceDefine(method.getName(), fn);
        }

        if (!instanceEnv.exists("this")) {
            instanceEnv.define("this", instanceEnv);
        }

        for (Map.Entry<String, Expression> override : expression.getOverrides().entrySet()) {
            String name = override.getKey();
            if (!instanceEnv.exists(name)) {
                throw new UnknownStructFieldError(name);
            }
            instanceEnv.forceDefine(name, override.getValue().accept(this));
        }

        return (T) instanceEnv;
    }

    private <T> T visitPipeExpr(BinaryExpression expr) {
        Object piped = expr.getLeft().accept(this);

        if (expr.getRight() instanceof NamespaceCallExpression nsCall) {
            Object namespaceObj = localEnvironment != null ? localEnvironment.getOrNull(nsCall.getAlias()) : null;
            if (!(namespaceObj instanceof Namespace)) {
                namespaceObj = globalEnvironment.get(nsCall.getAlias());
            }
            if (!(namespaceObj instanceof Namespace namespace)) {
                throw new NotCallableError(nsCall.getAlias() + "." + nsCall.getFunctionName());
            }
            Object callee = namespace.get(nsCall.getFunctionName());
            if (!(callee instanceof Callable callable)) {
                throw new NotCallableError(nsCall.getAlias() + "." + nsCall.getFunctionName());
            }
            List<Object> arguments = new ArrayList<>();
            arguments.add(piped);
            for (Expression arg : nsCall.getArguments()) {
                arguments.add(arg.accept(this));
            }
            if (!profiler.enabled) {
                return (T) callable.call(this, arguments);
            }
            String nsFrame = nsCall.getAlias() + "." + nsCall.getFunctionName();
            profiler.start();
            try {
                Object result = callable.call(this, arguments);
                profiler.stop(nsFrame);
                return (T) result;
            } catch (Throwable t) {
                profiler.stop(nsFrame);
                throw t;
            }
        }

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

        if (!profiler.enabled) {
            return (T) callable.call(this, arguments);
        }
        profiler.start();
        try {
            Object result = callable.call(this, arguments);
            profiler.stop(calleeName);
            return (T) result;
        } catch (Throwable t) {
            profiler.stop(calleeName);
            throw t;
        }
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
        if (s.length() > 1 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
            return Long.parseLong(s, 2, s.length(), 16);
        }
        if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
            return Double.parseDouble(s);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Double.parseDouble(s);
        }
    }

    private Object numericAdd(Object a, Object b) {
        if (a instanceof Double da && b instanceof Double db) {
            return da + db;
        }
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
        if (a instanceof Double da && b instanceof Double db) {
            return da - db;
        }
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
        if (a instanceof Double da && b instanceof Double db) {
            return da * db;
        }
        if (a instanceof Long la && b instanceof Long lb) {
            try {
                return Math.multiplyExact(la, lb);
            } catch (ArithmeticException e) {
                return (double) la * (double) lb;
            }
        }
        return toNumber(a) * toNumber(b);
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
                case "+", "-", "*", "/" -> builder.append(operator).append(right);
                default -> throw new UnknownOperatorError("Unknown operator: " + operator);
            }

            i++;
        }

        return builder.toString();
    }

    private Boolean evaluateComparison(Object leftObj, String op, Object rightObj) {
        if (leftObj instanceof Number ln && rightObj instanceof Number rn) {
            double l = ln.doubleValue(), r = rn.doubleValue();
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case "<" -> l < r;
                case ">" -> l > r;
                case "<=" -> l <= r;
                case ">=" -> l >= r;
                default -> throw new UnknownOperatorError(op);
            };
        }
        if (leftObj instanceof Boolean lb && rightObj instanceof Boolean rb) {
            return switch (op) {
                case "==" -> lb.equals(rb);
                case "!=" -> !lb.equals(rb);
                default -> throw new UnknownOperatorError(op);
            };
        }
        String left = String.valueOf(leftObj);
        String right = String.valueOf(rightObj);
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case "<" -> l < r;
                case ">" -> l > r;
                case "<=" -> l <= r;
                case ">=" -> l >= r;
                default -> throw new UnknownOperatorError(op);
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==" -> left.equals(right);
                case "!=" -> !left.equals(right);
                case "<" -> left.compareTo(right) < 0;
                case ">" -> left.compareTo(right) > 0;
                case "<=" -> left.compareTo(right) <= 0;
                case ">=" -> left.compareTo(right) >= 0;
                default -> throw new UnknownOperatorError(op);
            };
        }
    }

    private boolean resolveBoolean(Object value) {
        return switch (value) {
            case Boolean b -> b;
            case NullValue n -> false;
            case Number n -> n.doubleValue() != 0;
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
            default -> throw new AssertionError("Cannot resolve boolean from: " + value.getClass());
        };
    }

    @Override
    public Void visitVarDecl(VarDecl varDecl) {
        notifyDebugger(varDecl.line);
        Object value = NullValue.INSTANCE;

        if (varDecl.getInitializer() != null) {
            value = varDecl.getInitializer().accept(this);
        }

        Environment env = localEnvironment == null ? globalEnvironment : localEnvironment;

        try {
            if (varDecl.isConst()) {
                env.defineConst(varDecl.getName(), value);
            } else {
                env.define(varDecl.getName(), value);
            }
        } catch (com.mira.error.MiraError e) {
            if (e.getLine() < 0) {
                e.withLocation(varDecl.line, varDecl.nameColumn);
            }
            throw e;
        }

        if (varDecl.isPublic()) {
            env.markPublic(varDecl.getName());
        }

        return null;
    }

    @Override
    public Object visitEnum(EnumDecl stmt) {
        Environment enumEnv = new Environment(null, stmt.getValues().size());
        for (Map.Entry<String, Expression> entry : stmt.getValues().entrySet()) {
            enumEnv.defineConst(entry.getKey(), entry.getValue().accept(this));
        }
        globalEnvironment.defineConst(stmt.getIdentifier(), enumEnv);
        if (stmt.isPublic()) {
            globalEnvironment.markPublic(stmt.getIdentifier());
        }
        return null;
    }

    @Override
    public Void visitVarDestructure(VarDestructure stmt) {
        notifyDebugger(stmt.line);
        Object value = stmt.getInitializer().accept(this);
        List<Expression> members = switch (value) {
            case ListExpression l -> l.getMembers();
            case ArrayExpression a -> a.getMembers();
            default -> throw new NotIterableError();
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
        notifyDebugger(stmt.line);
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
    public Object visitComptimeBlock(ComptimeBlock stmt) {
        return null;
    }

    @Override
    public Object visitStaticAssert(StaticAssert stmt) {
        Object condition = stmt.getCondition().accept(this);
        if (!resolveLoopCondition(condition)) {
            String msg = stmt.getMessage() != null ? String.valueOf(stmt.getMessage().accept(this)) : null;
            throw new StaticAssertFailedError(msg, stmt.line, stmt.column);
        }
        return null;
    }

    @Override
    public Object visitTestCall(TestCall stmt) {
        if (!Flags.testMode) {
            return null;
        }
        String name = String.valueOf(stmt.getName().accept(this));
        Object fn = stmt.getTestFn().accept(this);
        if (!(fn instanceof Callable callable)) {
            throw new RuntimeException("test() second argument must be a function");
        }
        TestRunner.register(name, callable, this);
        return null;
    }

    @Override
    public Void visitAssign(Assign assign) {
        notifyDebugger(assign.line);
        switch (assign.getReference()) {
            case AccessExpression accessExpression ->
                assignToAccess(accessExpression, () -> assign.getExpression().accept(this));
            case FieldAccessExpression fieldAccessExpression ->
                assignToField(fieldAccessExpression, () -> assign.getExpression().accept(this));
            default -> {
                if (assign.getReference() instanceof UnaryExpression unaryExpression) {
                    String name = String.valueOf(unaryExpression.getRight().accept(this));
                    Object expression = assign.getExpression().accept(this);
                    int assignLine = unaryExpression.getOperation().getLine();
                    int assignCol = unaryExpression.getOperation().getColumn();

                    try {
                        if (localEnvironment != null && localEnvironment.existsInChain(name)) {
                            localEnvironment.assign(name, expression);
                        } else {
                            globalEnvironment.assign(name, expression);
                        }
                    } catch (com.mira.error.MiraError e) {
                        if (e.getLine() < 0) {
                            e.withLocation(assignLine, assignCol);
                        }
                        throw e;
                    }
                } else {
                    throw new AssertionError();
                }
            }
        }

        return null;
    }

    private void assignToAccess(AccessExpression accessExpression, java.util.function.Supplier<Object> valueSupplier) {
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
                default -> throw new AssertionError();
            }

            switch (referencedObject) {
                case ArrayExpression array -> {
                    var members = array.getMembers();
                    int size = members.size();
                    if (i < 0 || i >= size) {
                        throw new IndexOutOfBoundsError(i, size);
                    }
                    referencedObject = switch (members.get(i)) {
                        case ArrayExpression innerArray -> innerArray;
                        case ListExpression innerList -> innerList;
                        default -> throw new ImmutableCollectionError();
                    };
                }
                case ListExpression list -> {
                    var members = list.getMembers();
                    int size = members.size();
                    if (i < 0 || i >= size) {
                        throw new IndexOutOfBoundsError(i, size);
                    }
                    referencedObject = switch (members.get(i)) {
                        case ListExpression innerList -> innerList;
                        default -> throw new ImmutableCollectionError();
                    };
                }
                default -> throw new AssertionError("Reference is not a type of collection!");
            }
        }

        if (referencedObject instanceof Mutability mutability) {
            if (mutability.isMutable()) {
                Object evaluatedRhs = valueSupplier.get();
                Expression assignment;
                if (evaluatedRhs instanceof Expression e) {
                    assignment = e;
                } else {
                    // Collections store their elements as lazily-(re)evaluated Expression
                    // nodes, but evaluatedRhs is already a concrete runtime value here (e.g.
                    // a struct/Environment, list, or other object) - stringifying it into the
                    // token text would be lossy (structs have no meaningful toString()) and
                    // then get misread back as a bare identifier on the next read. Caching the
                    // real object directly makes DumbExpression return it verbatim instead.
                    DumbExpression dumb = new DumbExpression(
                            new Token(TokenType.EXPRESSION, String.valueOf(evaluatedRhs), 0, 0));
                    dumb.setCachedValue(evaluatedRhs);
                    assignment = dumb;
                }

                switch (referencedObject) {
                    case ArrayExpression array -> {
                        Object lastIdx = accessExpression.getIndecies().getLast().accept(this);
                        int arrayIdx = lastIdx instanceof Number n
                                ? (int) n.longValue()
                                : Integer.parseInt((String) lastIdx);
                        array.getMembers().set(arrayIdx, assignment);
                    }
                    case ListExpression list -> {
                        Object lastIdx = accessExpression.getIndecies().getLast().accept(this);
                        int listIdx = lastIdx instanceof Number n
                                ? (int) n.longValue()
                                : Integer.parseInt((String) lastIdx);
                        list.getMembers().set(listIdx, assignment);
                    }
                    case MapExpression map -> {
                        String key = String.valueOf(accessExpression.getIndecies().getLast().accept(this));
                        map.getEntries().put(key, assignment);
                    }
                    default -> throw new ImmutableCollectionError();
                }
            } else {
                throw new ImmutableCollectionError();
            }
        } else {
            throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
        }
    }

    private void assignToField(FieldAccessExpression fieldAccessExpression,
            java.util.function.Supplier<Object> valueSupplier) {
        Object object = fieldAccessExpression.getObject().accept(this);
        if (!(object instanceof Environment objectEnv)) {
            throw new FieldAccessError(fieldAccessExpression.getField());
        }
        Object value = valueSupplier.get();
        objectEnv.assign(fieldAccessExpression.getField(), value);
    }

    @Override
    public <T> T visitAssignExpression(AssignExpression e) {
        if (e.getReference() instanceof UnaryExpression u) {
            String name = String.valueOf(u.getRight().accept(this));
            Object value = e.getValue().accept(this);
            int line = u.getOperation().getLine();
            int col = u.getOperation().getColumn();
            try {
                if (localEnvironment != null && localEnvironment.existsInChain(name)) {
                    localEnvironment.assign(name, value);
                } else {
                    globalEnvironment.assign(name, value);
                }
            } catch (com.mira.error.MiraError err) {
                if (err.getLine() < 0) {
                    err.withLocation(line, col);
                }
                throw err;
            }
            return (T) value;
        }
        throw new AssertionError("Unsupported assignment target in assign expression");
    }

    @Override
    public Void visitReturn(Return ret) {
        notifyDebugger(ret.line);
        Object value = null;

        if (ret.getValue() != null) {
            value = ret.getValue().accept(this);
        }

        throw new ReturnSignal(value);
    }

    @Override
    public Object visitIf(If stmt) {
        notifyDebugger(stmt.line);
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
    public Object visitLoop(Loop stmt) {
        return stmt.isForeach() ? visitForeachLoop(stmt) : visitForLoop(stmt);
    }

    private Object visitForLoop(Loop stmt) {
        notifyDebugger(stmt.line);

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
        notifyDebugger(stmt.line);
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

    private Object visitForeachLoop(Loop stmt) {
        notifyDebugger(stmt.line);

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
                    List<Expression> snapshot = new ArrayList<>(list.getMembers());
                    for (Expression expr : snapshot) {
                        Object value = expr.accept(this);
                        try {
                            runBodyWithIterator(iteratorName, value, stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                case String string -> {
                    for (char c : string.toCharArray()) {
                        try {
                            runBodyWithIterator(iteratorName, String.valueOf(c), stmt.getBody());
                        } catch (ContinueSignal continueSignal) {
                        }
                    }
                }
                default -> throw new NotIterableError();
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
        notifyDebugger(stmt.line);
        Expression value = stmt.getValue();
        if (value instanceof ThrownException exception) {
            throw new ThrowSignal(exception.getIdentifier(), exception.accept(this));
        } else {
            throw new ThrowSignal(null, value.accept(this));
        }
    }

    @Override
    public Object visitThrownException(ThrownException expression) {
        return expression.getValue().accept(this);
    }

    @Override
    public Object visitTryCatch(TryCatch stmt) {
        notifyDebugger(stmt.line);
        int stackDepthBeforeTry = miraCallStack.size();
        try {
            runBodyInFreshScope(stmt.getTryBody());
        } catch (ThrowSignal signal) {
            boolean caught = false;
            for (CatchClause clause : stmt.getCatchClauses()) {
                String filter = clause.getTypeFilter();
                if (filter == null || filter.equals(signal.getExceptionType())) {
                    caught = true;
                    while (miraCallStack.size() > stackDepthBeforeTry) {
                        miraCallStack.poll();
                    }
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

    public void runBody(List<Node> body) {
        for (Node node : body) {
            switch (node) {
                case Expression expression -> {
                    notifyDebugger(expression.line);
                    expression.accept(this);
                }
                case Statement statement -> statement.accept(this);
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
            case Boolean b -> b;
            case NullValue n -> false;
            case Number n -> n.doubleValue() != 0;
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
            default -> throw new AssertionError("Unexpected condition type: " + condition.getClass());
        };
    }

    private boolean isHoisted(Node ast) {
        return ast instanceof FuncDecl || ast instanceof EnumDecl || (ast instanceof VarDecl vd && vd.isConst())
                || ast instanceof ComptimeBlock;
    }

    @Override
    public <T> T visitTypeofExpr(TypeofExpression expression) {
        Object val = expression.getExpr().accept(this);
        return (T) switch (val) {
            case null -> "null";
            case NullValue n -> "null";
            case Boolean b -> "bool";
            case Number n -> "number";
            case String s -> "string";
            case Promise p -> "promise";
            case Callable c -> "fn";
            case ListExpression l -> "list";
            case ArrayExpression a -> "array";
            case MapExpression m -> "map";
            case Environment e -> "object";
            default -> "unknown";
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

    @Override
    public <T> T visitExecBlock(ExecBlock expression) {
        Environment parent = expression.isIsolated()
                ? globalEnvironment
                : (localEnvironment != null ? localEnvironment : globalEnvironment);
        Environment blockEnv = new Environment(parent);
        Environment prevLocal = localEnvironment;
        localEnvironment = blockEnv;
        try {
            runBody(expression.getBody());
            return (T) NullValue.INSTANCE;
        } catch (ReturnSignal signal) {
            return (T) signal.getValue();
        } catch (ThrowSignal signal) {
            throw signal;
        } finally {
            localEnvironment = prevLocal;
        }
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

    public void presetComptimeConsts(Map<String, Object> consts) {
        this.presetComptimeConsts = consts;
    }

    public void setGlobalEnvironment(Environment globalEnvironment) {
        this.globalEnvironment = globalEnvironment;
    }

    public void reset() {
        globalEnvironment = new Environment();
        localEnvironment = null;
        ImportResolver.reset();
        ImportResolver.loadInternal(globalEnvironment);
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
