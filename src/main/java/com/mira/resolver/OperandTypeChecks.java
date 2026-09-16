package com.mira.resolver;

import com.mira.error.resolver.StaticCheckError.BinaryOperatorTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.UnaryOperatorTypeMismatchError;
import com.mira.error.resolver.StaticCheckError.VariableNotCallableError;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.TypeAnnotation;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import static com.mira.resolver.StaticCheckSupport.ARITHMETIC_TYPE_CHECKED_OPERATORS;
import com.mira.resolver.StaticCheckSupport.OperandTypes;
import static com.mira.resolver.StaticCheckSupport.isIdentifier;
import static com.mira.resolver.StaticCheckSupport.isNumberType;
import static com.mira.resolver.StaticCheckSupport.isStringType;
import static com.mira.resolver.StaticCheckSupport.sameNamedType;

/**
 * Binary/unary/comparison operand-type checks and the bareword-callable check
 * (`checkVariableCallable`), which shares {@code inferType}-based inference
 * with the operand checks below it. Delegates back to {@link StaticCheck} for
 * the type-inference/resolution machinery (`inferType`,
 * `resolveTypeAnnotation`) and its shared state (`declaredVarTypes`,
 * `userFuncDecls`, `errors`) - those stay centralized there since nearly every
 * other check in the file also depends on them.
 */
final class OperandTypeChecks {

    private final StaticCheck owner;

    OperandTypeChecks(StaticCheck owner) {
        this.owner = owner;
    }

    /**
     * Shared gate for every operand-type check below: resolves both operand types
     * only when at least one side carries an explicit annotation (same rule as
     * checkAssignable's callers everywhere else in this file - a bare literal/
     * bareword mismatch like `5 - "oops"` stays covered by the pre-existing, softer
     * isStringLiteral-based warnings and must not escalate into a hard error here),
     * and only when both sides resolve to a concrete, non-Any, non-nullable type
     * worth comparing.
     */
    private OperandTypes resolveGatedOperandTypes(Node left, Node right) {
        TypeAnnotation leftExplicit = inferExplicitlyTypedOperand(left);
        TypeAnnotation rightExplicit = inferExplicitlyTypedOperand(right);
        if (leftExplicit == null && rightExplicit == null) {
            return null;
        }
        TypeAnnotation leftType = leftExplicit != null ? leftExplicit : owner.inferType(left);
        TypeAnnotation rightType = rightExplicit != null ? rightExplicit : owner.inferType(right);
        if (leftType == null || rightType == null || "Any".equals(leftType.name()) || "Any".equals(rightType.name())
                || leftType.nullable() || rightType.nullable()) {
            return null;
        }
        return new OperandTypes(leftType, rightType);
    }

    void checkBinaryOperandTypes(BinaryExpression e) {
        String op = e.getOperator().getLexeme();
        if (!ARITHMETIC_TYPE_CHECKED_OPERATORS.contains(op)) {
            return;
        }
        OperandTypes types = resolveGatedOperandTypes(e.getLeft(), e.getRight());
        if (types == null) {
            return;
        }
        // '+' is also string concatenation - valid whenever either side is a
        // String (the other side gets stringified), not just when both sides
        // match exactly like every other arithmetic operator requires.
        boolean mismatch = "+".equals(op)
                ? !(isNumberType(types.left()) && isNumberType(types.right())) && !isStringType(types.left())
                        && !isStringType(types.right())
                : !isNumberType(types.left()) || !isNumberType(types.right());
        if (mismatch) {
            owner.errors.add(new BinaryOperatorTypeMismatchError(op, types.left().toString(), types.right().toString(),
                    e.getOperator().getLine(), e.getOperator().getColumn()));
        }
    }

    void checkComparisonOperandTypes(BinaryExpression e) {
        OperandTypes types = resolveGatedOperandTypes(e.getLeft(), e.getRight());
        if (types == null || sameNamedType(types.left(), types.right())) {
            return;
        }
        owner.errors.add(new BinaryOperatorTypeMismatchError(e.getOperator().getLexeme(), types.left().toString(),
                types.right().toString(), e.getOperator().getLine(), e.getOperator().getColumn()));
    }

    /**
     * Builds the same synthetic "$" unary wrapper the parser itself builds for a
     * bareword variable read (see {@code Parser.wrapAsVariableRef}) - lets
     * name-only-callee checks reuse $-ref-shaped inference (inferType,
     * checkVariableCallable) without duplicating it.
     */
    UnaryExpression asDollarRef(DumbExpression nameExpr) {
        Token dollar = new Token(TokenType.OPERATION, "$", nameExpr.getLine(), nameExpr.getColumn());
        return new UnaryExpression(dollar, nameExpr);
    }

    void checkVariableCallable(UnaryExpression dollarRef, DumbExpression nameExpr) {
        TypeAnnotation type = owner.inferType(dollarRef);
        if (type == null || "Any".equals(type.name()) || type.nullable()) {
            return;
        }
        if (type.isFunctionType() || "Fn".equals(type.name())) {
            return;
        }
        owner.errors.add(new VariableNotCallableError(nameExpr.getValue(), type.toString(), nameExpr.getLine(),
                nameExpr.getColumn()));
    }

    void checkUnaryOperandType(UnaryExpression e) {
        TypeAnnotation type = inferExplicitlyTypedOperand(e.getRight());
        if (type == null || isNumberType(type)) {
            return;
        }
        owner.errors.add(new UnaryOperatorTypeMismatchError(e.getOperation().getLexeme(), type.toString(),
                e.getOperation().getLine(), e.getOperation().getColumn()));
    }

    /**
     * Explicit-annotation-only variant of inferType, used to gate
     * resolveGatedOperandTypes/checkUnaryOperandType.
     */
    private TypeAnnotation inferExplicitlyTypedOperand(Node expr) {
        if (expr instanceof UnaryExpression u && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d && isIdentifier(d)) {
            return owner.declaredVarTypes.get(d.getValue());
        }
        if (expr instanceof CallExpression call && call.getCallee() instanceof DumbExpression callee
                && isIdentifier(callee)) {
            FuncDecl fn = owner.userFuncDecls.get(callee.getValue());
            if (fn != null && fn.getReturnType() != null) {
                return owner.resolveTypeAnnotation(fn.getReturnType());
            }
        }
        return null;
    }
}
