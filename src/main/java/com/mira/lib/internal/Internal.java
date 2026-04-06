package com.mira.lib.internal;

import java.util.List;

import com.mira.error.runtime.RuntimeError.ArgMismatchError;
import com.mira.error.runtime.RuntimeError.AssertionFailedError;
import com.mira.error.runtime.RuntimeError.InvalidArgumentError;
import com.mira.lexer.Tokenizer;
import com.mira.lib.Lib;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.NullValue;

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
                        case ListExpression list -> {
                            return list.getLength();
                        }
                        case NullValue value -> {
                            throw new InvalidArgumentError("length", "argument must not be null");
                        }
                        default -> {
                            throw new InvalidArgumentError("length",
                                    "unsupported type '" + arg.getClass().getSimpleName() + "' — expected a string, list, or tuple");
                        }
                    }
                })
        );

        environment.define("exit", new NativeFunction(1, args -> {
            int code = (int) Double.parseDouble(String.valueOf(args.get(0)));
            System.exit(code);
            return null;
        }));

        environment.define("assert", new NativeFunction(-1, args -> {
            if (args.isEmpty() || args.size() > 2) {
                throw new ArgMismatchError("assert", 1, args.size());
            }
            Object condition = args.get(0);
            boolean result = switch (condition) {
                case Boolean b ->
                    b;
                case NullValue n ->
                    false;
                case String s -> {
                    if (s.equalsIgnoreCase("true")) {
                        yield true;
                    }
                    if (s.equalsIgnoreCase("false")) {
                        yield false;
                    }
                    try {
                        yield Double.parseDouble(s) != 0;
                    } catch (NumberFormatException e) {
                        yield !s.isEmpty();
                    }
                }
                default ->
                    true;
            };
            if (!result) {
                if (args.size() == 2) {
                    throw new AssertionFailedError(String.valueOf(args.get(1)));
                }
                throw new AssertionFailedError();
            }
            return null;
        }));
    }
}
