package com.mira.lib.std;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.lib.Lib;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Regex implements Lib {

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("matches", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            return input.matches(pattern);
        }));

        environment.define("contains", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            return Pattern.compile(pattern).matcher(input).find();
        }));

        environment.define("findFirst", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            Matcher m = Pattern.compile(pattern).matcher(input);
            return m.find() ? m.group() : "";
        }));

        environment.define("findAll", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            Matcher m = Pattern.compile(pattern).matcher(input);
            List<com.mira.parser.nodes.expression.Expression> results = new ArrayList<>();
            while (m.find()) {
                results.add(wrap(m.group()));
            }
            return new ListExpression(results);
        }));

        environment.define("replaceAll", new NativeFunction(3, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            String replacement = String.valueOf(args.get(2));
            return input.replaceAll(pattern, replacement);
        }));

        environment.define("replaceFirst", new NativeFunction(3, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            String replacement = String.valueOf(args.get(2));
            return input.replaceFirst(pattern, replacement);
        }));

        environment.define("split", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            String[] parts = input.split(pattern);
            List<com.mira.parser.nodes.expression.Expression> results = new ArrayList<>();
            for (String part : parts) {
                results.add(wrap(part));
            }
            return new ListExpression(results);
        }));

        environment.define("capture", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            Matcher m = Pattern.compile(pattern).matcher(input);
            List<com.mira.parser.nodes.expression.Expression> groups = new ArrayList<>();
            if (m.find()) {
                for (int i = 1; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    groups.add(wrap(g != null ? g : ""));
                }
            }
            return new ListExpression(groups);
        }));

        environment.define("countMatches", new NativeFunction(2, args -> {
            String input = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            Matcher m = Pattern.compile(pattern).matcher(input);
            int count = 0;
            while (m.find()) {
                count++;
            }
            return (double) count;
        }));
    }
}
