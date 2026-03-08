package com.mira.runtime.visitors;

import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;

public interface ExprVisitor<T> {

    public <T> T visitValueExpr(DumbExpression expression);

    public <T> T visitCallExpr(CallExpression expression);

    public <T> T visitComplexExpr(ComplexExpression expression);

    public <T> T visitUnaryExpr(UnaryExpression expression);
}
