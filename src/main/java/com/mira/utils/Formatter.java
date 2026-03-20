package com.mira.utils;

import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.Tuple;

public class Formatter {

    public static String formatToString(Expression expression) {
        if (expression instanceof Tuple array) {

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

        return expression.toString();
    }
}
