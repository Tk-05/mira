package com.mira.runtime.visitors;

import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;

public interface ExprVisitor<T> {

    public <T> T visitDumbExpr(DumbExpression expression);

    public <T> T visitCallExpr(CallExpression expression);

    public <T> T visitComplexExpr(ComplexExpression expression);

    public <T> T visitUnaryExpr(UnaryExpression expression);

    public <T> T visitTupleExpr(TupleExpression expression);

    public <T> T visitAccessExpr(AccessExpression expression);

    public <T> T visitListExpr(ListExpression expression);

    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression);

    public <T> T visitRangeExpression(RangeExpression expression);

    public <T> T visitObjectExpression(ObjectExpression expression);

    public <T> T visitFieldAccessExpression(FieldAccessExpression expression);
}
