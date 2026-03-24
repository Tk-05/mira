package com.mira.runtime.interpreter;

import java.util.ArrayList;
import java.util.List;

import com.mira.Flags;
import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UnknownOperatorError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.Mutability;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.lib.ImportResolver;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;

public class Interpreter implements ExprVisitor<Object>, StmtVisitor<Object> {

    private static Interpreter instance;
    private static Environment globalEnvironment = new Environment();
    private Environment localEnvironment;

    public static Interpreter getInstance() {
        if (instance == null) {
            instance = new Interpreter();
        }
        return instance;
    }

    private void loadGlobalContext(List<Node> asts) {
        if (globalEnvironment.getSize() > 0) {
            globalEnvironment = new Environment();
        }

        List<ImportExpression> imports = new ArrayList<>();
        for (Node ast : asts) {
            if (ast instanceof ImportExpression importExpression) {
                imports.add(importExpression);
            }
        }
        ImportResolver.resolveImports(imports, globalEnvironment);

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

    public <T> T run(List<Node> asts, String[] args) {
        loadGlobalContext(asts);
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
    }

    public <T> T run(List<Node> asts) {
        loadGlobalContext(asts);
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
    }

    public <T> T runWithoutLoadingNewContext(List<Node> asts) {
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
    }

    @Override
    public Void visitVarDecl(VarDecl varDecl) {
        Object value = null;

        if (varDecl.getInitializer() != null) {
            value = varDecl.getInitializer().accept(this);
        }

        if (localEnvironment == null) {
            globalEnvironment.define(varDecl.getName(), value);
        } else {
            localEnvironment.define(varDecl.getName(), value);
        }

        return null;
    }

    @Override
    public <T> T visitDumbExpr(DumbExpression expression) {
        return (T) expression.getValue();
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        Object callee = globalEnvironment.get((String) expression.getCallee().accept(this));

        if (!(callee instanceof Callable callable)) {
            throw new RuntimeException("Object is not callable");
        }

        List<Object> arguments = new ArrayList<>();

        for (Expression arg : expression.getArguments()) {
            arguments.add(arg.accept(this));
        }

        if (arguments.size() != callable.getArity()) {
            throw new ArgMismatchError((String) expression.getCallee().accept(this), callable.getArity(), arguments.size());
        }

        Object result = callable.call(this, arguments);

        return (T) result;
    }

    @Override
    public Void visitFuncDecl(FuncDecl funcDecl) {
        globalEnvironment.define(funcDecl.getName(),
                new Function(localEnvironment,
                        funcDecl.getBody(),
                        funcDecl.getParameters(),
                        funcDecl.getArity()));

        return null;
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
        StringBuilder builder = new StringBuilder();

        Object first = expressions.getFirst().accept(this);

        if (expressions.getFirst() instanceof ComplexExpression) {
            builder.append("(").append(first).append(")");
        } else {
            builder.append(first);
        }

        int i = 1;

        while (i < expressions.size()) {

            Expression operatorExpr = expressions.get(i);

            Object right;
            String operator;

            if (operatorExpr instanceof UnaryExpression unary) {

                operator = unary.getOperation().getLexeme();

                if (unary.getRight() != null) {
                    right = unary.accept(this);
                    i += 1;
                } else {
                    builder.append(operator);
                    i += 1;
                    continue;
                }

            } else {
                right = operatorExpr.accept(this);
                i++;

                builder.append(right);
                continue;
            }

            switch (operator) {
                case "+", "-", "*", "/" ->
                    builder.append(operator).append(right);
                case "$" ->
                    builder.append(right);
                default ->
                    throw new UnknownOperatorError("Unknown operator: " + operator);
            }
        }

        return (T) builder.toString();
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        String operator = expression.getOperation().getLexeme();

        Object right = null;

        if (expression.getRight() != null) {
            right = expression.getRight().accept(this);
        }

        switch (operator) {
            case "$" -> {
                String name = (String) right;

                if (localEnvironment != null && localEnvironment.exists(name)) {
                    return (T) localEnvironment.get(name);
                }

                return (T) globalEnvironment.get(name);
            }
            case "-" -> {
                if (right == null) {
                    return (T) "-";
                } else {
                    return (T) ("-" + right);
                }
            }
            default ->
                throw new UnknownOperatorError("Unknown unary operator: " + operator);
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
    public <T> T visitAccessExpr(AccessExpression expression) {
        Object accessedObject = expression.getReference().accept(this);

        for (Expression index : expression.getIndecies()) {
            Object object = index.accept(this);
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
                default ->
                    throw new AssertionError("Acessed object is an instance of '" + accessedObject.getClass() + "' and not a type of collection!");
            }
        }

        return (T) accessedObject;
    }

    @Override
    public Void visitAssign(Assign assign) {
        switch (assign.getReference()) {
            case AccessExpression accessExpression -> {
                Object referencedObject = accessExpression.getReference().accept(this);
                //Last index reserved for assignment
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
                                throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
                            }
                        }
                        case ListExpression list -> {
                            referencedObject = list.getMembers().get(i);
                            if (referencedObject instanceof ListExpression innerList) {
                                referencedObject = innerList.accept(this);
                            } else {
                                throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
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
                            default ->
                                throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
                        }
                    }
                } else {
                    throw new ReferenceIsImmutableError("Can not assign value to immutable data structure");
                }
            }
            default -> {
                if (assign.getReference() instanceof UnaryExpression unaryExpression) {
                    String name = (String) unaryExpression.getRight().accept(this);
                    Object expression = assign.getExpression().accept(this);

                    if (localEnvironment == null) {
                        globalEnvironment.assign(name, expression);
                    } else {
                        localEnvironment.assign(name, expression);
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
        String condition = (String) stmt.getCondition().accept(this);
        boolean value = (boolean) Evaluator.evaluate(condition);

        List<Node> body = value ? stmt.getThenBody() : stmt.getElseBody();

        if (body == null) {
            return null;
        }

        for (Node node : body) {
            switch (node) {
                case Expression expression ->
                    expression.accept(this);
                case Statement statement ->
                    statement.accept(this);
                default ->
                    throw new AssertionError();
            }
        }

        return null;
    }

    @Override
    public Object visitFor(For stmt) {
        for (Node node : stmt.getVarDecls()) {
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

        try {
            while (true) {
                if (stmt.getCondition() != null) {
                    String condition = (String) stmt.getCondition().accept(this);
                    switch (Evaluator.evaluate(condition)) {
                        case Boolean b -> {
                            if (!b) {
                                return null;
                            }
                        }
                        case Double d -> {
                            if (d == 0) {
                                return null;
                            }
                        }
                        default ->
                            throw new AssertionError();
                    }
                }

                for (Node node : stmt.getBody()) {
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

                for (Node node : stmt.getPostExpressions()) {
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
        } catch (BreakSignal breakSignal) {
            return null;
        }
    }

    @Override
    public Object visitWhile(While stmt) {
        try {
            while (true) {
                String condition = (String) stmt.getCondition().accept(this);
                switch (Evaluator.evaluate(condition)) {
                    case Boolean b -> {
                        if (!b) {
                            return null;
                        }
                    }
                    case Double d -> {
                        if (d == 0) {
                            return null;
                        }
                    }
                    default ->
                        throw new AssertionError();
                }

                for (Node node : stmt.getBody()) {
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
        } catch (BreakSignal breakSignal) {
            return null;
        }
    }

    @Override
    public Object visitBreak(Break stmt) {
        throw new BreakSignal();
    }

    @Override
    public Object visitBlock(Block stmt) {
        Environment previous = localEnvironment;
        localEnvironment = new Environment(previous);

        for (Node node : stmt.getBody()) {
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

        localEnvironment = previous;
        return null;
    }

    public Environment getLocalEnvironment() {
        return localEnvironment;
    }

    public void setLocalEnvironment(Environment localEnvironment) {
        this.localEnvironment = localEnvironment;
    }

    public static Environment getGlobalEnvironment() {
        return globalEnvironment;
    }
}
