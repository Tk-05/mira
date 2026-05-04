package com.mira.runtime.visitors;

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
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;

public interface ExprVisitor<T> {

    public <T> T visitBinaryExpr(BinaryExpression expression);

    public <T> T visitDumbExpr(DumbExpression expression);

    public <T> T visitCallExpr(CallExpression expression);

    public <T> T visitComplexExpr(ComplexExpression expression);

    public <T> T visitUnaryExpr(UnaryExpression expression);

    public <T> T visitArrayExpr(ArrayExpression expression);

    public <T> T visitAccessExpr(AccessExpression expression);

    public <T> T visitListExpr(ListExpression expression);

    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression);

    public <T> T visitRangeExpression(RangeExpression expression);

    public <T> T visitObjectExpression(ObjectExpression expression);

    public <T> T visitFieldAccessExpression(FieldAccessExpression expression);

    public <T> T visitMethodCallExpression(MethodCallExpression expression);

    public <T> T visitLambdaExpr(LambdaExpression expression);

    public <T> T visitMapExpr(MapExpression expression);

    public <T> T visitTernaryExpr(TernaryExpression expression);

    public <T> T visitThrownExpection(ThrownException expression);

    public <T> T visitAwaitExpr(AwaitExpression expression);

    public <T> T visitSwitchExpr(SwitchExpression expression);

    public <T> T visitTypeofExpr(TypeofExpression expression);
}
