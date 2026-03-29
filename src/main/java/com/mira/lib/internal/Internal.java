package com.mira.lib.internal;

import java.util.List;

import com.mira.lexer.Tokenizer;
import com.mira.lib.Lib;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.Interpreter;

public class Internal implements Lib {

    @Override
    public void loadLib(Environment environment) {
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
                    return Evaluator.evaluate(eval, false);
                }));

        environment.define("exec",
                new NativeFunction(1, args -> {
                    String code = String.valueOf(args.get(0));
                    Tokenizer tokenizer = new Tokenizer();
                    Parser parser = new Parser();
                    List<Node> ast = parser.parseTokens(tokenizer.tokenize(code, true));
                    return Interpreter.getInstance().runWithoutLoadingNewContext(ast);
                }));

        environment.define("length",
                new NativeFunction(1, (var args) -> {
                    Object arg = args.get(0);

                    switch (arg) {
                        case TupleExpression tuple -> {
                            return String.valueOf(tuple.getMembers().size());
                        }
                        case String string -> {
                            return string.length();
                        }
                        default -> {
                            throw new RuntimeException("Option has not been implemented yet!");
                        }
                    }
                })
        );

        environment.define("exit", new NativeFunction(1, args -> {
            int code = (int) Double.parseDouble(String.valueOf(args.get(0)));
            System.exit(code);
            return null;
        }));
    }
}
