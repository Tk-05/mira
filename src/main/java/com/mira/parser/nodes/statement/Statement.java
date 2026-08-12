package com.mira.parser.nodes.statement;

import java.util.List;
import java.util.Map;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.runtime.visitors.StmtVisitor;

public abstract class Statement implements Node {

    public int line = 0;
    public int column = 0;
    public int endLine = 0;

    public abstract <T> T accept(StmtVisitor<T> visitor);

    @Override
    public abstract String toString();

    public static class VarDecl extends Statement {

        private final String name;
        private final Expression initializer;
        private final boolean isConst;
        private final boolean isPublic;
        public int nameColumn = 0;

        public VarDecl(String name, Expression initializer, boolean isConst) {
            this(name, initializer, isConst, false);
        }

        public VarDecl(String name, Expression initializer, boolean isConst, boolean isPublic) {
            this.name = name;
            this.initializer = initializer;
            this.isConst = isConst;
            this.isPublic = isPublic;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitVarDecl(this);
        }

        @Override
        public String toString() {
            String keyword = isConst ? "const" : "var";
            return (isPublic ? "pub " : "") + keyword + " " + name
                    + (initializer != null ? " : " + initializer : "") + ";";
        }

        public String getName() {
            return name;
        }

        public Expression getInitializer() {
            return initializer;
        }

        public boolean isConst() {
            return isConst;
        }

        public boolean isPublic() {
            return isPublic;
        }
    }

    public static class FuncDecl extends Statement {

