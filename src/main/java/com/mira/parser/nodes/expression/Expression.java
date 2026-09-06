package com.mira.parser.nodes.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.utils.StringFormatter;

public abstract class Expression implements Node {

    public int line = 0;

    public static interface Mutability {

        public abstract boolean isMutable();
    }

    public abstract <T> T accept(ExprVisitor<T> visitor);

    @Override
    public abstract String toString();

    public static class DumbExpression extends Expression {

        private final Token token;
        private Object cachedValue;

        public DumbExpression(Token token) {
            this.token = token;
            if (token != null) {
                this.line = token.getLine();
            }
        }

        public Object getCachedValue() {
            return cachedValue;
        }

        public void setCachedValue(Object v) {
            cachedValue = v;
        }

        public String getValue() {
            return token.getLexeme();
        }

        public TokenType getTokenType() {
            return token.getTokenType();
        }

        public int getLine() {
            return token.getLine();
        }

        public int getColumn() {
            return token.getColumn();
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitDumbExpr(this);
        }

        @Override
        public String toString() {
            return token.getLexeme();
        }
    }

    public static class UnaryExpression extends Expression {

        private final Token operation;
        private final Expression right;
        private final boolean prefix;

        public UnaryExpression(Token operation, Expression right) {
            this(operation, right, false);
        }

        public UnaryExpression(Token operation, Expression right, boolean prefix) {
            this.operation = operation;
            this.right = right;
            this.prefix = prefix;
        }

        public Token getOperation() {
            return operation;
        }

        public Expression getRight() {
            return right;
        }

        public boolean isPrefix() {
            return prefix;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitUnaryExpr(this);
        }

        @Override
        public String toString() {
            if (right == null) {
                return operation.getLexeme();
            }
            if ("$".equals(operation.getLexeme())) {
                return right.toString();
            }
            return operation.getLexeme() + right.toString();
        }
    }

    public static class AssignExpression extends Expression {

        private final Expression reference;
        private final Expression value;

        public AssignExpression(Expression reference, Expression value) {
            this.reference = reference;
            this.value = value;
            this.line = reference.line;
        }

        public Expression getReference() {
            return reference;
        }

        public Expression getValue() {
            return value;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitAssignExpression(this);
        }

        @Override
        public String toString() {
            return reference + " : " + value;
        }
    }

    public static class ComplexExpression extends Expression {

        private final List<Expression> expressions;

        public ComplexExpression(List<Expression> expressions) {
            this.expressions = expressions;
        }

        public List<Expression> getExpressions() {
            return expressions;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitComplexExpr(this);
        }

        @Override
        public String toString() {
            String value = "";
            for (Expression expression : expressions) {
                value += expression.toString();
            }
            return value;
        }
    }

    public static class CallExpression extends Expression {

        private final Expression callee;
        private final List<Expression> arguments;

        public CallExpression(Expression callee, List<Expression> arguments) {
            this.callee = callee;
            this.arguments = arguments;
        }

        public Expression getCallee() {
            return callee;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitCallExpr(this);
        }

        @Override
        public String toString() {
            return callee.toString();
        }
    }

    public static class ArrayExpression extends Expression implements Mutability {

        private final List<Expression> members;

        public ArrayExpression(List<Expression> members) {
            this.members = members;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitArrayExpr(this);
        }

        public int getLength() {
            return members.size();
        }

        public List<Expression> getMembers() {
            return members;
        }

        @Override
        public String toString() {
            return StringFormatter.formatToString(this);
        }

        @Override
        public boolean isMutable() {
            return true;
        }
    }

    public static class AccessExpression extends Expression {

        private final Expression reference;
        private final List<Expression> indecies;

        public AccessExpression(Expression reference, List<Expression> indecies) {
            this.reference = reference;
            this.indecies = indecies;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitAccessExpr(this);
        }

        @Override
        public String toString() {
            return reference.toString() + "[" + indecies.toString() + "]";
        }

        public Expression getReference() {
            return reference;
        }

