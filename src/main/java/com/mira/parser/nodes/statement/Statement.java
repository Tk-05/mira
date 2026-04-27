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

    public abstract <T> T accept(StmtVisitor<T> visitor);

    public static class VarDecl extends Statement {

        private final String name;
        private final Expression initializer;
        private final boolean isConst;

        public VarDecl(String name, Expression initializer, boolean isConst) {
            this.name = name;
            this.initializer = initializer;
            this.isConst = isConst;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitVarDecl(this);
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
    }

    public static class FuncDecl extends Statement {

        private final String name;
        private final List<Parameter> parameters;
        private final List<Node> body;
        private final String variadicParam;
        private final boolean isAsync;

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam) {
            this(name, parameters, body, variadicParam, false);
        }

        public FuncDecl(String name, List<Parameter> parameters,
                List<Node> body, String variadicParam, boolean isAsync) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
            this.variadicParam = variadicParam;
            this.isAsync = isAsync;
        }

        public boolean isAsync() {
            return isAsync;
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

    public static class For extends Statement {

        private final List<Node> varDecls;
        private final Expression condition;
        private final List<Node> postExpressions;
        private final List<Node> body;

        public For(List<Node> varDecls, Expression condition, List<Node> postExpressions, List<Node> body) {
            this.varDecls = varDecls;
            this.condition = condition;
            this.postExpressions = postExpressions;
            this.body = body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitFor(this);
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
    }

    public static class Continue extends Statement {

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitContinue(this);
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

        public List<Node> getBody() {
            return body;
        }
    }

    public static class Overwrite extends Statement {

        private final String stmt;

        public Overwrite(String stmt) {
            this.stmt = stmt;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitOverwrite(this);
        }

        public String getStmt() {
            return stmt;
        }
    }

    public static class Foreach extends Statement {

        private final VarDecl iterator;
        private final Expression collection;
        private final List<Node> body;

        public Foreach(VarDecl iterator, Expression collection, List<Node> body) {
            this.iterator = iterator;
            this.collection = collection;
            this.body = body;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitForeach(this);
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
    }

    public static class EnumDecl extends Statement {

        private final Map<String, Object> values;
        private final String identifier;

        public EnumDecl(Map<String, Object> values, String identifier) {
            this.values = values;
            this.identifier = identifier;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitEnum(this);
        }

        public Map<String, Object> getValues() {
            return values;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    public static class VarDestructure extends Statement {

        private final List<String> names;
        private final Expression initializer;

        public VarDestructure(List<String> names, Expression initializer) {
            this.names = names;
            this.initializer = initializer;
        }

        public List<String> getNames() {
            return names;
        }

        public Expression getInitializer() {
            return initializer;
        }

        @Override
        public <T> T accept(StmtVisitor<T> visitor) {
            return visitor.visitVarDestructure(this);
        }
    }
}
