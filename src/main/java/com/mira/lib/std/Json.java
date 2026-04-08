package com.mira.lib.std;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Json implements Lib {

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    @Override
    public void loadLib(Environment environment) {
        environment.define("jsonGet", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            try {
                Matcher m = Pattern
                        .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.eE+\\-]+)|(true|false|null))")
                        .matcher(json);
                if (m.find()) {
                    for (int i = 1; i <= m.groupCount(); i++) {
                        if (m.group(i) != null) {
                            return m.group(i);
                        }
                    }
                }
                return "";
            } catch (Exception e) {
                throw new RuntimeException("jsonGet failed: " + e.getMessage());
            }
        }));

        environment.define("jsonHas", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            return json.contains("\"" + key + "\"");
        }));

        environment.define("jsonArray", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            try {
                Matcher arrayMatcher = Pattern
                        .compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]")
                        .matcher(json);
                List<Expression> results = new ArrayList<>();
                if (arrayMatcher.find()) {
                    String arrayContent = arrayMatcher.group(1);
                    Matcher itemMatcher = Pattern
                            .compile("\"([^\"]*)\"|([\\d.eE+\\-]+)|(true|false|null)")
                            .matcher(arrayContent);
                    while (itemMatcher.find()) {
                        for (int i = 1; i <= itemMatcher.groupCount(); i++) {
                            if (itemMatcher.group(i) != null) {
                                results.add(wrap(itemMatcher.group(i)));
                                break;
                            }
                        }
                    }
                }
                return new ListExpression(results);
            } catch (Exception e) {
                throw new RuntimeException("jsonArray failed: " + e.getMessage());
            }
        }));

        environment.define("jsonBuild", new NativeFunction(2, args -> {
            if (!(args.get(0) instanceof ListExpression keys)
                    || !(args.get(1) instanceof ListExpression values)) {
                throw new RuntimeException("jsonBuild requires two lists");
            }
            List<Expression> k = keys.getMembers();
            List<Expression> v = values.getMembers();
            if (k.size() != v.size()) {
                throw new RuntimeException("jsonBuild: keys and values must have same size");
            }

            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < k.size(); i++) {
                String key = k.get(i) instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(k.get(i));
                String val = v.get(i) instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(v.get(i));
                sb.append("\"").append(key).append("\":");
                if (val.matches("-?\\d+(\\.\\d+)?") || val.equals("true") || val.equals("false") || val.equals("null")) {
                    sb.append(val);
                } else {
                    sb.append("\"").append(val).append("\"");
                }
                if (i < k.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("}");
            return sb.toString();
        }));

        environment.define("jsonFormat", new NativeFunction(1, args -> {
            String json = String.valueOf(args.get(0));
            StringBuilder sb = new StringBuilder();
            int indent = 0;
            boolean inString = false;
            for (char c : json.toCharArray()) {
                if (c == '"') {
                    inString = !inString;
                }
                if (!inString) {
                    switch (c) {
                        case '{', '[' -> {
                            sb.append(c).append('\n').append("  ".repeat(++indent));
                            continue;
                        }
                        case '}', ']' -> {
                            sb.append('\n').append("  ".repeat(--indent)).append(c);
                            continue;
                        }
                        case ',' -> {
                            sb.append(c).append('\n').append("  ".repeat(indent));
                            continue;
                        }
                        case ':' -> {
                            sb.append(": ");
                            continue;
                        }
                        default -> {
                        }
                    }
                }
                sb.append(c);
            }
            return sb.toString();
        }));

        environment.define("getArray", new NativeFunction(3, args -> {
            String json = String.valueOf(args.get(0));
            String parentKey = String.valueOf(args.get(1));
            String arrayKey = String.valueOf(args.get(2));
            try {
                Matcher objectMatcher = Pattern
                        .compile("\"" + Pattern.quote(parentKey) + "\"\\s*:\\s*\\{([^}]*)\\}")
                        .matcher(json);
                if (!objectMatcher.find()) {
                    throw new RuntimeException("jsonGetArray: parent key '" + parentKey + "' not found");
                }
                String nested = "{" + objectMatcher.group(1) + "}";

                Matcher arrayMatcher = Pattern
                        .compile("\"" + Pattern.quote(arrayKey) + "\"\\s*:\\s*\\[([^\\]]*)]")
                        .matcher(nested);
                List<Expression> results = new ArrayList<>();
                if (arrayMatcher.find()) {
                    String arrayContent = arrayMatcher.group(1);
                    Matcher itemMatcher = Pattern
                            .compile("\"([^\"]*)\"|([\\d.eE+\\-]+)|(true|false|null)")
                            .matcher(arrayContent);
                    while (itemMatcher.find()) {
                        for (int i = 1; i <= itemMatcher.groupCount(); i++) {
                            if (itemMatcher.group(i) != null) {
                                results.add(wrap(itemMatcher.group(i)));
                                break;
                            }
                        }
                    }
                }
                return new ListExpression(results);
            } catch (RuntimeException e) {
                throw new RuntimeException("jsonGetArray failed: " + e.getMessage());
            }
        }));
    }
}
