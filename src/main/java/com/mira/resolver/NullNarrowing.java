package com.mira.resolver;

import static com.mira.resolver.StaticCheckSupport.NO_NARROWING;
import static com.mira.resolver.StaticCheckSupport.alwaysReturns;
import static com.mira.resolver.StaticCheckSupport.nullCheckVarName;
import static com.mira.resolver.StaticCheckSupport.union;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.resolver.StaticCheckSupport.NarrowSave;
import com.mira.resolver.StaticCheckSupport.NullCheckNarrowing;

/**
 * `x != null`/`x == null` narrowing (basic, guard-clause, `&&`/`||`
 * composition). Shares {@link StaticCheck}'s own {@code declaredVarTypes} map
 * by reference (not a copy), so an apply/restore pair here is visible to every
 * other check running against the same walk.
 */
final class NullNarrowing {

    private final Map<String, MiraType> declaredVarTypes;

    NullNarrowing(Map<String, MiraType> declaredVarTypes) {
        this.declaredVarTypes = declaredVarTypes;
    }

    /**
     * Recognizes {@code x != null} / {@code x == null} (either operand order) as
     * narrowing a nullable-declared {@code x} to its non-null inner type for the
     * branch where it's known not to be null - the then branch for {@code !=}, the
     * else branch for {@code ==}. Composes through {@code &&} (both sides'
     * then-narrowings apply - both must hold for then to run) and {@code ||} (both
     * sides' else-narrowings apply - De Morgan: neither held for else to run); the
     * other side of each is dropped since which operand actually failed isn't
     * knowable. No narrowing survives past the if itself here - see
     * {@link #detectGuardClause} for the early-return-guard case.
     */
    NullCheckNarrowing detect(Expression condition) {
        if (!(condition instanceof BinaryExpression be)) {
            return NO_NARROWING;
        }
        String op = be.getOperator().getLexeme();
        if ("&&".equals(op)) {
            NullCheckNarrowing left = detect(be.getLeft());
            NullCheckNarrowing right = detect(be.getRight());
            return new NullCheckNarrowing(union(left.thenNarrowedVars(), right.thenNarrowedVars()), List.of());
        }
        if ("||".equals(op)) {
            NullCheckNarrowing left = detect(be.getLeft());
            NullCheckNarrowing right = detect(be.getRight());
            return new NullCheckNarrowing(List.of(), union(left.elseNarrowedVars(), right.elseNarrowedVars()));
        }
        if (!"!=".equals(op) && !"==".equals(op)) {
            return NO_NARROWING;
        }
        String varName = nullCheckVarName(be.getLeft(), be.getRight());
        if (varName == null) {
            return NO_NARROWING;
        }
        return "!=".equals(op)
                ? new NullCheckNarrowing(List.of(varName), List.of())
                : new NullCheckNarrowing(List.of(), List.of(varName));
    }

    List<NarrowSave> apply(List<String> varNames) {
        List<NarrowSave> saves = new ArrayList<>();
        for (String varName : varNames) {
            MiraType current = declaredVarTypes.get(varName);
            if (current instanceof MiraType.NullableType nt) {
                declaredVarTypes.put(varName, nt.inner());
                saves.add(new NarrowSave(varName, current));
            }
        }
        return saves;
    }

    void restore(List<NarrowSave> saves) {
        for (NarrowSave save : saves) {
            declaredVarTypes.put(save.varName(), save.previous());
        }
    }

    /**
     * Guard-clause narrowing: "if (x == null) { return; } ...rest..." (or its "!=
     * null" + exiting-else mirror) means every later statement in this same body
     * can only run once x is known non-null - unlike the then/else narrowing in
     * {@link #detect}, which is scoped to just inside the if, this is applied and
     * restored around the whole enclosing body, so it covers everything after the
     * guard until that body ends.
     */
    List<String> detectGuardClause(If ifStmt) {
        NullCheckNarrowing narrowing = detect(ifStmt.getCondition());
        if (!narrowing.elseNarrowedVars().isEmpty() && alwaysReturns(ifStmt.getThenBody())) {
            return narrowing.elseNarrowedVars();
        }
        if (!narrowing.thenNarrowedVars().isEmpty() && ifStmt.getElseBody() != null
                && alwaysReturns(ifStmt.getElseBody())) {
            return narrowing.thenNarrowedVars();
        }
        return List.of();
    }
}
