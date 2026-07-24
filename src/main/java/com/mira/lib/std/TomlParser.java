package com.mira.lib.std;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mira.build.BuildException;

public class TomlParser {

    public static Map<String, Object> parse(String content) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("[[")) {
                int end = line.indexOf("]]");
                if (end < 0) {
                    throw new BuildException("TOML line " + (i + 1) + ": unclosed '[['");
                }
                String section = line.substring(2, end).trim();
                current = getOrCreateArrayTable(root, section);
                continue;
            }

            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end < 0) {
                    throw new BuildException("TOML line " + (i + 1) + ": unclosed '['");
                }
                String section = line.substring(1, end).trim();
                current = getOrCreateSection(root, section);
                continue;
            }

            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new BuildException("TOML line " + (i + 1) + ": expected '='");
            }
            String key = line.substring(0, eq).trim();
            String rawVal = line.substring(eq + 1).trim();
            current.put(key, parseValue(rawVal, i + 1));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getOrCreateSection(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (String part : parts) {
            Object existing = cur.get(part);
            if (existing instanceof Map) {
                cur = (Map<String, Object>) existing;
            } else {
                Map<String, Object> sub = new LinkedHashMap<>();
                cur.put(part, sub);
                cur = sub;
            }
        }
        return cur;
    }

    // [[section]] — TOML "array of tables": each occurrence appends a fresh
    // table to a list at `path`, and becomes the table subsequent key=value
    // lines populate, until the next [section]/[[section]]/EOF.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getOrCreateArrayTable(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = cur.get(parts[i]);
            if (existing instanceof Map) {
                cur = (Map<String, Object>) existing;
            } else if (existing instanceof List) {
                List<Object> list = (List<Object>) existing;
                cur = (Map<String, Object>) list.get(list.size() - 1);
            } else {
                Map<String, Object> sub = new LinkedHashMap<>();
                cur.put(parts[i], sub);
                cur = sub;
            }
        }

        String lastKey = parts[parts.length - 1];
        Object existing = cur.get(lastKey);
        List<Object> list;
        if (existing instanceof List) {
            list = (List<Object>) existing;
        } else {
            list = new ArrayList<>();
            cur.put(lastKey, list);
        }
        Map<String, Object> table = new LinkedHashMap<>();
        list.add(table);
        return table;
    }

    static Object parseValue(String raw, int lineNum) {
        if (raw.isEmpty()) {
            throw new BuildException("TOML line " + lineNum + ": empty value");
        }
        if (raw.equals("true")) {
            return Boolean.TRUE;
        }
        if (raw.equals("false")) {
            return Boolean.FALSE;
        }
        if (raw.startsWith("\"")) {
            return parseString(raw, lineNum);
        }
        if (raw.startsWith("[")) {
            return parseArray(raw, lineNum);
        }
        if (raw.startsWith("{")) {
            return parseInlineTable(raw, lineNum);
        }
        String bare = stripComment(raw).trim();
        if (bare.equals("true")) {
            return Boolean.TRUE;
        }
        if (bare.equals("false")) {
            return Boolean.FALSE;
        }
        try {
            return Long.parseLong(bare);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(bare);
        } catch (NumberFormatException ignored) {
        }
        throw new BuildException("TOML line " + lineNum + ": cannot parse value: " + raw);
    }

    static String parseString(String raw, int lineNum) {
        if (!raw.startsWith("\"")) {
            throw new BuildException("TOML line " + lineNum + ": expected '\"'");
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"' ->
                        sb.append('"');
                    case '\\' ->
                        sb.append('\\');
                    case 'n' ->
                        sb.append('\n');
                    case 't' ->
                        sb.append('\t');
                    default -> {
                        sb.append('\\');
                        sb.append(c);
                    }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new BuildException("TOML line " + lineNum + ": unclosed string");
    }

    static List<String> parseArray(String raw, int lineNum) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < raw.length() && raw.charAt(i) != '[') {
            i++;
        }
        i++;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == ']') {
                break;
            }
            if (c == ' ' || c == '\t' || c == ',') {
                i++;
                continue;
            }
            if (c == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                boolean esc = false;
                while (i < raw.length()) {
                    char ch = raw.charAt(i);
                    if (esc) {
                        sb.append(ch);
                        esc = false;
                    } else if (ch == '\\') {
                        esc = true;
                    } else if (ch == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(ch);
                    }
                    i++;
                }
                result.add(sb.toString());
                continue;
            }
            int start = i;
            while (i < raw.length() && raw.charAt(i) != ',' && raw.charAt(i) != ']') {
                i++;
            }
            String elem = raw.substring(start, i).trim();
            if (!elem.isEmpty()) {
                result.add(elem);
            }
        }
        return result;
    }

    static Map<String, Object> parseInlineTable(String raw, int lineNum) {
        Map<String, Object> result = new LinkedHashMap<>();
        int start = raw.indexOf('{') + 1;
        int end = raw.lastIndexOf('}');
        if (end < 0) {
            throw new BuildException("TOML line " + lineNum + ": unclosed '{'");
        }
        String inner = raw.substring(start, end).trim();
        if (inner.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && inner.charAt(i) == ' ') {
                i++;
            }
            if (i >= inner.length()) {
                break;
            }

            int keyStart = i;
            while (i < inner.length() && inner.charAt(i) != '=') {
                i++;
            }
            if (i >= inner.length()) {
                throw new BuildException("TOML line " + lineNum + ": expected '=' in inline table");
            }
            String key = inner.substring(keyStart, i).trim();
            i++; // skip '='
            while (i < inner.length() && inner.charAt(i) == ' ') {
                i++;
            }

            if (i >= inner.length()) {
                throw new BuildException("TOML line " + lineNum + ": missing value in inline table");
            }

            if (inner.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                boolean esc = false;
                while (i < inner.length()) {
                    char c = inner.charAt(i);
                    if (esc) {
                        sb.append(c);
                        esc = false;
                    } else if (c == '\\') {
                        esc = true;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(c);
                    }
                    i++;
                }
                result.put(key, sb.toString());
            } else {
                int valStart = i;
                while (i < inner.length() && inner.charAt(i) != ',') {
                    i++;
                }
                String val = inner.substring(valStart, i).trim();
                result.put(key, parseValue(val, lineNum));
            }

            while (i < inner.length() && (inner.charAt(i) == ',' || inner.charAt(i) == ' ')) {
                i++;
            }
        }
        return result;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
