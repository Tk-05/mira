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

        environment.define("jsonNested", new NativeFunction(3, args -> {
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

        environment.define("jsonIndexOf", new NativeFunction(2, args -> {
            if (!(args.get(0) instanceof ListExpression list)) {
                throw new RuntimeException("jsonIndexOf: first argument must be a list");
            }
            String target = String.valueOf(args.get(1));
            List<Expression> members = list.getMembers();
            for (int i = 0; i < members.size(); i++) {
                String val = members.get(i) instanceof DumbExpression d
                        ? String.valueOf(d.getValue())
                        : String.valueOf(members.get(i));
                if (val.equals(target)) {
                    return (double) i;
                }
            }
            return (double) -1;
        }));

        environment.define("jsonKeys", new NativeFunction(1, args -> {
            String json = String.valueOf(args.get(0)).trim();
            List<Expression> members = new ArrayList<>();
            int start = json.indexOf('{');
            if (start < 0) {
                return new ListExpression(members);
            }
            char[] chars = json.toCharArray();
            int depth = 0;
            boolean inStr = false;
            for (int i = start + 1; i < chars.length; i++) {
                char c = chars[i];
                if (c == '"' && (i == 0 || chars[i - 1] != '\\')) {
                    if (!inStr) {
                        inStr = true;
                        if (depth == 0) {
                            int end = json.indexOf('"', i + 1);
                            if (end > i) {
                                String after = json.substring(end + 1).stripLeading();
                                if (after.startsWith(":")) {
                                    String key = json.substring(i + 1, end);
                                    members.add(new DumbExpression(new Token(TokenType.EXPRESSION, key, 0, 0)));
                                    i = end;
                                    inStr = false;
                                    continue;
                                }
                            }
                        }
                    } else {
                        inStr = false;
                    }
                }
                if (!inStr) {
                    if (c == '{' || c == '[') {
                        depth++;
                    } else if (c == '}' || c == ']') {
                        depth--;
                    }
                }
            }
            return new ListExpression(members);
        }));

        environment.define("jsonSize", new NativeFunction(1, args -> {
            String json = String.valueOf(args.get(0)).trim();
            if (json.startsWith("{")) {
                int count = 0;
                int depth = 0;
                boolean inStr = false;
                char[] chars = json.toCharArray();
                int start = json.indexOf('{');
                for (int i = start + 1; i < chars.length; i++) {
                    char c = chars[i];
                    if (c == '"' && (i == 0 || chars[i - 1] != '\\')) {
                        if (!inStr) {
                            inStr = true;
                            if (depth == 0) {
                                int end = json.indexOf('"', i + 1);
                                if (end > i) {
                                    String after = json.substring(end + 1).stripLeading();
                                    if (after.startsWith(":")) {
                                        count++;
                                        i = end;
                                        inStr = false;
                                        continue;
                                    }
                                }
                            }
                        } else {
                            inStr = false;
                        }
                    }
                    if (!inStr) {
                        if (c == '{' || c == '[') {
                            depth++;
                        } else if (c == '}' || c == ']') {
                            depth--;
                        }
                    }
                }
                return (double) count;
            } else if (json.startsWith("[")) {
                int count = 0, depth = 0;
                boolean inStr = false;
                for (int i = 1; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '"') {
                        inStr = !inStr;
                    }
                    if (!inStr) {
                        if (c == '{' || c == '[') {
                            depth++;
                        } else if (c == '}' || c == ']') {
                            depth--;
                        } else if (c == ',' && depth == 0) {
                            count++;
                        }
                    }
                }
                String inner = json.substring(1, json.length() - 1).trim();
                return inner.isEmpty() ? 0.0 : (double) (count + 1);
            }
            return 0.0;
        }));

        environment.define("jsonSet", new NativeFunction(3, args -> {
            String json = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            Object value = args.get(2);
            String valStr;
            if (value instanceof Double d) {
                valStr = d == d.longValue() ? String.valueOf(d.longValue()) : String.valueOf(d);
            } else if (value instanceof Boolean b) {
                valStr = String.valueOf(b);
            } else if (value == null || value.toString().equals("null")) {
                valStr = "null";
            } else {
                valStr = "\"" + String.valueOf(value) + "\"";
            }
            String keyPattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"[^\"]*\"|[^,}\\]]+)";
            String replacement = "\"" + key + "\": " + valStr;
            String result = json.replaceFirst(keyPattern, Matcher.quoteReplacement(replacement));
            if (result.equals(json)) {
                int lastBrace = json.lastIndexOf('}');
                if (lastBrace < 0) {
                    return json;
                }
                String trimmed = json.substring(0, lastBrace).trim();
                String sep = trimmed.endsWith("{") ? "" : ", ";
                result = trimmed + sep + "\"" + key + "\": " + valStr + "}";
            }
            return result;
        }));
    }
}
