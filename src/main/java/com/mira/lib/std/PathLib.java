package com.mira.lib.std;

import java.nio.file.Path;
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

public class PathLib implements Lib {

    private static String str(Object arg) {
        return String.valueOf(arg);
    }

    private static Expression wrapStr(String s) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, s, 0, 0));
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("join", new NativeFunction(-1, "...parts", args -> {
            if (args.isEmpty()) {
                return "";
            }
            Path result = Path.of(str(args.get(0)));
            for (int i = 1; i < args.size(); i++) {
                result = result.resolve(str(args.get(i)));
            }
            return result.toString();
        }));

        environment.define("normalize",
                new NativeFunction(1, "path", args -> Path.of(str(args.get(0))).normalize().toString()));

        environment.define("resolve", new NativeFunction(2, "base, rel",
                args -> Path.of(str(args.get(0))).resolve(str(args.get(1))).toString()));

        environment.define("relative", new NativeFunction(2, "from, to",
                args -> Path.of(str(args.get(0))).relativize(Path.of(str(args.get(1)))).toString()));

        environment.define("absolute",
                new NativeFunction(1, "path", args -> Path.of(str(args.get(0))).toAbsolutePath().toString()));

        environment.define("parent", new NativeFunction(1, "path", args -> {
            Path parent = Path.of(str(args.get(0))).getParent();
            return parent != null ? parent.toString() : "";
        }));

        environment.define("fileName", new NativeFunction(1, "path", args -> {
            Path name = Path.of(str(args.get(0))).getFileName();
            return name != null ? name.toString() : "";
        }));

        environment.define("stem", new NativeFunction(1, "path", args -> {
            Path p = Path.of(str(args.get(0)));
            Path name = p.getFileName();
            if (name == null) {
                return "";
            }
            String s = name.toString();
            int dot = s.lastIndexOf('.');
            return dot > 0 ? s.substring(0, dot) : s;
        }));

        environment.define("extension", new NativeFunction(1, "path", args -> {
            Path p = Path.of(str(args.get(0)));
            Path name = p.getFileName();
            if (name == null) {
                return "";
            }
            String s = name.toString();
            int dot = s.lastIndexOf('.');
            return dot > 0 ? s.substring(dot + 1) : "";
        }));

        environment.define("isAbsolute", new NativeFunction(1, "path", args -> Path.of(str(args.get(0))).isAbsolute()));

        environment.define("split", new NativeFunction(1, "path", args -> {
            Path p = Path.of(str(args.get(0)));
            List<Expression> parts = new ArrayList<>();
            for (Path part : p) {
                parts.add(wrapStr(part.toString()));
            }
            return new ListExpression(parts);
        }));
    }
}
