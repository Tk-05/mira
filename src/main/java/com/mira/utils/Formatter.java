package com.mira.utils;

import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;

public class Formatter {

    public static String formatToString(Expression expression) {
        switch (expression) {
            case TupleExpression tuple -> {
                StringBuilder sb = new StringBuilder();
                sb.append("[");

                for (int i = 0; i < tuple.getMembers().size(); i++) {
                    Expression elem = tuple.getMembers().get(i);

                    sb.append(formatToString(elem));

                    if (i < tuple.getMembers().size() - 1) {
                        sb.append(", ");
                    }
                }

                sb.append("]");
                return sb.toString();
            }
            case ListExpression list -> {
                StringBuilder sb = new StringBuilder();
                sb.append("{");

                for (int i = 0; i < list.getMembers().size(); i++) {
                    Expression elem = list.getMembers().get(i);

                    sb.append(formatToString(elem));

                    if (i < list.getMembers().size() - 1) {
                        sb.append(", ");
                    }
                }

                sb.append("}");
                return sb.toString();
            }
            default -> {
            }
        }

        return expression.toString();
    }
}
