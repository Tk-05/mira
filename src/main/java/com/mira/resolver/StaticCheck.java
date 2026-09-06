package com.mira.resolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.cli.Flags;
import com.mira.error.DiagnosticFormatter;
import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.error.resolver.StaticCheckError.ArgumentTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.ArityMismatchError;
import com.mira.error.resolver.StaticCheckError.BinaryOperatorTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.BreakOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.ConstReassignmentError;
import com.mira.error.resolver.StaticCheckError.ContinueOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.DuplicateDeclarationError;
import com.mira.error.resolver.StaticCheckError.FieldAccessOnNonObjectError;
import com.mira.error.resolver.StaticCheckError.ImmutableCollectionStaticError;
import com.mira.error.resolver.StaticCheckError.LiteralNotCallableError;
import com.mira.error.resolver.StaticCheckError.MissingModuleDeclarationError;
import com.mira.error.resolver.StaticCheckError.MissingTypeAnnotationError;
import com.mira.error.resolver.StaticCheckError.NotIterableStaticError;
import com.mira.error.resolver.StaticCheckError.PostExprNaNStaticError;
import com.mira.error.resolver.StaticCheckError.PrivateAccessError;
import com.mira.error.resolver.StaticCheckError.PrivateImportError;
import com.mira.error.resolver.StaticCheckError.RangeStepZeroStaticError;
import com.mira.error.resolver.StaticCheckError.ReturnOutsideFunctionError;
import com.mira.error.resolver.StaticCheckError.ReturnTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.StaticAssertFailedError;
import com.mira.error.resolver.StaticCheckError.StaticAssertRuntimeValueError;
import com.mira.error.resolver.StaticCheckError.StructFieldTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.TypeMismatchError;
import com.mira.error.resolver.StaticCheckError.UnaryOperatorTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.UndeclaredVariableError;
import com.mira.error.resolver.StaticCheckError.UndefinedFunctionError;
import com.mira.error.resolver.StaticCheckError.UndefinedModuleSymbolError;
import com.mira.error.resolver.StaticCheckError.UndefinedObjectFieldStaticError;
import com.mira.error.resolver.StaticCheckError.UnknownModuleSymbolError;
import com.mira.error.resolver.StaticCheckError.UnknownNamespaceError;
import com.mira.error.resolver.StaticCheckError.UnknownTypeNameError;
import com.mira.error.resolver.StaticCheckError.VariableNotCallableError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.LibIndex;
import com.mira.lib.NativeInterfaceManifest;
import com.mira.lib.NativeInterfaceManifest.Signature;
import com.mira.lib.NativeLibLocator;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.TypeAnnotation;
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
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.TypeAliasDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.resolver.LintScope.VarInfo;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.warning.WarningCollector;
import com.mira.warning.WarningLevel;

public class StaticCheck {

    private final LintScope scope = new LintScope();
    private final Set<String> knownFunctions = new HashSet<>(LibIndex.GLOBAL_NAMES);
    private final Set<String> knownNamespaces = new HashSet<>();
    private final List<MiraError> errors = new ArrayList<>();
    private int loopDepth = 0;
    private int functionDepth = 0;
    private int branchDepth = 0;
    private boolean inComptimeBlock = false;
    private final Map<String, int[]> knownArities = new HashMap<>();
    private boolean isModule = false;
    private final Set<String> externallyUsed;
    private Path sourcePath;
    private final Set<String> checkedModuleImports = new HashSet<>();
    private final Map<String, Map<String, Boolean>> moduleAliasSymbols = new HashMap<>();
    private final Map<String, String> moduleAliasFileName = new HashMap<>();
    // Populated for `import native ... as alias` when the jar carries a
    // classloading-free ReflectiveLib manifest (see NativeInterfaceManifest) -
    // lets namespace calls into a native lib be argument-type-checked the same
    // way as a call to a declared Mira function, without ever loading the
    // native jar's actual Java classes during a check/LSP pass.
    private final Map<String, Map<String, Signature>> nativeNamespaceSignatures = new HashMap<>();
    private static final Map<String, CachedManifest> NATIVE_MANIFEST_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedManifest(long mtime, Map<String, Signature> signatures) {
    }
    private final Map<String, Node> varLiteralTypes = new HashMap<>();
    private final Map<String, FuncDecl> userFuncDecls = new HashMap<>();
    private final Map<String, EnumDecl> userEnumDecls = new HashMap<>();
    private Map<Path, String> openDocuments = Map.of();
    private final Map<String, Object> comptimeConsts;

    // Type-checking (gradual: only ever consulted/enforced when an explicit
    // annotation is present somewhere in the comparison - unannotated code
    // is never newly rejected).
    private static final Set<String> BUILTIN_TYPE_NAMES = Set.of(
            "Number", "String", "Bool", "List", "Array", "Map", "Object", "Fn", "Null", "Any", "Void");
    private final Map<String, MiraType> typeAliases = new HashMap<>();

    // Explicit, declared types only - unlike varLiteralTypes (inferred literal
    // shapes), these persist across reassignment/loops/branches: an explicit
    // annotation is a standing contract, not a best-effort guess.
    private final Map<String, MiraType> declaredVarTypes = new HashMap<>();

    // A broader, best-effort type inference cache alongside varLiteralTypes:
    // consulted only when there's no declared type AND no known literal-node
    // shape, so it can cover value sources varLiteralTypes structurally can't
    // represent as a Node (e.g. the result of calling a function with an
    // explicit return type) without disturbing any of varLiteralTypes' own
    // Node-based consumers (isZeroLiteral, structTemplateNames, etc.). Same
    // "put if determinable, else remove" drop-on-reassignment discipline.
    private final Map<String, MiraType> varInferredTypes = new HashMap<>();

    // A struct literal carries no name of its own - only the var that declares
    // it as a template does (`var point : struct {...};`). Populated in lockstep
    // with varLiteralTypes wherever a StructExpression is registered as a named
    // template, so inferMiraType can report a struct instance's real nominal
    // type instead of collapsing every struct/object alike to plain `Object`.
    private final Map<StructExpression, String> structTemplateNames = new java.util.IdentityHashMap<>();

    // The innermost enclosing named function, for checking `return` against its
    // declared return type - null while inside a lambda/object-or-struct method,
    // since those have no return-type annotation in v1.
    private final Deque<FuncDecl> functionStack = new java.util.LinkedList<>();

    public StaticCheck() {
        this(Set.of());
    }

    public StaticCheck(Set<String> externallyUsed) {
        this(externallyUsed, null);
    }

    public StaticCheck(Set<String> externallyUsed, Path sourcePath) {
        this(externallyUsed, sourcePath, Map.of());
    }

    public StaticCheck(Set<String> externallyUsed, Path sourcePath, Map<Path, String> openDocuments) {
        this(externallyUsed, sourcePath, openDocuments, null);
    }

    public StaticCheck(Set<String> externallyUsed, Path sourcePath, Map<Path, String> openDocuments,
            Map<String, Object> comptimeConsts) {
        this.externallyUsed = externallyUsed;
        this.sourcePath = sourcePath;
        this.openDocuments = openDocuments;
        this.comptimeConsts = comptimeConsts;
        knownFunctions.addAll(LibIndex.INTERNAL_NAMES);
        LibIndex.GLOBAL_ARITIES.forEach((name, arity) -> {
            if (arity >= 0) {
                knownArities.put(name, new int[]{arity, arity});
            }
        });
    }

