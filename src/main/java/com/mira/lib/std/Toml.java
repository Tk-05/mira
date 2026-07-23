package com.mira.lib.std;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.NullValue;

public class Toml implements Lib {

    private static Expression wrap(Object val) {
        if (val instanceof Expression e) {
            return e;
        }
        if (val instanceof Map<?, ?> m) {
            LinkedHashMap<String, Expression> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                map.put(String.valueOf(entry.getKey()), wrap(entry.getValue()));
            }
            return new MapExpression(map);
        }
        if (val instanceof List<?> list) {
            List<Expression> members = new ArrayList<>();
            for (Object item : list) {
                members.add(wrap(item));
            }
            return new ListExpression(members);
        }
        return new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(val), 0, 0));
    }

    private static Object evaluateLeaf(Expression e) {
        if (!(e instanceof DumbExpression d)) {
            return e;
        }
        String v = d.getValue();
        if (v.equals("true")) {
            return Boolean.TRUE;
        }
        if (v.equals("false")) {
            return Boolean.FALSE;
        }
        if (v.equals("null")) {
            return NullValue.INSTANCE;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignored) {
        }
        return v;
    }

    private static MapExpression tomlToMap(Map<String, Object> parsed) {
        LinkedHashMap<String, Expression> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            map.put(entry.getKey(), wrap(entry.getValue()));
        }
        return new MapExpression(map);
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("parse", new NativeFunction(1, "tomlStr", args -> {
            Map<String, Object> parsed = TomlParser.parse(String.valueOf(args.get(0)));
            return tomlToMap(parsed);
        }));

        environment.define("parseFile", new NativeFunction(1, "path", args -> {
            try {
                String content = Files.readString(Path.of(String.valueOf(args.get(0))));
                return tomlToMap(TomlParser.parse(content));
            } catch (IOException e) {
                throw new RuntimeException("toml.parseFile failed: " + e.getMessage());
            }
        }));

        environment.define("get", new NativeFunction(2, "map, key", args -> {
            if (!(args.get(0) instanceof MapExpression map)) {
                throw new RuntimeException("toml.get: expected map");
            }
            String key = String.valueOf(args.get(1));
            Expression val = map.getEntries().get(key);
            return val != null ? evaluateLeaf(val) : NullValue.INSTANCE;
        }));

        environment.define("getArray", new NativeFunction(2, "map, key", args -> {
            if (!(args.get(0) instanceof MapExpression map)) {
                throw new RuntimeException("toml.getArray: expected map");
            }
            String key = String.valueOf(args.get(1));
            Expression val = map.getEntries().get(key);
            if (val instanceof ListExpression l) {
                return l;
            }
            return new ListExpression(new ArrayList<>());
        }));

        environment.define("has", new NativeFunction(2, "map, key", args -> {
            if (!(args.get(0) instanceof MapExpression map)) {
                return false;
            }
            return map.getEntries().containsKey(String.valueOf(args.get(1)));
        }));
    }
}
