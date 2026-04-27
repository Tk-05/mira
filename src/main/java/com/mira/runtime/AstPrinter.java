package com.mira.runtime;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
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
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;

public class AstPrinter implements ExprVisitor<String>, StmtVisitor<String> {

    private int depth = 0;

    public String print(List<Node> program) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < program.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(node(program.get(i)));
        }
        return sb.toString();
    }

    private String pad() {
        return "  ".repeat(depth);
    }

    private String node(Node n) {
        return switch (n) {
            case com.mira.parser.nodes.statement.Statement s ->
                s.accept(this);
            case Expression e ->
                e.accept(this);
            default ->
                pad() + "<unknown>";
        };
    }

    private String body(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        depth++;
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append('\n').append(node(n));
        }
        depth--;
        return sb.toString();
    }

    private String child(Expression e) {
        if (e == null) {
            return "";
        }
        depth++;
        String result = '\n' + e.accept(this);
        depth--;
        return result;
    }

    private String params(List<Parameter> params, String variadicParam) {
        StringJoiner sj = new StringJoiner(", ");
        for (Parameter p : params) {
            sj.add(p.hasDefault() ? p.name() + "=…" : p.name());
        }
        if (variadicParam != null) {
            sj.add("..." + variadicParam);
        }
        return sj.toString();
    }

    @Override
    public <T> T visitDumbExpr(DumbExpression expression) {
        return (T) (pad() + "Literal [" + expression.getValue() + "]");
    }

    @Override
    public <T> T visitBinaryExpr(BinaryExpression expression) {
        return (T) (pad() + "Binary [" + expression.getOperator().getLexeme() + "]"
                + child(expression.getLeft())
                + child(expression.getRight()));
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        return (T) (pad() + "Unary [" + expression.getOperation().getLexeme() + "]"
                + child(expression.getRight()));
    }

    @Override
    public <T> T visitTernaryExpr(TernaryExpression expression) {
        return (T) (pad() + "Ternary [?:]"
                + child(expression.getCondition())
                + child(expression.getThenExpr())
                + child(expression.getElseExpr()));
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        return (T) (pad() + "Call [" + expression.getCallee().accept(this).strip() + "]"
                + body(expression.getArguments().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        return (T) (pad() + "Call [" + expression.getAlias() + "." + expression.getFunctionName() + "]"
                + body(expression.getArguments().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitMethodCallExpression(MethodCallExpression expression) {
        String op = expression.isOptional() ? "?." : ".";
        return (T) (pad() + "MethodCall [" + op + expression.getMethod() + "]"
                + child(expression.getObject())
                + body(expression.getArguments().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitFieldAccessExpression(FieldAccessExpression expression) {
        String op = expression.isOptional() ? "?." : ".";
        return (T) (pad() + "FieldAccess [" + op + expression.getField() + "]"
                + child(expression.getObject()));
    }

    @Override
    public <T> T visitAccessExpr(AccessExpression expression) {
        return (T) (pad() + "Index []"
                + child(expression.getReference())
                + body(expression.getIndecies().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitArrayExpr(ArrayExpression expression) {
        return (T) (pad() + "Array"
                + body(expression.getMembers().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitListExpr(ListExpression expression) {
        return (T) (pad() + "List"
                + body(expression.getMembers().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public <T> T visitMapExpr(MapExpression expression) {
        StringBuilder sb = new StringBuilder(pad() + "Map");
        depth++;
        for (Map.Entry<String, Expression> entry : expression.getEntries().entrySet()) {
            sb.append('\n').append(pad()).append("Key [").append(entry.getKey()).append("]");
            sb.append(child(entry.getValue()));
        }
        depth--;
        return (T) sb.toString();
    }

    @Override
    public <T> T visitObjectExpression(ObjectExpression expression) {
        StringBuilder sb = new StringBuilder(pad() + "Object");
        depth++;
        for (VarDecl v : expression.getVarDecls()) {
            sb.append('\n').append(visitVarDecl(v));
        }
        for (FuncDecl f : expression.getMethods()) {
            sb.append('\n').append(visitFuncDecl(f));
        }
        depth--;
        return (T) sb.toString();
    }

    @Override
    public <T> T visitRangeExpression(RangeExpression expression) {
        return (T) (pad() + "Range"
                + child(expression.getStart())
                + child(expression.getEnd())
                + (expression.getStepsize() != null ? child(expression.getStepsize()) : ""));
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression expression) {
        return (T) (pad() + "Lambda [(" + params(expression.getParameters(), expression.getVariadicParam()) + ")]"
                + body(expression.getBody()));
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        return (T) (pad() + "Complex"
                + body(expression.getExpressions().stream()
                        .map(e -> (Node) e).toList()));
    }

    @Override
    public String visitVarDecl(VarDecl stmt) {
        String keyword = stmt.isConst() ? "const" : "var";
        return pad() + "VarDecl [" + keyword + " " + stmt.getName() + "]"
                + child(stmt.getInitializer());
    }

    @Override
    public String visitFuncDecl(FuncDecl stmt) {
        return pad() + "FuncDecl [" + stmt.getName()
                + "(" + params(stmt.getParameters(), stmt.getVariadicParam()) + ")]"
                + body(stmt.getBody());
    }

    @Override
    public String visitEnum(EnumDecl stmt) {
        StringBuilder sb = new StringBuilder(pad() + "Enum [" + stmt.getIdentifier() + "]");
        depth++;
        for (Map.Entry<String, Object> entry : stmt.getValues().entrySet()) {
            sb.append('\n').append(pad()).append(entry.getKey()).append(" = ").append(entry.getValue());
        }
        depth--;
        return sb.toString();
    }

    @Override
    public String visitVarDestructure(VarDestructure stmt) {
        return pad() + "Destructure [" + String.join(", ", stmt.getNames()) + "]"
                + child(stmt.getInitializer());
    }

    @Override
    public String visitAssign(Assign stmt) {
        return pad() + "Assign"
                + child(stmt.getReference())
                + child(stmt.getExpression());
    }

    @Override
    public String visitOverwrite(Overwrite stmt) {
        return pad() + "Overwrite [" + stmt.getStmt() + "]";
    }

    @Override
    public String visitReturn(Return stmt) {
        return pad() + "Return" + child(stmt.getValue());
    }

    @Override
    public String visitIf(If stmt) {
        StringBuilder sb = new StringBuilder(pad() + "If");
        sb.append(child(stmt.getCondition()));
        depth++;
        sb.append('\n').append(pad()).append("Then");
        sb.append(body(stmt.getThenBody()));
        if (stmt.getElseBody() != null && !stmt.getElseBody().isEmpty()) {
            sb.append('\n').append(pad()).append("Else");
            sb.append(body(stmt.getElseBody()));
        }
        depth--;
        return sb.toString();
    }

    @Override
    public String visitFor(For stmt) {
        StringBuilder sb = new StringBuilder(pad() + "For");
        depth++;
        if (!stmt.getVarDecls().isEmpty()) {
            sb.append('\n').append(pad()).append("Init");
            sb.append(body(stmt.getVarDecls()));
        }
        if (stmt.getCondition() != null) {
            sb.append('\n').append(pad()).append("Cond");
            depth++;
            sb.append('\n').append(stmt.getCondition().accept(this));
            depth--;
        }
        if (!stmt.getPostExpressions().isEmpty()) {
            sb.append('\n').append(pad()).append("Post");
            sb.append(body(stmt.getPostExpressions()));
        }
        sb.append('\n').append(pad()).append("Body");
        depth--;
        sb.append(body(stmt.getBody()));
        return sb.toString();
    }

    @Override
    public String visitWhile(While stmt) {
        String keyword = stmt.getDoModifier() ? "DoWhile" : "While";
        return pad() + keyword
                + child(stmt.getCondition())
                + body(stmt.getBody());
    }

    @Override
    public String visitForeach(Foreach stmt) {
        return pad() + "Foreach [" + stmt.getIterator().getName() + "]"
                + child(stmt.getCollection())
                + body(stmt.getBody());
    }

    @Override
    public String visitBreak(Break stmt) {
        return pad() + "Break";
    }

    @Override
    public String visitContinue(Continue stmt) {
        return pad() + "Continue";
    }

    @Override
    public String visitBlock(Block stmt) {
        return pad() + "Block" + body(stmt.getBody());
    }

    @Override
    public String visitSwitch(Switch stmt) {
        StringBuilder sb = new StringBuilder(pad() + "Switch");
        sb.append(child(stmt.getSubject()));
        depth++;
        for (SwitchCase c : stmt.getCases()) {
            sb.append('\n').append(pad()).append("Case");
            depth++;
            sb.append('\n').append(c.getValue().accept(this));
            depth--;
            sb.append(body(c.getBody()));
        }
        if (stmt.getDefaultBody() != null && !stmt.getDefaultBody().isEmpty()) {
            sb.append('\n').append(pad()).append("Default");
            sb.append(body(stmt.getDefaultBody()));
        }
        depth--;
        return sb.toString();
    }

    @Override
    public String visitThrow(Throw stmt) {
        return pad() + "Throw" + child(stmt.getValue());
    }

    @Override
    public String visitTryCatch(TryCatch stmt) {
        StringBuilder sb = new StringBuilder(pad() + "TryCatch");
        depth++;
        sb.append('\n').append(pad()).append("Try");
        sb.append(body(stmt.getTryBody()));
        for (CatchClause clause : stmt.getCatchClauses()) {
            String label = clause.getTypeFilter() != null ? clause.getTypeFilter() : "*";
            if (clause.getParamName() != null && !clause.getParamName().equals(clause.getTypeFilter())) {
                label += " " + clause.getParamName();
            }
            sb.append('\n').append(pad()).append("Catch [").append(label).append("]");
            sb.append(body(clause.getBody()));
        }
        if (stmt.getFinallyBody() != null && !stmt.getFinallyBody().isEmpty()) {
            sb.append('\n').append(pad()).append("Finally");
            sb.append(body(stmt.getFinallyBody()));
        }
        depth--;
        return sb.toString();
    }

    @Override
    public String visitThrownExpection(ThrownException thrownException) {
        return pad() + "Exception Literal [" + thrownException.getIdentifier() + "]";
    }

    @Override
    public String visitAwaitExpr(AwaitExpression awaitExpression) {
        return pad() + "Await " + node(awaitExpression.getExpr());
    }
}