        public List<Expression> getIndecies() {
            return indecies;
        }
    }

    public static class ListExpression extends Expression implements Mutability {

        private final List<Expression> members;

        public ListExpression(List<Expression> members) {
            this.members = members;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitListExpr(this);
        }

        @Override
        public String toString() {
            return StringFormatter.formatToString(this);
        }

        @Override
        public boolean isMutable() {
            return true;
        }

        public List<Expression> getMembers() {
            return members;
        }

        public int getLength() {
            return members.size();
        }
    }

    public static class MapExpression extends Expression implements Mutability {

        private final LinkedHashMap<String, Expression> entries;

        public MapExpression(LinkedHashMap<String, Expression> entries) {
            this.entries = entries;
        }

        public LinkedHashMap<String, Expression> getEntries() {
            return entries;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitMapExpr(this);
        }

        @Override
        public String toString() {
            return StringFormatter.formatToString(this);
        }

        @Override
        public boolean isMutable() {
            return true;
        }
    }

    public static class ImportExpression extends Expression {

        public enum ImportKind {
            STDLIB, MODULE, NATIVE
        }

        public int line = 0;

        private final Expression module;
        private final String namespace;
        private final ImportKind kind;
        private final List<String> selectedFunctions;

        public ImportExpression(Expression module, String namespace, ImportKind kind) {
            this(module, namespace, kind, null);
        }

        public ImportExpression(Expression module, String namespace, ImportKind kind, List<String> selectedFunctions) {
            this.module = module;
            this.namespace = namespace;
            this.kind = kind;
            this.selectedFunctions = selectedFunctions;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return null;
        }

        @Override
        public String toString() {
            String path = module.toString();
            String selection = isSelective() ? " {" + String.join(", ", selectedFunctions) + "}" : "";
            String alias = namespace != null ? " as " + namespace : "";
            return switch (kind) {
                case NATIVE ->
                    "import native " + path + alias;
                case MODULE ->
                    "import module " + path + selection + alias;
                case STDLIB ->
                    "import " + path + selection + alias;
            };
        }

        public String getModule() {
            return module.toString();
        }

        public String getNamespace() {
            return namespace;
        }

        public ImportKind getKind() {
            return kind;
        }

        public List<String> getSelectedFunctions() {
            return selectedFunctions;
        }

        public boolean isSelective() {
            return selectedFunctions != null && !selectedFunctions.isEmpty();
        }

        public boolean isExternalModule() {
            return kind == ImportKind.MODULE;
        }

        public boolean isNativeJar() {
            return kind == ImportKind.NATIVE;
        }
    }

    public static class NamespaceCallExpression extends Expression {

        private final String alias;
        private final String functionName;
        private final List<Expression> arguments;
        private final int line;
        private final int column;

        public NamespaceCallExpression(String alias, String functionName, List<Expression> arguments, int line) {
            this(alias, functionName, arguments, line, 0);
        }

        public NamespaceCallExpression(String alias, String functionName, List<Expression> arguments, int line, int column) {
            this.alias = alias;
            this.functionName = functionName;
            this.arguments = arguments;
            this.line = line;
            this.column = column;
        }

        public String getAlias() {
            return alias;
        }

        public String getFunctionName() {
            return functionName;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitNamespaceCallExpr(this);
        }

        @Override
        public String toString() {
            return alias + "." + functionName;
        }
    }

    public static class RangeExpression extends Expression {

        private final Expression start;
        private final Expression end;
        private final Expression stepsize;

        public RangeExpression(Expression start, Expression end, Expression stepsize) {
            this.start = start;
            this.end = end;
            this.stepsize = stepsize;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitRangeExpression(this);
        }

        @Override
        public String toString() {
            return "<" + start + ".." + end + (stepsize != null ? "," + stepsize : "") + ">";
        }

        public Expression getStart() {
            return start;
        }

        public Expression getEnd() {
            return end;
        }

        public Expression getStepsize() {
            return stepsize;
        }
    }

    public static class ObjectExpression extends Expression {

