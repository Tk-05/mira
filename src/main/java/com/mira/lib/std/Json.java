package com.mira.lib.std;

import java.util.ArrayList;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
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
        // get a value from a flat JSON string by key
        environment.define("jsonGet", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            try {
                String pattern = "\"" + key + "\"\\s*:\\s*";
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile(pattern + "\"([^\"]*)\"|" + pattern + "([\\d.eE+\\-]+)|" + pattern + "(true|false|null)")
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

        // check if a key exists in flat JSON
        environment.define("jsonHas", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            return json.contains("\"" + key + "\"");
        }));

        // extract a JSON array into a ListExpression
        environment.define("jsonArray", new NativeFunction(2, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            try {
                java.util.regex.Matcher arrayMatcher = java.util.regex.Pattern
                        .compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]")
                        .matcher(json);
                List<com.mira.parser.nodes.expression.Expression> results = new ArrayList<>();
                if (arrayMatcher.find()) {
                    String arrayContent = arrayMatcher.group(1);
                    java.util.regex.Matcher itemMatcher = java.util.regex.Pattern
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

        // build a simple key-value JSON string
        environment.define("jsonBuild", new NativeFunction(2, args -> {
            if (!(args.get(0) instanceof ListExpression keys)
                    || !(args.get(1) instanceof ListExpression values)) {
                throw new RuntimeException("jsonBuild requires two lists");
            }
            List<com.mira.parser.nodes.expression.Expression> k = keys.getMembers();
            List<com.mira.parser.nodes.expression.Expression> v = values.getMembers();
            if (k.size() != v.size()) {
                throw new RuntimeException("jsonBuild: keys and values must have same size");
            }

            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < k.size(); i++) {
                String key = k.get(i) instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(k.get(i));
                String val = v.get(i) instanceof DumbExpression d ? String.valueOf(d.getValue()) : String.valueOf(v.get(i));
                sb.append("\"").append(key).append("\":");
                // numbers and booleans without quotes
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

        // pretty-print indented JSON (basic)
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
    }
}
