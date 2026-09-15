package com.mira.resolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;

public class EnumTest {

    private void assertClean(String source) {
        List<Node> ast = new Parser().parseTokens(new Tokenizer().tokenize("module Main; " + source, false));
        assertDoesNotThrow(() -> new StaticCheck().check(ast));
    }

    @Test
    void enumFieldAccessIsClean() {
        assertClean("enum direction { NORTH, EAST, SOUTH, WEST } var dir : direction.EAST;");
    }

    @Test
    void enumInSwitchIsClean() {
        assertClean(
                "enum direction { NORTH, EAST, SOUTH, WEST } var dir : direction.EAST; var label : switch(dir) { case direction.NORTH -> \"N\" case direction.EAST -> \"E\" default -> \"?\" };");
    }

    @Test
    void enumPassedToFunctionWithKnownStructArgIsClean() {
        // The key scenario: enum field access exists alongside struct-param-checking
        // function call
        assertClean(
                "enum direction { NORTH, EAST } " + "var point : struct { var x; var y; }; " + "var origin : point{}; "
                + "fn hello(name) { println(name.x); } " + "hello(origin); " + "var dir : direction.EAST;");
    }

    @Test
    void enumAccessInsideFunctionIsClean() {
        assertClean("enum direction { NORTH, EAST, SOUTH, WEST } "
                + "fn main() { var dir : direction.EAST; println(dir); }");
    }

    @Test
    void enumInSwitchInsideFunctionIsClean() {
        assertClean("enum direction { NORTH, EAST, SOUTH, WEST } " + "fn main() { " + "  var dir : direction.EAST; "
                + "  var label : switch(dir) { " + "    case direction.NORTH -> \"N\" "
                + "    case direction.EAST -> \"E\" " + "    default -> \"?\" " + "  }; " + "  println(label); " + "}");
    }
}