        private final List<VarDecl> varDecls;
        private final List<FuncDecl> methods;

        public ObjectExpression(List<VarDecl> varDecls) {
            this(varDecls, new ArrayList<>());
        }

        public ObjectExpression(List<VarDecl> varDecls, List<FuncDecl> methods) {
            this.varDecls = varDecls;
            this.methods = methods;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitObjectExpression(this);
        }

        @Override
        public String toString() {
            return "{ " + varDecls.size() + " field(s), " + methods.size() + " method(s) }";
        }

        public List<VarDecl> getVarDecls() {
            return varDecls;
        }

        public List<FuncDecl> getMethods() {
            return methods;
        }
    }

    public static class StructExpression extends Expression {

        private final List<VarDecl> varDecls;
        private final List<FuncDecl> methods;

        public StructExpression(List<VarDecl> varDecls, List<FuncDecl> methods) {
            this.varDecls = varDecls;
            this.methods = methods;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitStructExpression(this);
        }

        @Override
        public String toString() {
            return "struct { " + varDecls.size() + " field(s), " + methods.size() + " method(s) }";
        }

        public List<VarDecl> getVarDecls() {
            return varDecls;
        }

        public List<FuncDecl> getMethods() {
            return methods;
        }
    }

    public static class StructInitExpression extends Expression {

        private final Expression target;
        private final java.util.LinkedHashMap<String, Expression> overrides;

        public StructInitExpression(Expression target, java.util.LinkedHashMap<String, Expression> overrides) {
            this.target = target;
            this.overrides = overrides;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitStructInitExpression(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(target).append(" { ");
            int i = 0;
            for (var entry : overrides.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
                if (++i < overrides.size()) {
                    sb.append(", ");
                }
            }
            sb.append(" }");
            return sb.toString();
        }

        public Expression getTarget() {
            return target;
        }

        public java.util.LinkedHashMap<String, Expression> getOverrides() {
            return overrides;
        }
    }

    public static class FieldAccessExpression extends Expression {

        private final Expression object;
        private final String field;
        private final boolean optional;

        public FieldAccessExpression(Expression object, String field) {
            this(object, field, false);
        }

        public FieldAccessExpression(Expression object, String field, boolean optional) {
            this.object = object;
            this.field = field;
            this.optional = optional;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitFieldAccessExpression(this);
        }

        @Override
        public String toString() {
            return object.toString() + (optional ? "?." : ".") + field;
        }

        public Expression getObject() {
            return object;
        }

