package com.mira.runtime.visitors;

import com.mira.parser.nodes.expression.Expression.Access;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.Tuple;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;

public interface ExprVisitor<T> {

    public <T> T visitDumbExpr(DumbExpression expression);

    public <T> T visitCallExpr(CallExpression expression);

    public <T> T visitComplexExpr(ComplexExpression expression);

    public <T> T visitUnaryExpr(UnaryExpression expression);

    public <T> T visitTupleExpr(Tuple expression);

    public <T> T visitAccessExpr(Access expression);
}
