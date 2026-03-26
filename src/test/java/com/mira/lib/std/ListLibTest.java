package com.mira.lib.std;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class ListLibTest {

    static com.mira.lib.std.List list = new com.mira.lib.std.List();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        list.loadLib(environment);
    }

    @Test
    void testAppend() {
        if (environment.get("append") instanceof NativeFunction activeFunction) {
            ListExpression list = new ListExpression(new ArrayList<>());
            ListExpression appendedList = (ListExpression) activeFunction.call(interpreter, java.util.List.of("Test", list));
            assertEquals(1, appendedList.getLength());
        }
    }
}