    private void loadExternalFuncDecls() {
        if (sourcePath == null) {
            return;
        }
        Path dir = sourcePath.getParent();
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir)
                    .filter(p -> p.toString().endsWith(".mira") && !p.equals(sourcePath))
                    .forEach(p -> {
                        try {
                            String src = openDocuments.getOrDefault(p, Files.readString(p));
                            List<Node> siblingAst = new Parser().parseTokens(
                                    new Tokenizer().tokenize(src, false));
                            for (Node node : siblingAst) {
                                if (node instanceof FuncDecl f && f.isPublic()) {
                                    userFuncDecls.putIfAbsent(f.getName(), f);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
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
            case ComplexExpression e ->
                queue.addAll(e.getExpressions());
            case LambdaExpression e ->
                queue.addAll(e.getBody());
            case ExecBlock e ->
                queue.addAll(e.getBody());
            default -> {
            }
        }
    }

    public void check(List<Node> ast) {
        if (ast.isEmpty()) {
            errors.add(new MissingModuleDeclarationError());
            throw new MultipleStaticCheckErrors(errors);
        }
        if (!(ast.getFirst() instanceof ModuleDecl)) {
            errors.add(new MissingModuleDeclarationError());
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
                    if (scope.isDeclaredInCurrentScope(f.getName())) {
                        errors.add(new DuplicateDeclarationError(f.getName(), f.line, f.nameColumn));
                    } else {
                        scope.declareFunction(f.getName(), f.line, f.nameColumn);
                        knownFunctions.add(f.getName());
                        userFuncDecls.put(f.getName(), f);
                        if (f.getVariadicParam() == null) {
                            knownArities.put(f.getName(), new int[]{f.getArity(), f.getMaxArity()});
                        }
                        if (Flags.strictTypes) {
                            checkStrictAnnotations(f);
                        }
                    }
                }
                case EnumDecl e -> {
                    if (scope.isDeclaredInCurrentScope(e.getIdentifier())) {
                        errors.add(new DuplicateDeclarationError(e.getIdentifier(), e.line, 0));
                    } else {
                        scope.declare(e.getIdentifier(), e.line, 0, true);
                        scope.markUsed(e.getIdentifier());
                        userEnumDecls.put(e.getIdentifier(), e);
                    }
                }
                case ImportExpression imp ->
                    preDeclareImport(imp);
                case TypeAliasDecl t -> {
                    if (typeAliases.containsKey(t.getName()) || BUILTIN_TYPE_NAMES.contains(t.getName())) {
                        errors.add(new DuplicateDeclarationError(t.getName(), t.line, 0));
                    } else {
                        MiraType aliased = resolveNamedType(t.getAliasedType().name(),
                                t.getAliasedType().line(), t.getAliasedType().column());
                        if (aliased != null) {
                            typeAliases.put(t.getName(),
                                    t.getAliasedType().nullable() ? new MiraType.NullableType(aliased) : aliased);
                        }
                    }
                }
                default -> {
                }
            }
        }

        scope.markUsed("main");
        externallyUsed.forEach(scope::markUsed);
        loadExternalFuncDecls();

        resolveNodes(ast);
        popScope();

        if (Flags.verbose) {
            System.out.println(DiagnosticFormatter.formatInfo(
                    "static check: " + ast.size() + " top-level node(s), "
                    + errors.size() + " error(s), "
                    + WarningCollector.getWarnings().size() + " warning(s)"));
        }

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
                if (functionDepth == 0) {
                    errors.add(new ReturnOutsideFunctionError(stmt.line, stmt.column));
                }
                if (stmt.getValue() != null) {
                    resolveExpr(stmt.getValue());
                }
                FuncDecl enclosing = functionStack.peek();
                if (enclosing != null && enclosing.getReturnType() != null) {
                    MiraType expected = resolveTypeAnnotation(enclosing.getReturnType());
                    boolean bare = isBareReturn(stmt.getValue());
                    if (!bare) {
                        if (MiraType.isVoid(expected)) {
                            // any value at all is wrong for Void, regardless of its
                            // type - unlike ordinary mismatches this doesn't need the
                            // value's type to be inferable, so it bypasses checkAssignable's
                            // "skip if unknown" behavior
                            MiraType actual = inferMiraType(stmt.getValue());
                            errors.add(new ReturnTypeMismatchError(enclosing.getName(), "Void",
                                    actual != null ? MiraType.display(actual) : "a value",
                                    stmt.line, stmt.column));
                        } else {
                            checkAssignable(stmt.getValue(), expected, (exp, actual) -> errors.add(
                                    new ReturnTypeMismatchError(enclosing.getName(), exp, actual,
                                            stmt.line, stmt.column)));
                        }
                    } else if (expected != null && !MiraType.isVoid(expected)
                            && !MiraType.isAssignable(MiraType.NULL, expected)) {
                        errors.add(new ReturnTypeMismatchError(enclosing.getName(),
                                MiraType.display(expected), "Null", stmt.line, stmt.column));
                    }
                }
            }
            case If stmt ->
                resolveIf(stmt);
            case Loop stmt -> {
                if (stmt.isForeach()) {
                    resolveForeachLoop(stmt);
                } else {
                    resolveForLoop(stmt);
                }
            }
            case While stmt ->
                resolveWhile(stmt);
            case Block stmt ->
                resolveBlock(stmt);
            case Switch stmt ->
                resolveSwitch(stmt);
            case TryCatch stmt ->
                resolveTryCatch(stmt);
            case Throw stmt ->
                resolveExpr(stmt.getValue());
            case EnumDecl stmt -> {
                for (Expression value : stmt.getValues().values()) {
                    resolveExpr(value);
                }
            }
            case VarDestructure stmt ->
                resolveVarDestructure(stmt);
            case Lock stmt -> {
                resolveExpr(stmt.getMutex());
                resolveNodes(stmt.getBody());
            }
            case ComptimeBlock stmt -> {
                inComptimeBlock = true;
                resolveNodes(stmt.getBody());
                inComptimeBlock = false;
            }
            case StaticAssert stmt ->
                resolveStaticAssert(stmt);
            case TestCall stmt -> {
                resolveExpr(stmt.getName());
                resolveExpr(stmt.getTestFn());
            }
            case Break stmt -> {
                if (loopDepth == 0) {
                    errors.add(new BreakOutsideLoopError(stmt.line, stmt.column));
                }
            }
            case Continue stmt -> {
                if (loopDepth == 0) {
                    errors.add(new ContinueOutsideLoopError(stmt.line, stmt.column));
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
            case UnaryExpression e when "++".equals(e.getOperation().getLexeme())
            || "--".equals(e.getOperation().getLexeme()) -> {
                resolveExpr(e.getRight());
                DumbExpression varD = extractVarRef(e.getRight());
                if (varD != null) {
                    Node literal = varLiteralTypes.get(varD.getValue());
                    if (literal != null && isNonNumericLiteral(literal)) {
                        errors.add(new PostExprNaNStaticError(
                                varD.getValue(), varD.getLine(), varD.getColumn()));
                    }
                }
            }
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
            case UnaryExpression e when "-".equals(e.getOperation().getLexeme())
            || "~".equals(e.getOperation().getLexeme()) -> {
                if (e.getRight() != null) {
                    resolveExpr(e.getRight());
                    warnIfStringOperand(e.getRight(), e.getOperation());
                    checkUnaryOperandType(e);
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
                checkBinaryOperandTypes(e);
            }
            case BinaryExpression e when "/".equals(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
                if (isZeroLiteral(e.getRight())) {
                    WarningCollector.emit(WarningLevel.WARNING, "Division by zero", e.getOperator());
                } else {
                    DumbExpression varD = extractVarRef(e.getRight());
                    if (varD != null && isZeroLiteral(varLiteralTypes.get(varD.getValue()))) {
                        WarningCollector.emit(WarningLevel.WARNING, "Division by zero", e.getOperator());
                    }
                }
                warnIfStringOperand(e.getLeft(), e.getOperator());
                warnIfStringOperand(e.getRight(), e.getOperator());
                checkBinaryOperandTypes(e);
            }
            case BinaryExpression e when STRING_UNSAFE_OPERATORS.contains(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
                warnIfStringOperand(e.getLeft(), e.getOperator());
                warnIfStringOperand(e.getRight(), e.getOperator());
                checkBinaryOperandTypes(e);
            }
            case BinaryExpression e when COMPARISON_TYPE_CHECKED_OPERATORS.contains(e.getOperator().getLexeme()) -> {
                resolveExpr(e.getLeft());
                resolveExpr(e.getRight());
                checkComparisonOperandTypes(e);
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
                } else if (moduleAliasSymbols.containsKey(alias)) {
                    Map<String, Boolean> declared = moduleAliasSymbols.get(alias);
                    String fn = e.getFunctionName();
                    String modFile = moduleAliasFileName.get(alias);
                    if (!declared.containsKey(fn)) {
                        errors.add(new UndefinedModuleSymbolError(fn, modFile, e.getLine(), e.getColumn()));
                    } else if (!declared.get(fn)) {
                        errors.add(new PrivateAccessError(fn, modFile, e.getLine(), e.getColumn()));
                    }
                }
                scope.markUsed(alias);
                e.getArguments().forEach(this::resolveExpr);
                Map<String, Signature> nativeSigs = nativeNamespaceSignatures.get(alias);
                Signature nativeSig = nativeSigs != null ? nativeSigs.get(e.getFunctionName()) : null;
                if (nativeSig != null) {
                    checkNativeArgumentTypes(e, nativeSig);
                } else {
                    FuncDecl nsFn = userFuncDecls.get(e.getFunctionName());
                    if (nsFn != null) {
                        int min = nsFn.getArity();
                        int max = nsFn.getMaxArity();
                        int actual = e.getArguments().size();
                        if (min == max && actual != min) {
                            errors.add(new ArityMismatchError(e.getFunctionName(), min, actual, e.getLine(), e.getColumn()));
                        } else if (min != max && (actual < min || (max != -1 && actual > max))) {
                            errors.add(new ArityMismatchError(e.getFunctionName(), min, max, actual, e.getLine(), e.getColumn()));
                        }
                        if (!e.getArguments().isEmpty()) {
                            walkFuncWithParamTypes(nsFn, e.getArguments(), Map.of(), new HashSet<>(), e.getLine(), e.getColumn());
                        }
                    }
                }
            }
            case AccessExpression e -> {
                resolveExpr(e.getReference());
                e.getIndecies().forEach(this::resolveExpr);
            }
            case FieldAccessExpression e -> {
                resolveExpr(e.getObject());
                Node literalBase = resolveLiteralBase(e.getObject());
                if (literalBase != null && !(literalBase instanceof ObjectExpression)
                        && !(literalBase instanceof StructExpression)) {
                    String typeName = switch (literalBase) {
                        case ListExpression ignored ->
                            "list";
                        case ArrayExpression ignored ->
                            "array";
                        case MapExpression ignored ->
                            "map";
                        default ->
                            "non-object value";
                    };
                    int line = e.getObject().line;
                    int col = e.getObject() instanceof DumbExpression de
                            ? de.getColumn() + de.getValue().length() + 1 : 0;
                    errors.add(new FieldAccessOnNonObjectError(e.getField(), typeName, line, col));
                } else if (!e.isOptional() && literalBase instanceof ObjectExpression objExpr) {
                    String field = e.getField();
                    boolean fieldExists = objExpr.getVarDecls().stream().anyMatch(v -> field.equals(v.getName()))
                            || objExpr.getMethods().stream().anyMatch(m -> field.equals(m.getName()));
                    if (!fieldExists) {
                        DumbExpression varRef = extractVarRef(e.getObject());
                        String objectName = varRef != null ? varRef.getValue() : "object";
                        int line = varRef != null ? varRef.getLine() : e.getObject().line;
                        int col = varRef != null ? varRef.getColumn() + varRef.getValue().length() + 1 : 0;
                        errors.add(new UndefinedObjectFieldStaticError(field, objectName, line, col));
                    }
                } else if (!e.isOptional() && literalBase instanceof StructExpression structExpr) {
                    String field = e.getField();
                    boolean fieldExists = structExpr.getVarDecls().stream().anyMatch(v -> field.equals(v.getName()))
                            || structExpr.getMethods().stream().anyMatch(m -> field.equals(m.getName()));
                    if (!fieldExists) {
                        DumbExpression varRef = extractVarRef(e.getObject());
                        String objectName = varRef != null ? varRef.getValue() : "struct";
                        int line = varRef != null ? varRef.getLine() : e.getObject().line;
                        int col = varRef != null ? varRef.getColumn() + varRef.getValue().length() + 1 : 0;
                        errors.add(new UndefinedObjectFieldStaticError(field, objectName, line, col));
                    }
                }
                String possibleAlias = e.getObject().toString();
                if (moduleAliasSymbols.containsKey(possibleAlias)) {
                    Map<String, Boolean> declared = moduleAliasSymbols.get(possibleAlias);
                    String field = e.getField();
                    String modFile = moduleAliasFileName.get(possibleAlias);
                    int line = e.getObject().line;
                    int column = e.getObject() instanceof DumbExpression de
                            ? de.getColumn() + de.getValue().length() + 1
                            : 0;
                    if (!declared.containsKey(field)) {
                        errors.add(new UndefinedModuleSymbolError(field, modFile, line, column));
                    } else if (!declared.get(field)) {
                        errors.add(new PrivateAccessError(field, modFile, line, column));
                    }
                }
            }
            case MethodCallExpression e -> {
                resolveExpr(e.getObject());
                e.getArguments().forEach(this::resolveExpr);
                checkMethodArgumentTypes(e);
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
                        .forEach(v -> {
                            resolveExpr(v.getInitializer());
                            if (v.getType() != null) {
                                MiraType expected = resolveTypeAnnotation(v.getType());
                                checkAssignable(v.getInitializer(), expected, (exp, act) -> errors.add(
                                        new TypeMismatchError(v.getName(), exp, act,
                                                v.getInitializer().line, v.nameColumn)));
                            }
                        });
                for (var method : e.getMethods()) {
                    scope.push();
                    method.getParameters().forEach(p -> {
                        scope.declare(p.name(), method.line, 0, false);
                        if (p.type() != null) {
                            checkParamDefaultValue(p, resolveTypeAnnotation(p.type()));
                        }
                    });
                    if (method.getVariadicParam() != null) {
                        scope.declare(method.getVariadicParam(), method.line, 0, false);
                    }
                    scope.declare("this", 0, 0, false);
                    scope.markUsed("this");
                    e.getVarDecls().forEach(f -> {
                        scope.declare(f.getName(), 0, 0, false);
                        scope.markUsed(f.getName());
                    });
                    functionStack.push(null);
                    functionDepth++;
                    resolveBody(method.getBody());
                    functionDepth--;
                    functionStack.pop();
                    popScope();
                }
            }
            case StructExpression e -> {
                e.getVarDecls().stream()
                        .filter(v -> v.getInitializer() != null)
                        .forEach(v -> {
                            resolveExpr(v.getInitializer());
                            if (v.getType() != null) {
                                MiraType expected = resolveTypeAnnotation(v.getType());
                                checkAssignable(v.getInitializer(), expected, (exp, act) -> errors.add(
                                        new TypeMismatchError(v.getName(), exp, act,
                                                v.getInitializer().line, v.nameColumn)));
                            }
                        });
                for (var method : e.getMethods()) {
                    scope.push();
                    method.getParameters().forEach(p -> {
                        scope.declare(p.name(), method.line, 0, false);
                        if (p.type() != null) {
                            checkParamDefaultValue(p, resolveTypeAnnotation(p.type()));
                        }
                    });
                    if (method.getVariadicParam() != null) {
                        scope.declare(method.getVariadicParam(), method.line, 0, false);
                    }
                    scope.declare("this", 0, 0, false);
                    scope.markUsed("this");
                    e.getVarDecls().forEach(f -> {
                        scope.declare(f.getName(), 0, 0, false);
                        scope.markUsed(f.getName());
                    });
                    functionStack.push(null);
                    functionDepth++;
                    resolveBody(method.getBody());
                    functionDepth--;
                    functionStack.pop();
                    popScope();
                }
            }
            case StructInitExpression e -> {
                resolveExpr(e.getTarget());
                e.getOverrides().values().forEach(this::resolveExpr);
                Node templateLiteral = resolveLiteralBase(e.getTarget());
                if (templateLiteral instanceof StructExpression structExpr) {
                    DumbExpression varRef = extractVarRef(e.getTarget());
                    String templateName = varRef != null ? varRef.getValue() : "struct";
                    for (var entry : e.getOverrides().entrySet()) {
                        String key = entry.getKey();
                        VarDecl field = structExpr.getVarDecls().stream()
                                .filter(v -> key.equals(v.getName()))
                                .findFirst().orElse(null);
                        boolean exists = field != null
                                || structExpr.getMethods().stream().anyMatch(m -> key.equals(m.getName()));
                        if (!exists) {
                            errors.add(new UndefinedObjectFieldStaticError(key, templateName, entry.getValue().line, 0));
                        } else if (field != null && field.getType() != null) {
                            MiraType expected = resolveTypeAnnotation(field.getType());
                            Expression overrideValue = entry.getValue();
                            int column = expressionColumn(overrideValue, varRef != null ? varRef.getColumn() : 0);
                            int span = expressionSpan(overrideValue, key.length());
                            checkAssignable(overrideValue, expected, (exp, actual) -> errors.add(
                                    new StructFieldTypeMismatchError(key, templateName, exp, actual,
                                            overrideValue.line, column, span)));
                        }
                    }
                }
            }
            case AssignExpression e -> {
                if (e.getReference() instanceof UnaryExpression u
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
                        if (loopDepth == 0 && functionDepth == 0 && branchDepth == 0) {
                            Node rhsType = resolveRhsLiteralType(e.getValue());
                            if (rhsType != null) {
                                varLiteralTypes.put(name, rhsType);
                                varInferredTypes.remove(name);
                            } else {
                                varLiteralTypes.remove(name);
                                trackInferredType(name, e.getValue());
                            }
                        }
                        MiraType declared = declaredVarTypes.get(name);
                        if (declared != null) {
                            checkAssignable(e.getValue(), declared, (expected, actual) -> errors.add(
                                    new TypeMismatchError(name, expected, actual, d.getLine(), d.getColumn())));
                        }
                    }
                } else {
                    // mirrors resolveAssign's else-branch (the statement form of assignment) -
                    // this expression form previously had no equivalent at all, so a field
                    // target here ($obj.field : value used as an expression, not a statement)
                    // got neither the const-check nor any resolution of its own reference
                    checkFieldAssignment(e.getReference(), e.getValue());
                    resolveExpr(e.getReference());
                }
                resolveExpr(e.getValue());
            }
            case LambdaExpression e -> {
                scope.push();
                e.getParameters().forEach(p -> {
                    scope.declare(p.name(), 0, 0, false);
                    if (p.type() != null) {
                        checkParamDefaultValue(p, resolveTypeAnnotation(p.type()));
                    }
                });
                if (e.getVariadicParam() != null) {
                    scope.declare(e.getVariadicParam(), 0, 0, false);
                }
                functionStack.push(null);
                functionDepth++;
                resolveBody(e.getBody());
                functionDepth--;
                functionStack.pop();
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
                    if (isZeroLiteral(e.getStepsize())) {
                        DumbExpression d = (DumbExpression) e.getStepsize();
                        errors.add(new RangeStepZeroStaticError(d.getLine(), d.getColumn()));
                    } else {
                        DumbExpression varD = extractVarRef(e.getStepsize());
                        if (varD != null && isZeroLiteral(varLiteralTypes.get(varD.getValue()))) {
                            errors.add(new RangeStepZeroStaticError(varD.getLine(), varD.getColumn()));
                        }
                    }
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
        if (expr.getCallee() instanceof DumbExpression callee) {
            if (isIdentifier(callee)) {
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
                    FuncDecl fn = userFuncDecls.get(name);
                    if (fn != null && !expr.getArguments().isEmpty()) {
                        checkCallParamFieldAccesses(fn, expr.getArguments());
                        checkArgumentTypes(fn, expr.getArguments(), callee);
                    }
                }
            } else {
                errors.add(new LiteralNotCallableError(
                        callee.getValue(), callee.getLine(), callee.getColumn()));
            }
        } else {
            resolveExpr(expr.getCallee());
            // calling a lambda held in a variable ($f(args), not a bareword function
            // name) never went through argument type checking before - only direct
            // calls to a named top-level function did. Reuses the same varLiteralTypes
            // tracking that already remembers a variable's last-known literal shape
            // (now including lambdas, see isKnownLiteral) to recover the lambda's own
            // parameter list; silently skips when that shape isn't known or isn't a lambda.
            if (expr.getCallee() instanceof UnaryExpression u
                    && "$".equals(u.getOperation().getLexeme())
                    && u.getRight() instanceof DumbExpression d
                    && isIdentifier(d)) {
                checkVariableCallable(u, d);
                if (!expr.getArguments().isEmpty()) {
                    Node tracked = varLiteralTypes.get(d.getValue());
                    if (tracked instanceof LambdaExpression lambda) {
                        checkArgumentTypes(d.getValue(), lambda.getParameters(), expr.getArguments(),
                                d.getLine(), d.getColumn());
                    }
                }
            }
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
        scope.declare(stmt.getName(), stmt.line, stmt.nameColumn, stmt.isConst(), inComptimeBlock && stmt.isConst());
        if (stmt.getInitializer() != null && isKnownLiteral(stmt.getInitializer())) {
            varLiteralTypes.put(stmt.getName(), stmt.getInitializer());
            if (stmt.getInitializer() instanceof StructExpression st) {
                structTemplateNames.put(st, stmt.getName());
            }
        } else if (stmt.getInitializer() instanceof StructInitExpression si) {
            Node templateLiteral = resolveLiteralBase(si.getTarget());
            if (templateLiteral instanceof StructExpression) {
                varLiteralTypes.put(stmt.getName(), templateLiteral);
            }
        } else if (stmt.getInitializer() != null) {
            trackInferredType(stmt.getName(), stmt.getInitializer());
        }
        if (stmt.getType() != null) {
            MiraType declared = resolveTypeAnnotation(stmt.getType());
            if (declared != null) {
                declaredVarTypes.put(stmt.getName(), declared);
                if (stmt.getInitializer() != null) {
                    checkAssignable(stmt.getInitializer(), declared, (expected, actual) -> errors.add(
                            new TypeMismatchError(stmt.getName(), expected, actual, stmt.line, stmt.nameColumn)));
                }
            }
        }
    }

    private void resolveStaticAssert(StaticAssert stmt) {
        int before = errors.size();
        resolveExpr(stmt.getCondition());
        checkComptimeExpr(stmt.getCondition());
        if (stmt.getMessage() != null) {
            resolveExpr(stmt.getMessage());
            checkComptimeExpr(stmt.getMessage());
        }
        if (comptimeConsts != null && errors.size() == before) {
            evaluateStaticAssert(stmt);
        }
    }

    private void evaluateStaticAssert(StaticAssert stmt) {
        Interpreter evalInterpreter = new Interpreter();
        comptimeConsts.forEach((name, value) -> {
            if (!evalInterpreter.getGlobalEnvironment().existsInChain(name)) {
                evalInterpreter.getGlobalEnvironment().defineConst(name, value);
            }
        });
        try {
            evalInterpreter.visitStaticAssert(stmt);
        } catch (StaticAssertFailedError e) {
            errors.add(e);
        }
    }

    private void checkComptimeExpr(Node expr) {
        switch (expr) {
            case DumbExpression e -> {
            }
            case UnaryExpression e when "$".equals(e.getOperation().getLexeme()) -> {
                if (e.getRight() instanceof DumbExpression d && isIdentifier(d)) {
                    String name = d.getValue();
                    if (!scope.isComptime(name)) {
                        errors.add(new StaticAssertRuntimeValueError(name, d.getLine(), d.getColumn()));
                    }
                } else if (e.getRight() != null) {
                    checkComptimeExpr(e.getRight());
                }
            }
            case UnaryExpression e -> {
                if (e.getRight() != null) {
                    checkComptimeExpr(e.getRight());
                }
            }
            case BinaryExpression e -> {
                checkComptimeExpr(e.getLeft());
                checkComptimeExpr(e.getRight());
            }
            case TernaryExpression e -> {
                checkComptimeExpr(e.getCondition());
                checkComptimeExpr(e.getThenExpr());
                checkComptimeExpr(e.getElseExpr());
            }
            default ->
                errors.add(new StaticAssertRuntimeValueError("expression", 0, 0));
        }
    }

    private void resolveFuncDecl(FuncDecl stmt) {
        knownFunctions.add(stmt.getName());
        if (stmt.getVariadicParam() == null) {
            knownArities.put(stmt.getName(), new int[]{stmt.getArity(), stmt.getMaxArity()});
        }
        scope.push();
        // params shadow any same-named declaredVarTypes entry from an outer/sibling
        // function - save so it can be restored on exit, since unlike varLiteralTypes
        // (best-effort, freely dropped) declaredVarTypes drives hard errors and must
        // not leak across unrelated functions that happen to share a parameter name.
        Map<String, MiraType> savedParamTypes = new HashMap<>();
        Set<String> typedParams = new HashSet<>();
        stmt.getParameters().forEach(p -> {
            scope.declare(p.name(), stmt.line, p.column(), false);
            if (p.type() != null) {
                MiraType paramType = resolveTypeAnnotation(p.type());
                if (paramType != null) {
                    typedParams.add(p.name());
                    savedParamTypes.put(p.name(), declaredVarTypes.get(p.name()));
                    declaredVarTypes.put(p.name(), paramType);
                    checkParamDefaultValue(p, paramType);
                }
            }
        });
        if (stmt.getVariadicParam() != null) {
            scope.declare(stmt.getVariadicParam(), stmt.line, 0, false);
        }
        if (stmt.getReturnType() != null) {
            resolveTypeAnnotation(stmt.getReturnType());
        }
        functionStack.push(stmt);
        functionDepth++;
        resolveBody(stmt.getBody());
        functionDepth--;
        functionStack.pop();
        typedParams.forEach(name -> {
            MiraType prior = savedParamTypes.get(name);
            if (prior != null) {
                declaredVarTypes.put(name, prior);
            } else {
                declaredVarTypes.remove(name);
            }
        });
        if (hasAnyReturn(stmt.getBody()) && !alwaysReturns(stmt.getBody())) {
            warn("Function '" + stmt.getName() + "' may not return a value on all code paths",
                    stmt.line, stmt.nameColumn, stmt.getName().length());
        }
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
                if (loopDepth == 0 && functionDepth == 0 && branchDepth == 0) {
                    Node rhsType = resolveRhsLiteralType(stmt.getExpression());
                    if (rhsType != null) {
                        varLiteralTypes.put(name, rhsType);
                        varInferredTypes.remove(name);
                    } else {
                        varLiteralTypes.remove(name);
                        trackInferredType(name, stmt.getExpression());
                    }
                }
                MiraType declared = declaredVarTypes.get(name);
                if (declared != null) {
                    checkAssignable(stmt.getExpression(), declared, (expected, actual) -> errors.add(
                            new TypeMismatchError(name, expected, actual, d.getLine(), d.getColumn())));
                }
            }
        } else {
            checkFieldAssignment(stmt.getReference(), stmt.getExpression());
            resolveExpr(stmt.getReference());
        }
        resolveExpr(stmt.getExpression());
    }

