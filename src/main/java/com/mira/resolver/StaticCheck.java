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

import com.mira.error.MiraError;
import com.mira.error.resolver.MultipleStaticCheckErrors;
import com.mira.error.resolver.StaticCheckError.ArityMismatchError;
import com.mira.error.resolver.StaticCheckError.BreakOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.ConstReassignmentError;
import com.mira.error.resolver.StaticCheckError.ContinueOutsideLoopError;
import com.mira.error.resolver.StaticCheckError.DuplicateDeclarationError;
import com.mira.error.resolver.StaticCheckError.FieldAccessOnNonObjectError;
import com.mira.error.resolver.StaticCheckError.ImmutableCollectionStaticError;
import com.mira.error.resolver.StaticCheckError.LiteralNotCallableError;
import com.mira.error.resolver.StaticCheckError.MissingModuleDeclarationError;
import com.mira.error.resolver.StaticCheckError.NotIterableStaticError;
import com.mira.error.resolver.StaticCheckError.PostExprNaNStaticError;
import com.mira.error.resolver.StaticCheckError.PostUnaryStaticError;
import com.mira.error.resolver.StaticCheckError.PrivateAccessError;
import com.mira.error.resolver.StaticCheckError.PrivateImportError;
import com.mira.error.resolver.StaticCheckError.RangeStepZeroStaticError;
import com.mira.error.resolver.StaticCheckError.ReturnOutsideFunctionError;
import com.mira.error.resolver.StaticCheckError.StaticAssertRuntimeValueError;
import com.mira.error.resolver.StaticCheckError.UndeclaredVariableError;
import com.mira.error.resolver.StaticCheckError.UndefinedFunctionError;
import com.mira.error.resolver.StaticCheckError.UndefinedModuleSymbolError;
import com.mira.error.resolver.StaticCheckError.UndefinedObjectFieldStaticError;
import com.mira.error.resolver.StaticCheckError.UnknownModuleSymbolError;
import com.mira.error.resolver.StaticCheckError.UnknownNamespaceError;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.TokenType;
import com.mira.lib.LibIndex;
import com.mira.linter.LintScope;
import com.mira.linter.LintScope.VarInfo;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
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
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
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
    private final Map<String, Node> varLiteralTypes = new HashMap<>();
    private final Map<String, FuncDecl> userFuncDecls = new HashMap<>();
    private Map<Path, String> openDocuments = Map.of();

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
        this.externallyUsed = externallyUsed;
        this.sourcePath = sourcePath;
        this.openDocuments = openDocuments;
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
                    }
                }
                case EnumDecl e -> {
                    if (scope.isDeclaredInCurrentScope(e.getIdentifier())) {
                        errors.add(new DuplicateDeclarationError(e.getIdentifier(), e.line, 0));
                    } else {
                        scope.declare(e.getIdentifier(), e.line, 0, true);
                        scope.markUsed(e.getIdentifier());
                    }
                }
                case ImportExpression imp ->
                    preDeclareImport(imp);
                default -> {
                }
            }
        }

        scope.markUsed("main");
        externallyUsed.forEach(scope::markUsed);
        loadExternalFuncDecls();

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
                if (functionDepth == 0) {
                    errors.add(new ReturnOutsideFunctionError(stmt.line, stmt.column));
                }
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
                if (!(e.getRight() instanceof UnaryExpression inner)
                        || !"$".equals(inner.getOperation().getLexeme())) {
                    errors.add(new PostUnaryStaticError(
                            e.getOperation().getLexeme(),
                            e.getOperation().getLine(),
                            e.getOperation().getColumn()));
                } else {
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
                } else {
                    DumbExpression varD = extractVarRef(e.getRight());
                    if (varD != null && isZeroLiteral(varLiteralTypes.get(varD.getValue()))) {
                        WarningCollector.emit(WarningLevel.WARNING, "Division by zero", e.getOperator());
                    }
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
                FuncDecl nsFn = userFuncDecls.get(e.getFunctionName());
                if (nsFn != null && !e.getArguments().isEmpty()) {
                    walkFuncWithParamTypes(nsFn, e.getArguments(), Map.of(), new HashSet<>(), e.getLine(), e.getColumn());
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
                    functionDepth++;
                    resolveBody(method.getBody());
                    functionDepth--;
                    popScope();
                }
            }
            case StructExpression e -> {
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
                    functionDepth++;
                    resolveBody(method.getBody());
                    functionDepth--;
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
                        boolean exists = structExpr.getVarDecls().stream().anyMatch(v -> key.equals(v.getName()))
                                || structExpr.getMethods().stream().anyMatch(m -> key.equals(m.getName()));
                        if (!exists) {
                            errors.add(new UndefinedObjectFieldStaticError(key, templateName, entry.getValue().line, 0));
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
                            } else {
                                varLiteralTypes.remove(name);
                            }
                        }
                    }
                }
                resolveExpr(e.getValue());
            }
            case LambdaExpression e -> {
                scope.push();
                e.getParameters().forEach(p -> scope.declare(p.name(), 0, 0, false));
                if (e.getVariadicParam() != null) {
                    scope.declare(e.getVariadicParam(), 0, 0, false);
                }
                functionDepth++;
                resolveBody(e.getBody());
                functionDepth--;
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
                    }
                }
            } else {
                errors.add(new LiteralNotCallableError(
                        callee.getValue(), callee.getLine(), callee.getColumn()));
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
        scope.declare(stmt.getName(), stmt.line, stmt.nameColumn, stmt.isConst(), inComptimeBlock && stmt.isConst());
        if (stmt.getInitializer() != null && isKnownLiteral(stmt.getInitializer())) {
            varLiteralTypes.put(stmt.getName(), stmt.getInitializer());
        } else if (stmt.getInitializer() instanceof StructInitExpression si) {
            Node templateLiteral = resolveLiteralBase(si.getTarget());
            if (templateLiteral instanceof StructExpression) {
                varLiteralTypes.put(stmt.getName(), templateLiteral);
            }
        }
    }

    private void resolveStaticAssert(StaticAssert stmt) {
        resolveExpr(stmt.getCondition());
        checkComptimeExpr(stmt.getCondition());
        if (stmt.getMessage() != null) {
            resolveExpr(stmt.getMessage());
            checkComptimeExpr(stmt.getMessage());
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
        stmt.getParameters().forEach(p -> scope.declare(p.name(), stmt.line, p.column(), false));
        if (stmt.getVariadicParam() != null) {
            scope.declare(stmt.getVariadicParam(), stmt.line, 0, false);
        }
        functionDepth++;
        resolveBody(stmt.getBody());
        functionDepth--;
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
                    } else {
                        varLiteralTypes.remove(name);
                    }
                }
            }
        } else {
            DumbExpression rootRef = null;
            if (stmt.getReference() instanceof AccessExpression ae) {
                rootRef = extractVarRef(ae.getReference());
            } else if (stmt.getReference() instanceof FieldAccessExpression fae) {
                rootRef = extractVarRef(fae.getObject());
            }
            if (rootRef != null && scope.isDeclared(rootRef.getValue()) && scope.isConst(rootRef.getValue())) {
                errors.add(new ImmutableCollectionStaticError(
                        rootRef.getValue(), rootRef.getLine(), rootRef.getColumn()));
            }
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

    private void checkSelectiveModuleImport(ImportExpression expr) {
        if (!checkedModuleImports.add(expr.getModule())) {
            return;
        }
        java.nio.file.Path base = sourcePath != null
                ? sourcePath.getParent()
                : (com.mira.Flags.inputPath.get() != null ? com.mira.Flags.inputPath.get().getParent() : null);
        if (base == null) {
            return;
        }

        String rawPath = expr.getModule().replace("\"", "");
        java.nio.file.Path modulePath = base.resolve(rawPath).normalize();
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
                : (com.mira.Flags.inputPath.get() != null ? com.mira.Flags.inputPath.get().getParent() : null);
        if (base == null) {
            return;
        }

        String rawPath = expr.getModule().replace("\"", "");
        java.nio.file.Path modulePath = base.resolve(rawPath).normalize();
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
            case For s ->
                s.column;
            case While s ->
                s.column;
            case Foreach s ->
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
            case For ignored ->
                "for".length();
            case While ignored ->
                "while".length();
            case Foreach ignored ->
                "foreach".length();
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

    private static boolean isKnownLiteral(Node n) {
        return n instanceof ListExpression
                || n instanceof ArrayExpression
                || n instanceof MapExpression
                || n instanceof ObjectExpression
                || n instanceof StructExpression
                || (n instanceof DumbExpression d && !isIdentifier(d));
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
        return null;
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
                case For f -> {
                    if (hasAnyReturn(f.getBody())) {
                        return true;

                    }
                }
                case Foreach fe -> {
                    if (hasAnyReturn(fe.getBody())) {
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
