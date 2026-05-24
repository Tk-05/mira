package com.mira.lib.std;

import java.util.ArrayList;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Strings implements Lib {

    @Override
    public void loadLib(Environment environment) {
        environment.define("charAt",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    int index = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return str.charAt(index);
                }));

        environment.define("indexOf",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    char ch = String.valueOf(args.get(1)).charAt(0);
                    return str.indexOf(ch);
                }));

        environment.define("trim",
                new NativeFunction(1, args -> {
                    return String.valueOf(args.get(0)).trim();
                }));

        environment.define("split",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    String delimiter = String.valueOf(args.get(1));
                    String[] parts = str.split(delimiter);
                    List<Expression> members = new ArrayList<>();
                    for (String part : parts) {
                        members.add(new DumbExpression(new Token(TokenType.EXPRESSION, part, 0, 0)));
                    }
                    return new ListExpression(members);
                }));

        environment.define("substr",
                new NativeFunction(3, args -> {
                    String str = String.valueOf(args.get(0));
                    int start = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    int end = (int) Double.parseDouble(String.valueOf(args.get(2)));
                    return str.substring(start, end);
                }));

        environment.define("strEqual",
                new NativeFunction(2, args -> {
                    String string1 = String.valueOf(args.get(0));
                    String string2 = String.valueOf(args.get(1));
                    return string1.equals(string2);
                }));

        environment.define("replace",
                new NativeFunction(3, args -> {
                    String string = String.valueOf(args.get(0));
                    char ch1 = String.valueOf(args.get(1)).charAt(0);
                    char ch2 = String.valueOf(args.get(2)).charAt(0);
                    return string.replace(ch1, ch2);
                }));

        environment.define("upper",
                new NativeFunction(1, args -> String.valueOf(args.get(0)).toUpperCase()));

        environment.define("lower",
                new NativeFunction(1, args -> String.valueOf(args.get(0)).toLowerCase()));

        environment.define("startsWith",
                new NativeFunction(2, args -> String.valueOf(args.get(0)).startsWith(String.valueOf(args.get(1)))));

        environment.define("endsWith",
                new NativeFunction(2, args -> String.valueOf(args.get(0)).endsWith(String.valueOf(args.get(1)))));

        environment.define("contains",
                new NativeFunction(2, args -> String.valueOf(args.get(0)).contains(String.valueOf(args.get(1)))));

        environment.define("repeat",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    int n = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return str.repeat(n);
                }));

        environment.define("toNumber",
                new NativeFunction(1, args -> Double.parseDouble(String.valueOf(args.get(0)))));

        environment.define("padLeft",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    int width = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return String.format("%" + width + "s", str);
                }));

        environment.define("padRight",
                new NativeFunction(2, args -> {
                    String str = String.valueOf(args.get(0));
                    int width = (int) Double.parseDouble(String.valueOf(args.get(1)));
                    return String.format("%-" + width + "s", str);
                }));

        environment.define("isNumeric",
                new NativeFunction(1, args -> {
                    try {
                        Double.valueOf(String.valueOf(args.get(0)));
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }));
    }
}
