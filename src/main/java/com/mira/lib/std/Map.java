package com.mira.lib.std;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Map implements Lib {

    private static MapExpression toMap(Object arg) {
        if (!(arg instanceof MapExpression map)) {
            throw new RuntimeException("Expected a map, got: " + arg.getClass().getSimpleName());
        }
        return map;
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("newMap", new NativeFunction(0, args -> {
            return new MapExpression(new LinkedHashMap<>());
        }));

        environment.define("mapSize", new NativeFunction(1, args -> {
            return (double) toMap(args.get(0)).getEntries().size();
        }));

        environment.define("mapHas", new NativeFunction(2, args -> {
            String key = String.valueOf(args.get(1));
            return toMap(args.get(0)).getEntries().containsKey(key);
        }));

        environment.define("mapRemove", new NativeFunction(2, args -> {
            MapExpression map = toMap(args.get(0));
            String key = String.valueOf(args.get(1));
            map.getEntries().remove(key);
            return map;
        }));

        environment.define("mapKeys", new NativeFunction(1, args -> {
            MapExpression map = toMap(args.get(0));
            java.util.List<Expression> keys = new ArrayList<>();
            for (String key : map.getEntries().keySet()) {
                keys.add(new DumbExpression(new Token(TokenType.EXPRESSION, key, 0, 0)));
            }
            return new ListExpression(keys);
        }));

        environment.define("mapValues", new NativeFunction(1, args -> {
            MapExpression map = toMap(args.get(0));
            java.util.List<Expression> values = new ArrayList<>(map.getEntries().values());
            return new ListExpression(values);
        }));
    }
}
