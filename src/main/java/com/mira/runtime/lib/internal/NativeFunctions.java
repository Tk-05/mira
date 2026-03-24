package com.mira.runtime.lib.internal;

import java.util.List;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.Interpreter;

public class NativeFunctions {

    public static Environment defineNativeFunctions(Environment environment) {
        environment.define("print",
                new NativeFunction(1, args -> {
                    Object value = args.get(0);
                    System.out.print(value);
                    return null;
                })
        );

        environment.define("eval",
                new NativeFunction(1, args -> {
                    String eval = String.valueOf(args.get(0));
                    return Evaluator.evaluate(eval);
                }));

        environment.define("exec",
                new NativeFunction(1, args -> {
                    String code = String.valueOf(args.get(0));
                    Tokenizer tokenizer = new Tokenizer();
                    Parser parser = new Parser();
                    List<Node> ast = parser.parseTokens(tokenizer.tokenize(code));
                    return Interpreter.getInstance().run(ast);
                }));

        environment.define("length",
                new NativeFunction(1, args -> {
                    Object arg = args.get(0);

                    if (arg instanceof TupleExpression tuple) {
                        return String.valueOf(tuple.getMembers().size());
                    }

                    throw new RuntimeException("Option has not been implemented yet!");
                })
        );

        environment.define("incr",
                new NativeFunction(1, args -> {
                    String eval = String.valueOf(args.get(0));
                    return Evaluator.evaluate(eval + "+ 1");
                })
        );

        environment.define("decr",
                new NativeFunction(1, args -> {
                    String eval = String.valueOf(args.get(0));
                    return Evaluator.evaluate(eval + "- 1");
                })
        );

        environment.define("pow",
                new NativeFunction(2, args -> {
                    return Math.pow((double) Evaluator.evaluate(String.valueOf(args.get(0))), (double) Evaluator.evaluate(String.valueOf(args.get(1))));
                }));
        return environment;
    }
}
