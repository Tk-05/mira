package com.mira.runtime.lib.std;

import com.mira.lexer.token.Token;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.lib.Lib;

public class List implements Lib {

    @Override
    public void loadLib(Environment environment) {
        environment.define("append",
                new NativeFunction(2, args -> {
                    Expression toAppend;
                    if (args.get(0) instanceof String string) {
                        toAppend = new DumbExpression(new Token(null, string, 0, 0));
                    } else {
                        toAppend = (Expression) args.get(0);
                    }

                    if (args.get(1) instanceof ListExpression listExpression) {
                        listExpression.getMembers().add(toAppend);
                        return listExpression;
                    } else {
                        throw new RuntimeException("Provided argument is not of type list!");
                    }
                }));
    }
}
