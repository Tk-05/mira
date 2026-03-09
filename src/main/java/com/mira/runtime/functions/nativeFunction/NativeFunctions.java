package com.mira.runtime.functions.nativeFunction;

import java.util.List;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.runtime.Environment;
import com.mira.runtime.Evaluator;
import com.mira.runtime.Interpreter;

public class NativeFunctions {

    public static Environment defineNativeFunctions(Environment environment) {
        environment.define("print",
                new NativeFunction(1, args -> {
                    System.out.println(args.get(0));
                    return null;
                }));

        environment.define("eval",
                new NativeFunction(1, args -> {
                    String code = String.valueOf(args.get(0));
                    try {
                        return Evaluator.evaluate(code);

                    } catch (Exception e) {

                        Tokenizer tokenizer = new Tokenizer();
                        Parser parser;
                        parser = new Parser();
                        List<Node> ast = parser.parseTokens(tokenizer.tokenize(code));
                        return Interpreter.getInstance().run(ast);
                    }
                }));
        environment.define("pow",
                new NativeFunction(2, args -> {
                    return Math.pow(Evaluator.evaluate(String.valueOf(args.get(0))), Evaluator.evaluate(String.valueOf(args.get(1))));
                }));
        return environment;
    }
}
