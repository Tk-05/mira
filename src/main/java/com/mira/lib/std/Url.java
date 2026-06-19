package com.mira.lib.std;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.NullValue;

public class Url implements Lib {

    private static DumbExpression wrap(String s) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, s != null ? s : "", 0, 0));
    }

    private static DumbExpression wrapNullable(String s) {
        if (s == null) {
            return new DumbExpression(new Token(TokenType.EXPRESSION, "null", 0, 0));
        }
        return wrap(s);
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("parse", new NativeFunction(1, "urlStr", args -> {
            try {
                URI uri = URI.create(String.valueOf(args.get(0)));
                LinkedHashMap<String, com.mira.parser.nodes.expression.Expression> map = new LinkedHashMap<>();
                map.put("scheme", wrapNullable(uri.getScheme()));
                map.put("host", wrapNullable(uri.getHost()));
                map.put("port", new DumbExpression(new Token(TokenType.EXPRESSION,
                        String.valueOf(uri.getPort() == -1 ? "null" : uri.getPort()), 0, 0)));
                map.put("path", wrapNullable(uri.getPath()));
                map.put("query", wrapNullable(uri.getQuery()));
                map.put("fragment", wrapNullable(uri.getFragment()));
                map.put("user", wrapNullable(uri.getUserInfo()));
                return new MapExpression(map);
            } catch (Exception e) {
                throw new RuntimeException("url.parse failed: " + e.getMessage());
            }
        }));

        environment.define("build", new NativeFunction(4, "scheme, host, path, query", args -> {
            String scheme = String.valueOf(args.get(0));
            String host = String.valueOf(args.get(1));
            String path = String.valueOf(args.get(2));
            String query = String.valueOf(args.get(3));
            StringBuilder sb = new StringBuilder(scheme).append("://").append(host).append(path);
            if (query != null && !query.isBlank() && !query.equals("null")) {
                sb.append('?').append(query);
            }
            return sb.toString();
        }));

        environment.define("getParam", new NativeFunction(2, "urlStr, key", args -> {
            String urlStr = String.valueOf(args.get(0));
            String key = String.valueOf(args.get(1));
            try {
                String query = URI.create(urlStr).getQuery();
                if (query == null) {
                    return NullValue.INSTANCE;
                }
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0 && pair.substring(0, eq).equals(key)) {
                        return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                    }
                }
                return NullValue.INSTANCE;
            } catch (Exception e) {
                return NullValue.INSTANCE;
            }
        }));

        environment.define("getParams", new NativeFunction(1, "urlStr", args -> {
            try {
                String query = URI.create(String.valueOf(args.get(0))).getQuery();
                LinkedHashMap<String, com.mira.parser.nodes.expression.Expression> map = new LinkedHashMap<>();
                if (query != null) {
                    for (String pair : query.split("&")) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            String k = pair.substring(0, eq);
                            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                            map.put(k, wrap(v));
                        }
                    }
                }
                return new MapExpression(map);
            } catch (Exception e) {
                return new MapExpression(new LinkedHashMap<>());
            }
        }));

        environment.define("encode", new NativeFunction(1, "str", args
                -> URLEncoder.encode(String.valueOf(args.get(0)), StandardCharsets.UTF_8)));

        environment.define("decode", new NativeFunction(1, "str", args
                -> URLDecoder.decode(String.valueOf(args.get(0)), StandardCharsets.UTF_8)));

        environment.define("isValid", new NativeFunction(1, "str", args -> {
            try {
                URI.create(String.valueOf(args.get(0)));
                return true;
            } catch (Exception e) {
                return false;
            }
        }));
    }
}
