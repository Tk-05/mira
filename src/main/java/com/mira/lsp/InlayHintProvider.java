package com.mira.lsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.mira.format.AstWalker;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.resolver.MiraType;

/**
 * Inferred-type hints for {@code var}/{@code const} declarations that have no
 * explicit type annotation, e.g. {@code var x : 5;} ->
 * {@code var x: Number : 5;} rendered inline. Only ever infers from the
 * initializer's own direct literal shape (number/string/bool/null tokens,
 * array/list/map/object/struct literals, lambdas, and a negated/inverted
 * numeric or boolean literal) - mirroring
 * {@code StaticCheck.literalNodeToType}'s philosophy of never chasing a
 * variable/call/field reference to guess a type, since a wrong guess shown
 * inline is worse than no hint at all. Deliberately reimplemented here rather
 * than reusing StaticCheck's own (instance-stateful, whole-program) inference,
 * which needs a full checker pass this lightweight provider has no reason to
 * run.
 */
public class InlayHintProvider {

    public static List<InlayHint> provide(List<Node> ast, String content, Range range) {
        List<InlayHint> hints = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof VarDecl vd) {
                addTypeHint(vd, content, range, hints);
            }
            AstWalker.children(n, queue);
        }
        return hints;
    }

    private static void addTypeHint(VarDecl vd, String content, Range range, List<InlayHint> hints) {
        if (vd.getType() != null || vd.getInitializer() == null || vd.line <= 0) {
            return;
        }
        int lspLine = vd.line - 1;
        if (lspLine < range.getStart().getLine() || lspLine > range.getEnd().getLine()) {
            return;
        }
        MiraType type = inferLiteralType(vd.getInitializer());
        if (type == null) {
            return;
        }
        Position pos = LspPositions.nameRange(content, vd.line, vd.nameColumn, vd.getName()).getEnd();
        InlayHint hint = new InlayHint(pos, Either.forLeft(": " + MiraType.display(type)));
        hint.setKind(InlayHintKind.Type);
        hint.setPaddingLeft(true);
        hints.add(hint);
    }

    private static MiraType inferLiteralType(Expression expr) {
        return switch (expr) {
            case ListExpression ignored -> MiraType.LIST;
            case ArrayExpression ignored -> MiraType.ARRAY;
            case MapExpression ignored -> MiraType.MAP;
            case ObjectExpression ignored -> MiraType.OBJECT;
            case StructExpression ignored -> MiraType.OBJECT;
            case LambdaExpression ignored -> MiraType.FN;
            case UnaryExpression u when isInvertedNumberOrBool(u) ->
                "!".equals(u.getOperation().getLexeme()) ? MiraType.BOOL : MiraType.NUMBER;
            case DumbExpression d -> literalTokenType(d);
            default -> null;
        };
    }

    private static boolean isInvertedNumberOrBool(UnaryExpression u) {
        if (!(u.getRight() instanceof DumbExpression d) || d.getTokenType() == TokenType.STRING_LITERAL) {
            return false;
        }
        String op = u.getOperation().getLexeme();
        String value = d.getValue();
        if (("-".equals(op) || "~".equals(op)) && !value.isEmpty() && Character.isDigit(value.charAt(0))) {
            return true;
        }
        return "!".equals(op) && ("true".equals(value) || "false".equals(value));
    }

    private static MiraType literalTokenType(DumbExpression d) {
        if (d.getTokenType() == TokenType.STRING_LITERAL) {
            return MiraType.STRING;
        }
        String value = d.getValue();
        if ("true".equals(value) || "false".equals(value)) {
            return MiraType.BOOL;
        }
        if ("null".equals(value)) {
            return MiraType.NULL;
        }
        if (!value.isEmpty() && Character.isDigit(value.charAt(0))) {
            return MiraType.NUMBER;
        }
        return MiraType.STRING;
    }
}
