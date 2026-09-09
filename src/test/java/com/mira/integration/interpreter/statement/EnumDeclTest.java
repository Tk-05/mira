package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.runtime.interpreter.Environment;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractEnumDeclTests;

public class EnumDeclTest extends AbstractEnumDeclTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void autoIndexedVariantsStartAtZero() {
        backend.runAndGetValue("enum Dir { NORTH, SOUTH, EAST, WEST }");
        Environment dir = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Dir");
        assertEquals(0.0, InterpreterRunner.normNum(dir.get("NORTH")));
        assertEquals(1.0, InterpreterRunner.normNum(dir.get("SOUTH")));
        assertEquals(2.0, InterpreterRunner.normNum(dir.get("EAST")));
        assertEquals(3.0, InterpreterRunner.normNum(dir.get("WEST")));
    }

    @Test
    void explicitIntegerValues() {
        backend.runAndGetValue("enum Status { OK : 200, NOT_FOUND : 404, ERROR : 500 }");
        Environment status = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Status");
        assertEquals(200.0, InterpreterRunner.normNum(status.get("OK")));
        assertEquals(404.0, InterpreterRunner.normNum(status.get("NOT_FOUND")));
        assertEquals(500.0, InterpreterRunner.normNum(status.get("ERROR")));
    }

    @Test
    void explicitStringValues() {
        backend.runAndGetValue("enum Color { RED : \"red\", GREEN : \"green\", BLUE : \"blue\" }");
        Environment color = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Color");
        assertEquals("red", color.get("RED"));
        assertEquals("green", color.get("GREEN"));
        assertEquals("blue", color.get("BLUE"));
    }

    @Test
    void singleVariant() {
        backend.runAndGetValue("enum Single { ONLY }");
        Environment single = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Single");
        assertEquals(0.0, InterpreterRunner.normNum(single.get("ONLY")));
    }

    @Test
    void explicitExpressionValueIsFullyEvaluated() {
        backend.runAndGetValue("enum Calc { A : 1 + 2 }");
        Environment calc = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Calc");
        assertEquals(3.0, InterpreterRunner.normNum(calc.get("A")));
    }

    @Test
    void enumVariantIsImmutable() {
        backend.runAndGetValue("enum Dir { NORTH }");
        Environment dir = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Dir");
        assertThrows(ReferenceIsImmutableError.class, () -> dir.assign("NORTH", 99.0));
    }

    @Test
    void enumItselfIsImmutable() {
        assertThrows(ObjectAlreadyDefinedInScope.class, () -> backend.runAndGetValue("""
                enum Dir { NORTH }
                enum Dir { SOUTH }
                """));
    }

    @Test
    void unknownVariantThrows() {
        backend.runAndGetValue("enum Dir { NORTH }");
        Environment dir = (Environment) backend.getInterpreter().getGlobalEnvironment().get("Dir");
        assertThrows(UndefinedReferenceError.class, () -> dir.get("SOUTH"));
    }
}
