package com.mira.build;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class TomlParserTest {

    @Test
    void simpleString() {
        assertEquals("hello", TomlParser.parseString("\"hello\"", 1));
    }

    @Test
    void stringWithEscapes() {
        assertEquals("a\"b\\c\nd", TomlParser.parseString("\"a\\\"b\\\\c\\nd\"", 1));
    }

    @Test
    void unclosedStringThrows() {
        assertThrows(BuildException.class, () -> TomlParser.parseString("\"unclosed", 1));
    }

    @Test
    void emptyArray() {
        assertEquals(List.of(), TomlParser.parseArray("[]", 1));
    }

    @Test
    void stringArray() {
        assertEquals(List.of("a", "b", "c"), TomlParser.parseArray("[\"a\", \"b\", \"c\"]", 1));
    }

    @Test
    void arrayWithEscapedQuote() {
        assertEquals(List.of("say \"hi\""), TomlParser.parseArray("[\"say \\\"hi\\\"\"]", 1));
    }

    @Test
    void emptyInlineTable() {
        assertTrue(TomlParser.parseInlineTable("{}", 1).isEmpty());
    }

    @Test
    void inlineTableSingleEntry() {
        Map<String, Object> result = TomlParser.parseInlineTable("{ path = \"../lib\" }", 1);
        assertEquals("../lib", result.get("path"));
    }

    @Test
    void inlineTableMultipleEntries() {
        Map<String, Object> result = TomlParser.parseInlineTable("{ a = \"x\", b = \"y\" }", 1);
        assertEquals("x", result.get("a"));
        assertEquals("y", result.get("b"));
    }

    @Test
    void unclosedInlineTableThrows() {
        assertThrows(BuildException.class, () -> TomlParser.parseInlineTable("{ path = \"x\"", 1));
    }

    @Test
    void booleanTrue() {
        assertEquals(Boolean.TRUE, TomlParser.parseValue("true", 1));
    }

    @Test
    void booleanFalse() {
        assertEquals(Boolean.FALSE, TomlParser.parseValue("false", 1));
    }

    @Test
    void longValue() {
        assertEquals(42L, TomlParser.parseValue("42", 1));
    }

    @Test
    void unknownBareValueThrows() {
        assertThrows(BuildException.class, () -> TomlParser.parseValue("notAValue", 1));
    }

    @Test
    void fullDocument() {
        String toml = """
                [project]
                name    = "my-app"
                version = "1.0.0"
                entry   = "src/main.mira"
                authors = ["Alice", "Bob"]

                [build]
                mode = "compile"
                main = true
                lint = false
                output = "dist"
                args = []

                [test]
                pattern = "tests/**/*_test.mira"
                extra   = ["extra_test.mira"]

                [dependencies]
                utils = { path = "../utils" }
                """;

        Map<String, Object> doc = TomlParser.parse(toml);

        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) doc.get("project");
        assertEquals("my-app", project.get("name"));
        assertEquals("1.0.0", project.get("version"));
        assertEquals("src/main.mira", project.get("entry"));
        assertEquals(List.of("Alice", "Bob"), project.get("authors"));

        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) doc.get("build");
        assertEquals("compile", build.get("mode"));
        assertEquals(Boolean.TRUE, build.get("main"));
        assertEquals(Boolean.FALSE, build.get("lint"));
        assertEquals("dist", build.get("output"));
        assertEquals(List.of(), build.get("args"));

        @SuppressWarnings("unchecked")
        Map<String, Object> test = (Map<String, Object>) doc.get("test");
        assertEquals("tests/**/*_test.mira", test.get("pattern"));
        assertEquals(List.of("extra_test.mira"), test.get("extra"));

        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) doc.get("dependencies");
        @SuppressWarnings("unchecked")
        Map<String, Object> utils = (Map<String, Object>) deps.get("utils");
        assertEquals("../utils", utils.get("path"));
    }

    @Test
    void commentsIgnored() {
        String toml = """
                # This is a comment
                [project]
                name = "app" # inline comment
                """;
        Map<String, Object> doc = TomlParser.parse(toml);
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) doc.get("project");
        assertEquals("app", project.get("name"));
    }

    @Test
    void emptyDocumentParsesToEmptyMap() {
        Map<String, Object> doc = TomlParser.parse("");
        assertTrue(doc.isEmpty());
    }

    @Test
    void missingEqualsThrows() {
        assertThrows(BuildException.class, () -> TomlParser.parse("[project]\nbadline\n"));
    }
}
