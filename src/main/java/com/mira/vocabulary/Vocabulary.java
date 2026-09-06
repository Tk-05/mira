package com.mira.vocabulary;

import java.util.Map;
import java.util.Set;

public class Vocabulary {

    public static final int MAX_OPERATOR_LENGTH = 3;

    private static final Set<String> keywords = Set.of(
            "var",
            "return",
            "fn",
            "if",
            "else",
            "for",
            "while",
            "break",
            "import",
            "in",
            "module",
            "as",
            "const",
            "true",
            "false",
            "continue",
            "null",
            "switch",
            "case",
            "default",
            "enum",
            "try",
            "catch",
            "finally",
            "throw",
            "native",
            "do",
            "await",
            "async",
            "typeof",
            "lock",
            "pure",
            "comptime",
            "test",
            "static_assert",
            "pub",
            "struct"
    );

    public static final Set<String> COMPARISON_OPERATORS = Set.of("==", "!=", "<", ">", "<=", ">=");

    public static final Set<String> LOGICAL_OPERATORS = Set.of("&&", "||");

    public static final Set<String> ARITHMETIC_OPERATORS = Set.of("+", "-", "*", "/", "%", "**", "\\%");

    public static final Set<String> BITWISE_OPERATORS = Set.of("&", "|", "^", "~", "<<", ">>");

    public static final Set<String> COMPOUND_ASSIGNMENT_OPERATORS = Set.of(
            "+:", "-:", "*:", "/:", "%:", "**:", "\\%:", "&:", "|:", "^:"
    );

    public static final Set<String> UNARY_OPERATORS = Set.of("++", "--", "!", "~");

    public static final Set<String> SPECIAL_OPERATORS = Set.of("|>", "??", "?.", ":", "?");

    public static final Map<String, Integer> OPERATOR_PRECEDENCE = Map.ofEntries(
            Map.entry("|>", 1), Map.entry("||", 1), Map.entry("??", 1),
            Map.entry("&&", 2),
            Map.entry("|", 3),
            Map.entry("^", 4),
            Map.entry("&", 5),
            Map.entry("==", 6), Map.entry("!=", 6),
            Map.entry("<", 7), Map.entry(">", 7), Map.entry("<=", 7), Map.entry(">=", 7),
            Map.entry("<<", 8), Map.entry(">>", 8),
            Map.entry("+", 9), Map.entry("-", 9),
            Map.entry("*", 10), Map.entry("/", 10), Map.entry("%", 10), Map.entry("\\%", 10),
            Map.entry("**", 11)
    );

    public static final Set<String> OPERATORS = Set.of(
            "+", "-", "*", "/", "%", "**", "\\%",
            "++", "--",
            "+:", "-:", "*:", "/:", "%:", "**:", "\\%:",
            "&:", "|:", "^:",
            "==", "!=", "<", ">", "<=", ">=",
            "&&", "||",
            "&", "|", "^", "~",
            "<<", ">>",
            "|>",
            "??", "?.",
            ":", "!", "?",
            "->"
    );

    public static final Set<String> delimiters = Set.of(
            "(",
            ")",
            "{",
            "}",
            ";",
            ",",
            "[",
            "]",
            ".",
            "..",
            "..."
    );

    public static boolean stringIsKeyword(String s) {
        return keywords.contains(s);
    }

    public static boolean stringIsDelimiter(String string) {
        return delimiters.contains(string);
    }

    public static boolean stringIsOperation(String string) {
        return OPERATORS.contains(string);
    }
}
