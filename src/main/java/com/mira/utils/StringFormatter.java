package com.mira.utils;

import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;

public class StringFormatter {

    public static String formatToString(Expression expression) {
        switch (expression) {
            case ArrayExpression array -> {
                StringBuilder sb = new StringBuilder();
                sb.append("[");

                for (int i = 0; i < array.getMembers().size(); i++) {
                    Expression elem = array.getMembers().get(i);

                    sb.append(formatToString(elem));

                    if (i < array.getMembers().size() - 1) {
                        sb.append(", ");
                    }
                }

                sb.append("]");
                return sb.toString();
            }
            case TupleExpression tuple -> {
                StringBuilder sb = new StringBuilder();
                sb.append("(");

                for (int i = 0; i < tuple.getMembers().size(); i++) {
                    Expression elem = tuple.getMembers().get(i);

                    sb.append(formatToString(elem));

                    if (i < tuple.getMembers().size() - 1) {
                        sb.append(", ");
                    }
                }

                sb.append(")");
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
            case MapExpression map -> {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                int i = 0;
                for (var entry : map.getEntries().entrySet()) {
                    sb.append("\"").append(entry.getKey()).append("\": ");
                    sb.append(formatToString(entry.getValue()));
                    if (i++ < map.getEntries().size() - 1) {
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
