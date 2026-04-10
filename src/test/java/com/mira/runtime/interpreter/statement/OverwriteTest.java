package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class OverwriteTest extends InterpreterTestBase {

    @Test
    void overwriteExistingVariable() {
        Interpreter.getGlobalEnvironment().define("test", "Test");
        assertEquals("HelloWorld", runContinued("""
                overwrite(
                "
                    var test : Hello World;
                "
                );

                $test;
                """));
    }

    @Test
    void overwriteWithMultipleStatements() {
        assertEquals(3.0, runContinued("""
                overwrite(
                "
                    var a : 1;
                    var b : 2;
                    var result : eval($a + $b);
                "
                );

                eval($result);
                """));
    }

    @Test
    void overwriteWithFunctionDeclaration() {
        run("");
        assertEquals(42.0, runContinued("""
                overwrite(
                "
                    fn answer() { return 42; }
                "
                );

                eval(answer());
                """));
    }
}