    private void resolveIf(If stmt) {
        resolveExpr(stmt.getCondition());
        scope.push();
        branchDepth++;
        resolveBody(stmt.getThenBody());
        branchDepth--;
        popScope();
        if (stmt.getElseBody() != null) {
            scope.push();
            branchDepth++;
            resolveBody(stmt.getElseBody());
            branchDepth--;
            popScope();
        }
    }

    private void resolveForLoop(Loop stmt) {
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

    private void resolveForeachLoop(Loop stmt) {
        resolveExpr(stmt.getCollection());
        if (isNonIterableLiteral(stmt.getCollection())) {
            errors.add(new NotIterableStaticError(stmt.line, stmt.column));
        }
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
        if (isNonIterableLiteral(stmt.getInitializer())) {
            errors.add(new NotIterableStaticError(stmt.line, 0));
        }
        for (String name : stmt.getNames()) {
            if (scope.isDeclaredInCurrentScope(name)) {
                errors.add(new DuplicateDeclarationError(name, stmt.line, 0));
            } else {
                scope.declare(name, stmt.line, 0, false);
            }
        }
    }

    private void preDeclareImport(ImportExpression expr) {
        if (expr.isSelective()) {
            if (expr.isExternalModule()) {
                checkSelectiveModuleImport(expr);
            }
            if (expr.getNamespace() != null) {
                scope.declareImport(expr.getNamespace(), expr.line);
                knownNamespaces.add(expr.getNamespace());
            } else {
                for (String fn : expr.getSelectedFunctions()) {
                    scope.declareImport(fn, expr.line);
                    knownFunctions.add(fn);
                }
            }
        } else if (expr.getNamespace() != null) {
            scope.declareImport(expr.getNamespace(), expr.line);
            knownNamespaces.add(expr.getNamespace());
            if (expr.isExternalModule()) {
                loadModuleSymbolsForAlias(expr);
            } else if (expr.isNativeJar()) {
                loadNativeLibSignatures(expr);
            }
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

    private void loadNativeLibSignatures(ImportExpression expr) {
        String rawPath = expr.getModule().replace("\"", "");
        Path importingFile = sourcePath != null ? sourcePath : com.mira.cli.Flags.inputPath.get();
        Path jarPath = NativeLibLocator.locate(rawPath, importingFile);
        if (jarPath == null) {
            return;
        }
        Map<String, Signature> signatures = readNativeManifest(jarPath);
        if (!signatures.isEmpty()) {
            nativeNamespaceSignatures.put(expr.getNamespace(), signatures);
        }
    }

    private static Map<String, Signature> readNativeManifest(Path jarPath) {
        try {
            String key = jarPath.toAbsolutePath().toString();
            long mtime = Files.getLastModifiedTime(jarPath).toMillis();
            CachedManifest cached = NATIVE_MANIFEST_CACHE.get(key);
            if (cached != null && cached.mtime() == mtime) {
                return cached.signatures();
            }
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
                java.util.jar.JarEntry entry = jar.getJarEntry(NativeInterfaceManifest.RESOURCE_PATH);
                if (entry == null) {
                    return Map.of();
                }
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    Map<String, Signature> signatures = NativeInterfaceManifest.read(in);
                    NATIVE_MANIFEST_CACHE.put(key, new CachedManifest(mtime, signatures));
                    return signatures;
                }
            }
        } catch (java.io.IOException e) {
            return Map.of();
        }
    }

    private static java.nio.file.Path resolveModuleFile(java.nio.file.Path base, String rawPath) {
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

    private void checkSelectiveModuleImport(ImportExpression expr) {
        if (!checkedModuleImports.add(expr.getModule())) {
            return;
        }
        java.nio.file.Path base = sourcePath != null
                ? sourcePath.getParent()
                : (com.mira.cli.Flags.inputPath.get() != null ? com.mira.cli.Flags.inputPath.get().getParent() : null);
        if (base == null) {
            return;
        }

        String rawPath = expr.getModule().replace("\"", "");
        java.nio.file.Path modulePath = resolveModuleFile(base, rawPath);
        if (!java.nio.file.Files.exists(modulePath)) {
            return;
        }

        try {
            String src = java.nio.file.Files.readString(modulePath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));

            Map<String, Boolean> declared = new HashMap<>();
            for (Node n : modAst) {
                switch (n) {
                    case FuncDecl fd ->
                        declared.put(fd.getName(), fd.isPublic());
                    case VarDecl vd ->
                        declared.put(vd.getName(), vd.isPublic());
                    case EnumDecl ed ->
                        declared.put(ed.getIdentifier(), ed.isPublic());
                    default -> {
                    }
                }
            }

            String moduleName = modulePath.getFileName().toString();
            for (String name : expr.getSelectedFunctions()) {
                if (!declared.containsKey(name)) {
                    errors.add(new UnknownModuleSymbolError(name, moduleName, expr.line, 0));
                } else if (!declared.get(name)) {
                    errors.add(new PrivateImportError(name, moduleName, expr.line, 0));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void loadModuleSymbolsForAlias(ImportExpression expr) {
        java.nio.file.Path base = sourcePath != null
                ? sourcePath.getParent()
                : (com.mira.cli.Flags.inputPath.get() != null ? com.mira.cli.Flags.inputPath.get().getParent() : null);
        if (base == null) {
            return;
        }

        String rawPath = expr.getModule().replace("\"", "");
        java.nio.file.Path modulePath = resolveModuleFile(base, rawPath);
        if (!java.nio.file.Files.exists(modulePath)) {
            return;
        }

        try {
            String src = java.nio.file.Files.readString(modulePath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));

            Map<String, Boolean> declared = new HashMap<>();
            for (Node n : modAst) {
                switch (n) {
                    case FuncDecl fd ->
                        declared.put(fd.getName(), fd.isPublic());
                    case VarDecl vd ->
                        declared.put(vd.getName(), vd.isPublic());
                    case EnumDecl ed ->
                        declared.put(ed.getIdentifier(), ed.isPublic());
                    default -> {
                    }
                }
            }

            String alias = expr.getNamespace();
            moduleAliasSymbols.put(alias, declared);
            moduleAliasFileName.put(alias, modulePath.getFileName().toString());
        } catch (Exception ignored) {
        }
    }

    private void popScope() {
        checkUnused(scope.pop());
    }

    private void resolveBody(List<Node> body) {
        boolean terminated = false;
        for (Node node : body) {
            if (terminated) {
                WarningCollector.emit(WarningLevel.WARNING, "Unreachable code",
                        lineOf(node), columnOf(node), spanOf(node));
            } else {
                resolveNode(node);
                if (node instanceof Return || node instanceof Throw) {
                    terminated = true;
                }
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
            case Loop s ->
                s.line;
            case While s ->
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

    private static int columnOf(Node node) {
        return switch (node) {
            case VarDecl s ->
                s.column;
            case FuncDecl s ->
                s.column;
            case Return s ->
                s.column;
            case If s ->
                s.column;
            case Loop s ->
                s.column;
            case While s ->
                s.column;
            case Block s ->
                s.column;
            case Switch s ->
                s.column;
            case TryCatch s ->
                s.column;
            case Throw s ->
                s.column;
            case Assign s ->
                s.column;
            case CallExpression e when e.getCallee() instanceof DumbExpression d ->
                d.getColumn();
            default ->
                0;
        };
    }

    private static int spanOf(Node node) {
        return switch (node) {
            case VarDecl s ->
                s.getName().length();
            case FuncDecl s ->
                s.getName().length();
            case Return ignored ->
                "return".length();
            case Throw ignored ->
                "throw".length();
            case If ignored ->
                "if".length();
            case Loop ignored ->
                "for".length();
            case While ignored ->
                "while".length();
            case Switch ignored ->
                "switch".length();
            case TryCatch ignored ->
                "try".length();
            case CallExpression e when e.getCallee() instanceof DumbExpression d ->
                d.getValue().length();
            default ->
                1;
        };
    }

    private void warn(String message, int line, int column, int span) {
        WarningCollector.emit(WarningLevel.WARNING, message, line, column, span);
    }

    private static final Set<String> STRING_UNSAFE_OPERATORS = Set.of(
            "-", "*", "%", "\\%", "**", "&", "|", "^", "<<", ">>");

    private static final Set<String> ARITHMETIC_TYPE_CHECKED_OPERATORS = Set.of(
            "+", "-", "*", "/", "%", "\\%", "**");

    // "==" / "!=" are deliberately excluded: comparing an explicitly-typed value
    // against e.g. a nullable's `null` check is a common, legitimate pattern this
    // check must not flag - only ordering comparisons are unambiguously nonsensical
    // across mismatched named types.
    private static final Set<String> COMPARISON_TYPE_CHECKED_OPERATORS = Set.of("<", ">", "<=", ">=");

    private static boolean isStringLiteral(Node n) {
        return n instanceof DumbExpression d && d.getTokenType() == TokenType.STRING_LITERAL;
    }

    private static boolean isNonStringLiteral(Node n) {
        if (!(n instanceof DumbExpression d)) {
            return false;
        }
        return d.getTokenType() != TokenType.STRING_LITERAL && !isIdentifier(d);
    }

    private void warnIfStringOperand(Node operand, Token operator) {
        if (isStringLiteral(operand)) {
            WarningCollector.emit(WarningLevel.WARNING,
                    "Operator '" + operator.getLexeme() + "' used on a String literal",
                    operator);
        }
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

    /**
     * A bare {@code return;} isn't represented as a {@code null} value in the
     * AST - {@code Parser.parseReturn} fills in a synthetic {@code 0.0} literal
     * token at line/column {@code -1} so downstream code always has an
     * {@code Expression} to work with. This tells that sentinel apart from a
     * real, user-written return value (including a genuine
     * {@code return 0.0;}).
     */
    private static boolean isBareReturn(Expression value) {
        return value instanceof DumbExpression d && d.getLine() == -1;
    }

    private static boolean isKnownLiteral(Node n) {
        return n instanceof ListExpression
                || n instanceof ArrayExpression
                || n instanceof MapExpression
                || n instanceof ObjectExpression
                || n instanceof StructExpression
                || n instanceof LambdaExpression
                || (n instanceof DumbExpression d && !isIdentifier(d))
                // `-1`/`~1`/`!true` are each a UnaryExpression wrapping the literal
                // token, not themselves a DumbExpression - without this, e.g. `var x :
                // -1;` was invisible to every varLiteralTypes-based check (E332 calling
                // it, or the division-by-zero/bareword warnings elsewhere)
                || isInvertedLiteral(n);
    }

    private static boolean isInvertedLiteral(Node n) {
        return n instanceof UnaryExpression u && u.getRight() instanceof DumbExpression d
                && switch (u.getOperation().getLexeme()) {
            case "-", "~" ->
                isNumericLiteralToken(d);
            case "!" ->
                isBooleanLiteralToken(d);
            default ->
                false;
        };
    }

    private static boolean isNumericLiteralToken(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL || isIdentifier(d)) {
            return false;
        }
        String value = d.getValue();
        return !value.isEmpty() && Character.isDigit(value.charAt(0));
    }

    private static boolean isBooleanLiteralToken(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL || isIdentifier(d)) {
            return false;
        }
        return "true".equals(d.getValue()) || "false".equals(d.getValue());
    }

    private void trackInferredType(String name, Node valueExpr) {
        MiraType inferred = inferMiraType(valueExpr);
        if (inferred != null) {
            varInferredTypes.put(name, inferred);
        } else {
            varInferredTypes.remove(name);
        }
    }

    private Node resolveRhsLiteralType(Node rhs) {
        Node lit = resolveLiteralBase(rhs);
        if (lit != null) {
            return lit;
        }
        if (rhs instanceof StructInitExpression si) {
            Node t = resolveLiteralBase(si.getTarget());
            if (t instanceof StructExpression) {
                return t;
            }
        }
        if (rhs instanceof TernaryExpression te) {
            return agreeingLiteralType(List.of(te.getThenExpr(), te.getElseExpr()));
        }
        if (rhs instanceof SwitchExpression se) {
            List<Node> branches = new java.util.ArrayList<>();
            se.getCases().forEach(c -> branches.add(c.result()));
            if (se.getDefaultExpr() != null) {
                branches.add(se.getDefaultExpr());
            }
            return agreeingLiteralType(branches);
        }
        return null;
    }

    /**
     * When every branch of a ternary/switch resolves to the same known literal
     * shape (e.g. both sides of `cond ? 1 : 2` are Numbers), tracking survives
     * a reassignment through it instead of being dropped as "unknown" - a
     * reassignment like `$a : cond ? 1 : 2;` (previously invisible to
     * varLiteralTypes, since neither branch is itself a literal *node* the way
     * a plain `$a : 1;` reassignment is) now correctly updates what's tracked
     * for `a`. Returns one representative branch's literal node (any one works,
     * since callers only ever consult its *type* via literalNodeToType), or
     * null if the branches disagree or any branch's own shape isn't known.
     */
    private Node agreeingLiteralType(List<Node> branches) {
        Node first = null;
        MiraType firstType = null;
        for (Node branch : branches) {
            Node lit = resolveRhsLiteralType(branch);
            if (lit == null) {
                return null;
            }
            MiraType type = literalNodeToType(lit);
            if (type == null) {
                return null;
            }
            if (first == null) {
                first = lit;
                firstType = type;
            } else if (!sameNamedType(firstType, type)) {
                return null;
            }
        }
        return first;
    }

    private Node resolveLiteralBase(Node objectExpr) {
        if (isKnownLiteral(objectExpr)) {
            return objectExpr;
        }
        if (objectExpr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && isIdentifier(d)) {
            return varLiteralTypes.get(d.getValue());
        }
        return null;
    }

    // --- Type checking -----------------------------------------------------
    //
    // Gradual by construction: every check below only fires when an explicit
    // annotation is present on at least one side of the comparison, and
    // inferMiraType() returns null (meaning "unknown, don't check") far more
    // often than it returns a concrete type - unannotated code is never
    // newly rejected.
    /**
     * Resolves a parsed TypeAnnotation into a MiraType, reporting
     * UnknownTypeNameError for bad names.
     */
    private MiraType resolveTypeAnnotation(TypeAnnotation ann) {
        if (ann == null) {
            return null;
        }
        MiraType base = resolveNamedType(ann.name(), ann.line(), ann.column());
        if (base == null) {
            return null;
        }
        return ann.nullable() ? new MiraType.NullableType(base) : base;
    }

    private MiraType resolveNamedType(String name, int line, int column) {
        if ("Any".equals(name)) {
            return MiraType.ANY;
        }
        if (BUILTIN_TYPE_NAMES.contains(name)) {
            return new MiraType.NamedType(name);
        }
        if (typeAliases.containsKey(name)) {
            return typeAliases.get(name);
        }
        if (scope.isDeclared(name) || knownFunctions.contains(name)) {
            // A declared struct/enum/other name used as a type: accepted as an
            // opaque nominal type for now (no deeper checking against it yet -
            // that's struct nominal typing, a later milestone), rather than
            // flagged as unknown.
            return new MiraType.NamedType(name);
        }
        errors.add(new UnknownTypeNameError(name, line, column));
        return null;
    }

    /**
     * Infers the MiraType of an expression, when there's enough information to
     * be confident - a variable with a declared type, a literal, a struct init,
     * or a call to a function with a declared return type. Returns null
     * (meaning "unknown, skip the check") for anything else, notably binary/
     * unary expressions and calls to unannotated functions - guessing wrong
     * there would produce a false positive, which gradual typing must never do.
     */
    private MiraType inferMiraType(Node expr) {
        if (expr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && isIdentifier(d)) {
            MiraType declared = declaredVarTypes.get(d.getValue());
            if (declared != null) {
                return declared;
            }
            Node inferredLiteral = varLiteralTypes.get(d.getValue());
            if (inferredLiteral != null) {
                return literalNodeToType(inferredLiteral);
            }
            return varInferredTypes.get(d.getValue());
        }
        if (expr instanceof StructInitExpression si) {
            Node templateLiteral = resolveLiteralBase(si.getTarget());
            // Resolves to the specific struct template's nominal name when known
            // (e.g. NamedType("point") for `$point{...}`), falling back to the
            // generic structural Object only if the template can't be traced.
            return templateLiteral instanceof StructExpression
                    ? literalNodeToType(templateLiteral) : MiraType.OBJECT;
        }
        if (expr instanceof CallExpression call && call.getCallee() instanceof DumbExpression callee
                && isIdentifier(callee)) {
            FuncDecl fn = userFuncDecls.get(callee.getValue());
            if (fn != null && fn.getReturnType() != null) {
                return resolveTypeAnnotation(fn.getReturnType());
            }
            return null;
        }
        if (expr instanceof FieldAccessExpression fae && fae.getObject() instanceof DumbExpression obj
                && isIdentifier(obj)) {
            EnumDecl enumDecl = userEnumDecls.get(obj.getValue());
            if (enumDecl != null && enumDecl.getValues().containsKey(fae.getField())) {
                return new MiraType.NamedType(enumDecl.getIdentifier());
            }
            return null;
        }
        return literalNodeToType(expr);
    }

    /**
     * Maps one of isKnownLiteral()'s recognized literal shapes to a MiraType,
     * or null if not a literal.
     */
    private MiraType literalNodeToType(Node n) {
        return switch (n) {
            case ListExpression ignored ->
                MiraType.LIST;
            case ArrayExpression ignored ->
                MiraType.ARRAY;
            case MapExpression ignored ->
                MiraType.MAP;
            case ObjectExpression ignored ->
                MiraType.OBJECT;
            case StructExpression st -> {
                String name = structTemplateNames.get(st);
                yield name != null ? new MiraType.NamedType(name) : MiraType.OBJECT;
            }
            case DumbExpression d ->
                // covers plain literal tokens (numbers, true/false/null,
                // string literals) AND bare identifier-shaped tokens - a
                // bareword read without `$` deterministically evaluates to
                // its own text as a String at runtime (Mira's bareword
                // semantics), so it's just as safe to type as any other
                // literal, not "unknown" the way an actual $-reference is
                literalTokenType(d);
            case UnaryExpression u when isInvertedLiteral(u) ->
                "!".equals(u.getOperation().getLexeme()) ? MiraType.BOOL : MiraType.NUMBER;
            default ->
                null;
        };
    }

    private MiraType literalTokenType(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL) {
            return MiraType.STRING;
        }
        String value = d.getValue();
        if ("true".equals(value) || "false".equals(value)) {
            return MiraType.BOOL;
        }
        if ("null".equals(value)) {
            return MiraType.NULL;
        }
        if (!value.isEmpty() && Character.isDigit(value.charAt(0))) {
            return MiraType.NUMBER;
        }
        // A bareword (missing '$') - Mira treats this as a string literal at runtime.
        return MiraType.STRING;
    }

    /**
     * Checks a value's inferred type against an expected type, adding the given
     * error if it's a definite mismatch.
     */
    private void checkAssignable(Node valueExpr, MiraType expected, java.util.function.BiConsumer<String, String> onMismatch) {
        if (expected == null) {
            return;
        }
        // a ternary/switch has no type of its own - check each branch against the
        // same expected type individually, rather than trying to first infer one
        // combined type for the whole expression (inferMiraType has no case for
        // either shape, so without this every branch was silently unchecked)
        if (valueExpr instanceof TernaryExpression te) {
            checkAssignable(te.getThenExpr(), expected, onMismatch);
            checkAssignable(te.getElseExpr(), expected, onMismatch);
            return;
        }
        if (valueExpr instanceof SwitchExpression se) {
            for (var c : se.getCases()) {
                checkAssignable(c.result(), expected, onMismatch);
            }
            if (se.getDefaultExpr() != null) {
                checkAssignable(se.getDefaultExpr(), expected, onMismatch);
            }
            return;
        }
        MiraType actual = inferMiraType(valueExpr);
        if (actual != null && !MiraType.isAssignable(actual, expected)) {
            onMismatch.accept(MiraType.display(expected), MiraType.display(actual));
        }
    }

    private void checkStrictAnnotations(FuncDecl fn) {
        for (Parameter p : fn.getParameters()) {
            if (p.type() == null) {
                errors.add(new MissingTypeAnnotationError(fn.getName(),
                        "parameter '" + p.name() + "'", fn.line, fn.nameColumn));
            }
        }
        if (fn.getReturnType() == null) {
            errors.add(new MissingTypeAnnotationError(fn.getName(),
                    "its return type", fn.line, fn.nameColumn));
        }
    }

    private void checkArgumentTypes(FuncDecl fn, List<Expression> args, DumbExpression callee) {
        checkArgumentTypes(fn.getName(), fn.getParameters(), args, callee.getLine(), callee.getColumn());
    }

    /**
     * Shared by every call shape that can carry typed parameters: a plain
     * top-level function call, a method call on an object/struct instance, and
     * calling a lambda value held in a variable - callableName is whatever
     * reads naturally in the error message (the function's own name, the
     * method's name, or the variable holding the lambda).
     */
    private void checkArgumentTypes(String callableName, List<Parameter> params, List<Expression> args,
            int fallbackLine, int fallbackColumn) {
        for (int i = 0; i < Math.min(params.size(), args.size()); i++) {
            Parameter param = params.get(i);
            if (param.type() == null) {
                continue;
            }
            Expression argNode = args.get(i);
            MiraType expected = resolveTypeAnnotation(param.type());
            // point at the specific argument when it has a real position
            // (a literal or a $-reference), otherwise fall back to the call
            // site itself - either way a genuine token position, never a
            // coincidental column that happens to land somewhere else on the line
            int line = argNode.line > 0 ? argNode.line : fallbackLine;
            int column = expressionColumn(argNode, fallbackColumn);
            int span = expressionSpan(argNode, callableName.length());
            checkAssignable(argNode, expected, (exp, actual) -> errors.add(
                    new ArgumentTypeMismatchError(callableName, param.name(), exp, actual, line, column, span)));
        }
    }

    /**
     * Same idea as {@link #checkArgumentTypes}, but for a call into a native
     * lib whose signature came from a {@link NativeInterfaceManifest} rather
     * than a Mira {@code FuncDecl} - the manifest carries only types, no
     * parameter names, so arguments are labeled positionally ('#1', '#2', ...).
     */
    private void checkNativeArgumentTypes(NamespaceCallExpression e, Signature sig) {
        List<String> paramTypes = sig.paramTypes();
        int expected = paramTypes.size();
        int actual = e.getArguments().size();
        if (expected != actual) {
            errors.add(new ArityMismatchError(e.getFunctionName(), expected, actual, e.getLine(), e.getColumn()));
            return;
        }
        for (int i = 0; i < expected; i++) {
            MiraType expectedType = resolveNamedType(paramTypes.get(i), e.getLine(), e.getColumn());
            Expression argNode = e.getArguments().get(i);
            int line = argNode.line > 0 ? argNode.line : e.getLine();
            int column = expressionColumn(argNode, e.getColumn());
            int span = expressionSpan(argNode, e.getFunctionName().length());
            String argLabel = "#" + (i + 1);
            checkAssignable(argNode, expectedType, (exp, act) -> errors.add(
                    new ArgumentTypeMismatchError(e.getFunctionName(), argLabel, exp, act, line, column, span)));
        }
    }

    private void checkMethodArgumentTypes(MethodCallExpression e) {
        if (e.getArguments().isEmpty()) {
            return;
        }
        Node literalBase = resolveLiteralBase(e.getObject());
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
        if (method == null) {
            return;
        }
        DumbExpression varRef = extractVarRef(e.getObject());
        int fallbackLine = varRef != null ? varRef.getLine() : e.line;
        int fallbackColumn = varRef != null ? varRef.getColumn() + varRef.getValue().length() + 1 : 0;
        checkArgumentTypes(methodName, method.getParameters(), e.getArguments(), fallbackLine, fallbackColumn);
    }

    private void checkFieldAssignment(Expression reference, Expression rhsValue) {
        DumbExpression rootRef = null;
        if (reference instanceof AccessExpression ae) {
            rootRef = extractVarRef(ae.getReference());
        } else if (reference instanceof FieldAccessExpression fae) {
            rootRef = extractVarRef(fae.getObject());
        }
        if (rootRef != null && scope.isDeclared(rootRef.getValue()) && scope.isConst(rootRef.getValue())) {
            errors.add(new ImmutableCollectionStaticError(
                    rootRef.getValue(), rootRef.getLine(), rootRef.getColumn()));
        }
        if (reference instanceof FieldAccessExpression fae) {
            checkFieldAssignmentType(fae, rhsValue);
        }
    }

    private void checkFieldAssignmentType(FieldAccessExpression fae, Expression rhsValue) {
        Node literalBase = resolveLiteralBase(fae.getObject());
        String field = fae.getField();
        VarDecl fieldDecl;
        if (literalBase instanceof StructExpression structExpr) {
            fieldDecl = structExpr.getVarDecls().stream()
                    .filter(v -> field.equals(v.getName())).findFirst().orElse(null);
        } else if (literalBase instanceof ObjectExpression objExpr) {
            fieldDecl = objExpr.getVarDecls().stream()
                    .filter(v -> field.equals(v.getName())).findFirst().orElse(null);
        } else {
            return;
        }
        if (fieldDecl == null || fieldDecl.getType() == null) {
            return;
        }
        MiraType expected = resolveTypeAnnotation(fieldDecl.getType());
        DumbExpression varRef = extractVarRef(fae.getObject());
        String owner = varRef != null ? varRef.getValue() : "object";
        int line = varRef != null ? varRef.getLine() : fae.getObject().line;
        int column = expressionColumn(rhsValue, varRef != null ? varRef.getColumn() : 0);
        int span = expressionSpan(rhsValue, field.length());
        checkAssignable(rhsValue, expected, (exp, actual) -> errors.add(
                new StructFieldTypeMismatchError(field, owner, exp, actual, line, column, span)));
    }

    private void checkParamDefaultValue(Parameter p, MiraType paramType) {
        if (paramType == null || !p.hasDefault()) {
            return;
        }
        checkAssignable(p.defaultValue(), paramType, (expected, actual) -> errors.add(
                new TypeMismatchError(p.name(), expected, actual, p.defaultValue().line, p.column())));
    }

    private record OperandTypes(MiraType left, MiraType right) {

    }

    /**
     * Shared gate for every operand-type check below: resolves both operand
     * types only when at least one side carries an explicit annotation (same
     * rule as checkAssignable's callers everywhere else in this file - a bare
     * literal/ bareword mismatch like `5 - "oops"` stays covered by the
     * pre-existing, softer isStringLiteral-based warnings and must not escalate
     * into a hard error here), and only when both sides resolve to a concrete,
     * non-Any, non-nullable type worth comparing.
     */
    private OperandTypes resolveGatedOperandTypes(Node left, Node right) {
        MiraType leftExplicit = inferExplicitlyTypedOperand(left);
        MiraType rightExplicit = inferExplicitlyTypedOperand(right);
        if (leftExplicit == null && rightExplicit == null) {
            return null;
        }
        MiraType leftType = leftExplicit != null ? leftExplicit : inferMiraType(left);
        MiraType rightType = rightExplicit != null ? rightExplicit : inferMiraType(right);
        if (leftType == null || rightType == null
                || leftType instanceof MiraType.AnyType || rightType instanceof MiraType.AnyType
                || leftType instanceof MiraType.NullableType || rightType instanceof MiraType.NullableType) {
            return null;
        }
        return new OperandTypes(leftType, rightType);
    }

    private void checkBinaryOperandTypes(BinaryExpression e) {
        String op = e.getOperator().getLexeme();
        if (!ARITHMETIC_TYPE_CHECKED_OPERATORS.contains(op)) {
            return;
        }
        OperandTypes types = resolveGatedOperandTypes(e.getLeft(), e.getRight());
        if (types == null) {
            return;
        }
        boolean mismatch = "+".equals(op)
                ? !sameNamedType(types.left(), types.right())
                : !isNumberType(types.left()) || !isNumberType(types.right());
        if (mismatch) {
            errors.add(new BinaryOperatorTypeMismatchError(op, MiraType.display(types.left()),
                    MiraType.display(types.right()), e.getOperator().getLine(), e.getOperator().getColumn()));
        }
    }

    private void checkComparisonOperandTypes(BinaryExpression e) {
        OperandTypes types = resolveGatedOperandTypes(e.getLeft(), e.getRight());
        if (types == null || sameNamedType(types.left(), types.right())) {
            return;
        }
        errors.add(new BinaryOperatorTypeMismatchError(e.getOperator().getLexeme(), MiraType.display(types.left()),
                MiraType.display(types.right()), e.getOperator().getLine(), e.getOperator().getColumn()));
    }

    private void checkVariableCallable(UnaryExpression dollarRef, DumbExpression nameExpr) {
        MiraType type = inferMiraType(dollarRef);
        if (type == null || type instanceof MiraType.AnyType || type instanceof MiraType.NullableType) {
            return;
        }
        if (type instanceof MiraType.NamedType n && "Fn".equals(n.name())) {
            return;
        }
        errors.add(new VariableNotCallableError(nameExpr.getValue(), MiraType.display(type),
                nameExpr.getLine(), nameExpr.getColumn()));
    }

    private void checkUnaryOperandType(UnaryExpression e) {
        MiraType type = inferExplicitlyTypedOperand(e.getRight());
        if (type == null || isNumberType(type)) {
            return;
        }
        errors.add(new UnaryOperatorTypeMismatchError(e.getOperation().getLexeme(), MiraType.display(type),
                e.getOperation().getLine(), e.getOperation().getColumn()));
    }

    /**
     * Explicit-annotation-only variant of inferMiraType, used to gate
     * resolveGatedOperandTypes/checkUnaryOperandType.
     */
    private MiraType inferExplicitlyTypedOperand(Node expr) {
        if (expr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && isIdentifier(d)) {
            return declaredVarTypes.get(d.getValue());
        }
        if (expr instanceof CallExpression call && call.getCallee() instanceof DumbExpression callee
                && isIdentifier(callee)) {
            FuncDecl fn = userFuncDecls.get(callee.getValue());
            if (fn != null && fn.getReturnType() != null) {
                return resolveTypeAnnotation(fn.getReturnType());
            }
        }
        return null;
    }

    private static boolean sameNamedType(MiraType a, MiraType b) {
        return a instanceof MiraType.NamedType na && b instanceof MiraType.NamedType nb
                && na.name().equals(nb.name());
    }

    private static boolean isNumberType(MiraType t) {
        return t instanceof MiraType.NamedType n && "Number".equals(n.name());
    }

    private static int expressionColumn(Expression expr, int fallback) {
        if (expr instanceof DumbExpression d) {
            return d.getColumn();
        }
        if (expr instanceof UnaryExpression u) {
            return u.getOperation().getColumn();
        }
        return fallback;
    }

    private static int expressionSpan(Expression expr, int fallback) {
        if (expr instanceof DumbExpression d) {
            return Math.max(1, d.getValue().length());
        }
        if (expr instanceof UnaryExpression u && u.getRight() instanceof DumbExpression d) {
            return 1 + Math.max(1, d.getValue().length());
        }
        return Math.max(1, fallback);
    }

    private void checkCallParamFieldAccesses(FuncDecl fn, List<Expression> args) {
        walkFuncWithParamTypes(fn, args, Map.of(), new HashSet<>(), 0, 0);
    }

    private void walkFuncWithParamTypes(FuncDecl fn, List<Expression> args,
            Map<String, Node> callerParamTypes, Set<String> visited,
            int callSiteLine, int callSiteCol) {
        if (visited.contains(fn.getName())) {
            return;
        }
        List<Parameter> params = fn.getParameters();
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
                                errors.add(new UndefinedObjectFieldStaticError(field, varRef.getValue(), errLine, errCol));
                            }
                        }
                        case StructExpression structExpr -> {
                            boolean exists = structExpr.getVarDecls().stream().anyMatch(v -> field.equals(v.getName()))
                                    || structExpr.getMethods().stream().anyMatch(m -> field.equals(m.getName()));
                            if (!exists) {
                                errors.add(new UndefinedObjectFieldStaticError(field, varRef.getValue(), errLine, errCol));
                            }
                        }
                        default -> {
                            String typeName = type instanceof ListExpression ? "list"
                                    : type instanceof ArrayExpression ? "array"
                                            : type instanceof MapExpression ? "map"
                                                    : "non-object value";
                            errors.add(new FieldAccessOnNonObjectError(field, typeName, errLine, errCol));
                        }
                    }
                }
                queue.add(fae.getObject());
            } else {
                if (n instanceof CallExpression ce
                        && ce.getCallee() instanceof DumbExpression callee
                        && isIdentifier(callee)) {
                    FuncDecl calledFn = userFuncDecls.get(callee.getValue());
                    if (calledFn != null && !ce.getArguments().isEmpty()) {
                        walkFuncWithParamTypes(calledFn, ce.getArguments(), paramTypes, nextVisited, callSiteLine, callSiteCol);
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
        return resolveRhsLiteralType(arg);
    }

    private static void addChildrenNoFunctions(Node node, Deque<Node> queue) {
        if (node instanceof FuncDecl || node instanceof LambdaExpression) {
            return;
        }
        addChildren(node, queue);
    }

    private static DumbExpression extractVarRef(Node expr) {
        if (expr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && isIdentifier(d)) {
            return d;
        }
        return null;
    }

    private static boolean isNonNumericLiteral(Node n) {
        if (n instanceof ListExpression || n instanceof ArrayExpression
                || n instanceof MapExpression || n instanceof ObjectExpression) {
            return true;
        }
        return n instanceof DumbExpression d && d.getTokenType() == TokenType.STRING_LITERAL;
    }

    private static boolean isNonIterableLiteral(Node n) {
        if (!(n instanceof DumbExpression d)) {
            return false;
        }
        return !isIdentifier(d);
    }

    private static boolean alwaysReturns(List<Node> body) {
        for (Node node : body) {
            switch (node) {
                case Return ignored -> {
                    return true;
                }
                case Throw ignored -> {
                    return true;
                }
                case If ifStmt -> {
                    if (ifStmt.getElseBody() != null
                            && alwaysReturns(ifStmt.getThenBody())
                            && alwaysReturns(ifStmt.getElseBody())) {
                        return true;
                    }
                }
                case Switch sw -> {
                    if (sw.getDefaultBody() != null
                            && sw.getCases().stream().allMatch(c -> alwaysReturns(c.getBody()))
                            && alwaysReturns(sw.getDefaultBody())) {
                        return true;
                    }
                }
                case TryCatch tc -> {
                    if (alwaysReturns(tc.getTryBody())
                            && !tc.getCatchClauses().isEmpty()
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

    private static boolean hasAnyReturn(List<Node> body) {
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