        private final String name;
        private final List<Parameter> parameters;
        private final List<Node> body;
        private final String variadicParam;
        private final boolean isAsync;
        private final boolean isPure;
        private final boolean isPublic;
        public int nameColumn = 0;

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam) {
            this(name, parameters, body, variadicParam, false, false, false);
        }

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam, boolean isAsync) {
            this(name, parameters, body, variadicParam, isAsync, false, false);
        }

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam, boolean isAsync, boolean isPure) {
            this(name, parameters, body, variadicParam, isAsync, isPure, false);
        }

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam, boolean isAsync, boolean isPure, boolean isPublic) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
            this.variadicParam = variadicParam;
            this.isAsync = isAsync;
            this.isPure = isPure;
            this.isPublic = isPublic;
        }

        public boolean isAsync() {
            return isAsync;
        }

        public boolean isPure() {
            return isPure;
        }

        public boolean isPublic() {
            return isPublic;
        }

        public String getName() {
            return name;
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
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitFuncDecl(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (isPublic) {
                sb.append("pub ");
            }
            if (isAsync) {
                sb.append("async ");
            }
            if (isPure) {
                sb.append("pure ");
            }
            sb.append("fn ").append(name).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                Parameter p = parameters.get(i);
                sb.append(p.name());
                if (p.hasDefault()) {
                    sb.append(" : ").append(p.defaultValue());
                }
                if (i < parameters.size() - 1 || variadicParam != null) {
                    sb.append(", ");
                }
            }
            if (variadicParam != null) {
                sb.append("...").append(variadicParam);
            }
            sb.append(") {...}");
            return sb.toString();
        }
    }

    public static class Return extends Statement {

        private final Expression value;

        public Return(Expression value) {
            this.value = value;
        }

        public Expression getValue() {
            return value;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitReturn(this);
        }

        @Override
        public String toString() {
            return "return" + (value != null ? " " + value : "") + ";";
        }
    }

    public static class Assign extends Statement {

        private final Expression reference;
        private final Expression expression;

        public Assign(Expression reference, Expression expression) {
            this.reference = reference;
            this.expression = expression;
        }

        public Expression getReference() {
            return reference;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitAssign(this);
        }

        @Override
        public String toString() {
            return reference + " : " + expression + ";";
        }
    }

    public static class If extends Statement {

        private final Expression condition;
        private final List<Node> thenBody;
        private final List<Node> elseBody;

        public If(Expression condition, List<Node> ifBody, List<Node> elseBody) {
            this.condition = condition;
            this.thenBody = ifBody;
            this.elseBody = elseBody;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitIf(this);
        }

        @Override
        public String toString() {
            boolean hasElse = elseBody != null && !elseBody.isEmpty();
            return "if (" + condition + ") {...}" + (hasElse ? " else {...}" : "");
        }

        public Expression getCondition() {
            return condition;
        }

        public List<Node> getThenBody() {
            return thenBody;
        }

        public List<Node> getElseBody() {
            return elseBody;
        }
    }

    public static class Loop extends Statement {

        private final List<Node> varDecls;
        private final Expression condition;
        private final List<Node> postExpressions;
        private final VarDecl iterator;
        private final Expression collection;
        private final List<Node> body;

        private Loop(List<Node> varDecls, Expression condition, List<Node> postExpressions,
                VarDecl iterator, Expression collection, List<Node> body) {
            this.varDecls = varDecls;
            this.condition = condition;
            this.postExpressions = postExpressions;
            this.iterator = iterator;
            this.collection = collection;
            this.body = body;
        }

        public static Loop cStyle(List<Node> varDecls, Expression condition,
                List<Node> postExpressions, List<Node> body) {
            return new Loop(varDecls, condition, postExpressions, null, null, body);
        }

        public static Loop foreachStyle(VarDecl iterator, Expression collection, List<Node> body) {
            return new Loop(List.of(), null, List.of(), iterator, collection, body);
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitLoop(this);
        }

        @Override
        public String toString() {
            return isForeach()
                    ? "for (var " + iterator.getName() + " in " + collection + ") {...}"
                    : "for (...; " + condition + "; ...) {...}";
        }

        public boolean isForeach() {
            return iterator != null;
        }

        public List<Node> getVarDecls() {
            return varDecls;
        }

        public Expression getCondition() {
            return condition;
        }

        public List<Node> getPostExpressions() {
            return postExpressions;
        }

        public VarDecl getIterator() {
            return iterator;
        }

        public Expression getCollection() {
            return collection;
        }

        public List<Node> getBody() {
            return body;
        }
    }

    public static class While extends Statement {

        private final Expression condition;
        private final List<Node> body;
        private final boolean doModifier;

        public While(Expression condition, List<Node> body, boolean doModifier) {
            this.condition = condition;
            this.body = body;
            this.doModifier = doModifier;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitWhile(this);
        }

        @Override
        public String toString() {
            return doModifier
                    ? "do {...} while (" + condition + ");"
                    : "while (" + condition + ") {...}";
        }

        public Expression getCondition() {
            return condition;
        }

        public List<Node> getBody() {
            return body;
        }

        public boolean getDoModifier() {
            return doModifier;
        }
    }

    public static class Break extends Statement {

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitBreak(this);
        }

        @Override
        public String toString() {
            return "break;";
        }
    }

    public static class Continue extends Statement {

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitContinue(this);
        }

        @Override
        public String toString() {
            return "continue;";
        }
    }

    public static class Block extends Statement {

        private final List<Node> body;

        public Block(List<Node> body) {
            this.body = body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitBlock(this);
        }

        @Override
        public String toString() {
            return "{...}";
        }

        public List<Node> getBody() {
            return body;
        }
    }

    public static class SwitchCase {

        private final Expression value;
        private final List<Node> body;

        public SwitchCase(Expression value, List<Node> body) {
            this.value = value;
            this.body = body;
        }

        public Expression getValue() {
            return value;
        }

        public List<Node> getBody() {
            return body;
        }

        @Override
        public String toString() {
            return value + " -> {...}";
        }
    }

    public static class Switch extends Statement {

        private final Expression subject;
        private final List<SwitchCase> cases;
        private final List<Node> defaultBody;

        public Switch(Expression subject, List<SwitchCase> cases, List<Node> defaultBody) {
            this.subject = subject;
            this.cases = cases;
            this.defaultBody = defaultBody;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitSwitch(this);
        }

        @Override
        public String toString() {
            return "switch (" + subject + ") {...}";
        }

        public Expression getSubject() {
            return subject;
        }

        public List<SwitchCase> getCases() {
            return cases;
        }

        public List<Node> getDefaultBody() {
            return defaultBody;
        }
    }

    public static class ModuleDecl extends Statement {

        private final String moduleName;

        public ModuleDecl(String moduleName) {
            this.moduleName = moduleName;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return null;
        }

        @Override
        public String toString() {
            return "module " + moduleName + ";";
        }

        public String getModuleName() {
            return moduleName;
        }
    }

    public static class Throw extends Statement {

        private final ThrownException value;

        public Throw(ThrownException value) {
            this.value = value;
        }

        public Expression getValue() {
            return value;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitThrow(this);
        }

        @Override
        public String toString() {
            return "throw " + value + ";";
        }
    }

    public static class CatchClause {

        private final String typeFilter;
        private final String paramName;
        private final List<Node> body;

        public CatchClause(String typeFilter, String paramName, List<Node> body) {
            this.typeFilter = typeFilter;
            this.paramName = paramName;
            this.body = body;
        }

        public String getTypeFilter() {
            return typeFilter;
        }

        public String getParamName() {
            return paramName;
        }

        public List<Node> getBody() {
            return body;
        }

        @Override
        public String toString() {
            return "catch(" + (typeFilter != null ? typeFilter + " " : "") + paramName + ") {...}";
        }
    }

    public static class TryCatch extends Statement {

        private final List<Node> tryBody;
        private final List<CatchClause> catchClauses;
        private final List<Node> finallyBody;

        public TryCatch(List<Node> tryBody, List<CatchClause> catchClauses, List<Node> finallyBody) {
            this.tryBody = tryBody;
            this.catchClauses = catchClauses;
            this.finallyBody = finallyBody;
        }

        public List<Node> getTryBody() {
            return tryBody;
        }

        public List<CatchClause> getCatchClauses() {
            return catchClauses;
        }

        public List<Node> getFinallyBody() {
            return finallyBody;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitTryCatch(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("try {...}");
            for (CatchClause clause : catchClauses) {
                sb.append(" ").append(clause);
            }
            if (finallyBody != null && !finallyBody.isEmpty()) {
                sb.append(" finally {...}");
            }
            return sb.toString();
        }
    }

    public static class EnumDecl extends Statement {

        private final Map<String, Expression> values;
        private final String identifier;
        private final boolean isPublic;

        public EnumDecl(Map<String, Expression> values, String identifier) {
            this(values, identifier, false);
        }

        public EnumDecl(Map<String, Expression> values, String identifier, boolean isPublic) {
            this.values = values;
            this.identifier = identifier;
            this.isPublic = isPublic;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitEnum(this);
        }

        @Override
        public String toString() {
            return (isPublic ? "pub " : "") + "enum " + identifier
                    + " {" + String.join(", ", values.keySet()) + "}";
        }

        public Map<String, Expression> getValues() {
            return values;
        }

        public String getIdentifier() {
            return identifier;
        }

        public boolean isPublic() {
            return isPublic;
        }
    }

    public static class Lock extends Statement {

        private final Expression mutex;
        private final List<Node> body;

        public Lock(Expression mutex, List<Node> body) {
            this.mutex = mutex;
            this.body = body;
        }

        public Expression getMutex() {
            return mutex;
        }

        public List<Node> getBody() {
            return body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitLock(this);
        }

        @Override
        public String toString() {
            return "lock (" + mutex + ") {...}";
        }
    }

    public static class VarDestructure extends Statement {

        private final List<String> names;
        private final List<Integer> nameColumns;
        private final Expression initializer;

        public VarDestructure(List<String> names, List<Integer> nameColumns, Expression initializer) {
            this.names = names;
            this.nameColumns = nameColumns;
            this.initializer = initializer;
        }

        public List<String> getNames() {
            return names;
        }

        /** Column of each name in {@link #getNames()}, in the same order; all on this statement's own {@link #line}. */
        public List<Integer> getNameColumns() {
            return nameColumns;
        }

        public Expression getInitializer() {
            return initializer;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitVarDestructure(this);
        }

        @Override
        public String toString() {
            return "var (" + String.join(", ", names) + ") : " + initializer + ";";
        }
    }

    public static class ComptimeBlock extends Statement {

        private final List<Node> body;

        public ComptimeBlock(List<Node> body) {
            this.body = body;
        }

        public List<Node> getBody() {
            return body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitComptimeBlock(this);
        }

        @Override
        public String toString() {
            return "comptime {...}";
        }
    }

    public static class StaticAssert extends Statement {

        private final Expression condition;
        private final Expression message;

        public StaticAssert(Expression condition, Expression message) {
            this.condition = condition;
            this.message = message;
        }

        public Expression getCondition() {
            return condition;
        }

        public Expression getMessage() {
            return message;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitStaticAssert(this);
        }

        @Override
        public String toString() {
            return "static_assert(" + condition + (message != null ? ", " + message : "") + ");";
        }
    }

    public static class TestCall extends Statement {

        private final Expression name;
        private final Expression testFn;

        public TestCall(Expression name, Expression testFn) {
            this.name = name;
            this.testFn = testFn;
        }

        public Expression getName() {
            return name;
        }

        public Expression getTestFn() {
            return testFn;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitTestCall(this);
        }

        @Override
        public String toString() {
            return "test(" + name + ", " + testFn + ");";
        }
    }
}
