package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class EnumDeclTest extends InterpreterTestBase {

    @Test
    void autoIndexedVariantsStartAtZero() {
        run("enum Dir { NORTH, SOUTH, EAST, WEST };");
        Environment dir = (Environment) Interpreter.getGlobalEnvironment().get("Dir");
        assertEquals("0", dir.get("NORTH"));
        assertEquals("1", dir.get("SOUTH"));
        assertEquals("2", dir.get("EAST"));
        assertEquals("3", dir.get("WEST"));
    }

    @Test
    void explicitIntegerValues() {
        run("enum Status { OK : 200, NOT_FOUND : 404, ERROR : 500 };");
        Environment status = (Environment) Interpreter.getGlobalEnvironment().get("Status");
        assertEquals("200", status.get("OK"));
        assertEquals("404", status.get("NOT_FOUND"));
        assertEquals("500", status.get("ERROR"));
    }

    @Test
    void explicitStringValues() {
        run("enum Color { RED : \"red\", GREEN : \"green\", BLUE : \"blue\" };");
        Environment color = (Environment) Interpreter.getGlobalEnvironment().get("Color");
        assertEquals("red", color.get("RED"));
        assertEquals("green", color.get("GREEN"));
        assertEquals("blue", color.get("BLUE"));
    }

    @Test
    void singleVariant() {
        run("enum Single { ONLY };");
        Environment single = (Environment) Interpreter.getGlobalEnvironment().get("Single");
        assertEquals("0", single.get("ONLY"));
    }

    @Test
    void enumAccessedViaFieldAccess() {
        run("""
                enum Dir { NORTH, SOUTH };
                var x : Dir.SOUTH;
                """);
        assertEquals("1", Interpreter.getGlobalEnvironment().get("x"));
    }

    @Test
    void enumValueUsedInComparison() {
        run("""
                enum Dir { NORTH, SOUTH };
                var current : Dir.NORTH;
                var isNorth : false;
                if ($current == Dir.NORTH) {
                    $isNorth : true;
                }
                """);
        assertEquals(Boolean.TRUE, Interpreter.getGlobalEnvironment().get("isNorth"));
    }

    @Test
    void enumVariantIsImmutable() {
        run("enum Dir { NORTH };");
        Environment dir = (Environment) Interpreter.getGlobalEnvironment().get("Dir");
        assertThrows(ReferenceIsImmutableError.class, () -> dir.assign("NORTH", 99.0));
    }

    @Test
    void enumItselfIsImmutable() {
        assertThrows(ObjectAlreadyDefinedInScope.class, () -> run("""
                enum Dir { NORTH };
                enum Dir { SOUTH };
                """));
    }

    @Test
    void unknownVariantThrows() {
        run("enum Dir { NORTH };");
        Environment dir = (Environment) Interpreter.getGlobalEnvironment().get("Dir");
        assertThrows(UndefinedReferenceError.class, () -> dir.get("SOUTH"));
    }

    @Test
    void enumValueUsedInSwitch() {
        run("""
                enum Status { OK : 200, ERROR : 500 };
                var code : Status.OK;
                var result : 0;
                switch ($code) {
                    case (200) { $result : 1; }
                    case (500) { $result : 2; }
                }
                """);
        assertEquals("1", Interpreter.getGlobalEnvironment().get("result"));
    }
}