        public String getField() {
            return field;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    public static class MethodCallExpression extends Expression {

        private final Expression object;
        private final String method;
        private final List<Expression> arguments;
        private final boolean optional;

        public MethodCallExpression(Expression object, String method, List<Expression> arguments, boolean optional) {
            this.object = object;
            this.method = method;
            this.arguments = arguments;
            this.optional = optional;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitMethodCallExpression(this);
        }

        @Override
        public String toString() {
            return object.toString() + (optional ? "?." : ".") + method + "(...)";
        }

        public Expression getObject() {
            return object;
        }

        public String getMethod() {
            return method;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    public static class BinaryExpression extends Expression {

        private final Expression left;
        private final Token operator;
        private final Expression right;

        public BinaryExpression(Expression left, Token operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        public Expression getLeft() {
            return left;
        }

        public Token getOperator() {
            return operator;
        }

        public Expression getRight() {
            return right;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitBinaryExpr(this);
        }

        @Override
        public String toString() {
            return "(" + left + " " + operator.getLexeme() + " " + right + ")";
        }
    }

    public static class TernaryExpression extends Expression {

        private final Expression condition;
        private final Expression thenExpr;
        private final Expression elseExpr;

        public TernaryExpression(Expression condition, Expression thenExpr, Expression elseExpr) {
            this.condition = condition;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        public Expression getCondition() {
            return condition;
        }

        public Expression getThenExpr() {
            return thenExpr;
        }

        public Expression getElseExpr() {
            return elseExpr;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitTernaryExpr(this);
        }

        @Override
        public String toString() {
            return condition + " ? " + thenExpr + " : " + elseExpr;
        }
    }

    public static class AwaitExpression extends Expression {

        private final Expression expr;

        public AwaitExpression(Expression expr) {
            this.expr = expr;
        }

        public Expression getExpr() {
            return expr;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitAwaitExpr(this);
        }

        @Override
        public String toString() {
            return "await(" + expr + ")";
        }
    }

    public static class TypeofExpression extends Expression {

        private final Expression expr;

        public TypeofExpression(Expression expr) {
            this.expr = expr;
        }

        public Expression getExpr() {
            return expr;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitTypeofExpr(this);
        }

        @Override
        public String toString() {
            return "typeof(" + expr + ")";
        }
    }

    public static class SwitchExpression extends Expression {

        public record SwitchExprCase(Expression value, Expression result) {

        }

        private final Expression subject;
        private final List<SwitchExprCase> cases;
        private final Expression defaultExpr;

        public SwitchExpression(Expression subject, List<SwitchExprCase> cases, Expression defaultExpr) {
            this.subject = subject;
            this.cases = cases;
            this.defaultExpr = defaultExpr;
        }

        public Expression getSubject() {
            return subject;
        }

        public List<SwitchExprCase> getCases() {
            return cases;
        }

        public Expression getDefaultExpr() {
            return defaultExpr;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitSwitchExpr(this);
        }

        @Override
        public String toString() {
            return "switch(" + subject + "){...}";
        }
    }

    public static class LambdaExpression extends Expression {

        private final List<Parameter> parameters;
        private final List<Node> body;
        private final String variadicParam;
        private final boolean isAsync;
        private final boolean isArrow;

        public LambdaExpression(List<Parameter> parameters, List<Node> body, String variadicParam) {
            this(parameters, body, variadicParam, false, false);
        }

        public LambdaExpression(List<Parameter> parameters, List<Node> body, String variadicParam, boolean isAsync) {
            this(parameters, body, variadicParam, isAsync, false);
        }

        public LambdaExpression(List<Parameter> parameters, List<Node> body, String variadicParam, boolean isAsync, boolean isArrow) {
            this.parameters = parameters;
            this.body = body;
            this.variadicParam = variadicParam;
            this.isAsync = isAsync;
            this.isArrow = isArrow;
        }

        public boolean isAsync() {
            return isAsync;
        }

        public boolean isArrow() {
            return isArrow;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

        public List<Node> getBody() {
            return body;
        }

        public String getVariadicParam() {
            return variadicParam;
        }

        public int getArity() {
            if (variadicParam != null) {
                return -1;
            }
            return (int) parameters.stream().filter(p -> !p.hasDefault()).count();
        }

        public int getMaxArity() {
            return variadicParam != null ? -1 : parameters.size();
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitLambdaExpr(this);
        }

        @Override
        public String toString() {
            return "<lambda/" + parameters.size() + ">";
        }
    }

    public static class ExecBlock extends Expression {

        private final List<Node> body;
        private final boolean isolated;

        public ExecBlock(List<Node> body, boolean isolated) {
            this.body = body;
            this.isolated = isolated;
        }

        public List<Node> getBody() {
            return body;
        }

        public boolean isIsolated() {
            return isolated;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitExecBlock(this);
        }

        @Override
        public String toString() {
            return "exec" + (isolated ? " isolated" : "") + "{...}";
        }
    }

    public static class ThrownException extends Expression {

        private final String identifier;
        private final Expression value;

        public ThrownException(String identifier, Expression value) {
            this.identifier = identifier;
            this.value = value;
        }

        public String getIdentifier() {
            return identifier;
        }

        public Expression getValue() {
            return value;
        }

        @Override
        public <T> T accept(ExprVisitor<T> visitor) {
            return visitor.visitThrownException(this);
        }

        @Override
        public String toString() {
            return identifier + "(" + value + ")";
        }
    }
}
