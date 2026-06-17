package com.mira.lib.std;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Csv implements Lib {

    private static Expression wrap(String s) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, s, 0, 0));
    }

    private static List<String> parseRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static List<List<String>> parseCsv(String csvStr) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : csvStr.split("\n", -1)) {
            if (!line.isEmpty()) {
                rows.add(parseRow(line));
            }
        }
        return rows;
    }

    private static List<Expression> toMembers(Object arg) {
        return switch (arg) {
            case ListExpression l ->
                new ArrayList<>(l.getMembers());
            default ->
                throw new RuntimeException("Expected list, got: " + arg.getClass().getSimpleName());
        };
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("parse", new NativeFunction(1, args -> {
            List<List<String>> rows = parseCsv(String.valueOf(args.get(0)));
            List<Expression> result = new ArrayList<>();
            for (List<String> row : rows) {
                List<Expression> rowExprs = new ArrayList<>();
                for (String field : row) {
                    rowExprs.add(wrap(field));
                }
                result.add(new ListExpression(rowExprs));
            }
            return new ListExpression(result);
        }));

        environment.define("parseWithHeaders", new NativeFunction(1, args -> {
            List<List<String>> rows = parseCsv(String.valueOf(args.get(0)));
            if (rows.isEmpty()) {
                return new ListExpression(new ArrayList<>());
            }
            List<String> headers = rows.get(0);
            List<Expression> result = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                LinkedHashMap<String, Expression> map = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    map.put(headers.get(j), wrap(j < row.size() ? row.get(j) : ""));
                }
                result.add(new MapExpression(map));
            }
            return new ListExpression(result);
        }));

        environment.define("stringify", new NativeFunction(1, args -> {
            List<Expression> rows = toMembers(args.get(0));
            StringBuilder sb = new StringBuilder();
            for (Expression rowExpr : rows) {
                List<Expression> cols = toMembers(rowExpr);
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    String field = cols.get(i) instanceof DumbExpression d
                            ? String.valueOf(d.getValue()) : String.valueOf(cols.get(i));
                    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
                        sb.append('"').append(field.replace("\"", "\"\"")).append('"');
                    } else {
                        sb.append(field);
                    }
                }
                sb.append('\n');
            }
            return sb.toString();
        }));

        environment.define("column", new NativeFunction(2, args -> {
            List<Expression> rows = toMembers(args.get(0));
            int idx = (int) Double.parseDouble(String.valueOf(args.get(1)));
            List<Expression> col = new ArrayList<>();
            for (Expression rowExpr : rows) {
                List<Expression> cols = toMembers(rowExpr);
                col.add(idx < cols.size() ? cols.get(idx) : wrap(""));
            }
            return new ListExpression(col);
        }));

        environment.define("parseRow", new NativeFunction(1, args -> {
            List<String> fields = parseRow(String.valueOf(args.get(0)));
            List<Expression> result = new ArrayList<>();
            for (String f : fields) {
                result.add(wrap(f));
            }
            return new ListExpression(result);
        }));

        environment.define("rowCount", new NativeFunction(1, args
                -> (double) parseCsv(String.valueOf(args.get(0))).size()));
    }
}
