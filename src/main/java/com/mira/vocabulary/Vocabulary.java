package com.mira.vocabulary;

import java.util.Set;

public class Vocabulary {

    public static final int MAX_OPERATOR_LENGTH = 3;

    private static final Set<String> keywords = Set.of(
            "var",
            "ret",
            "fn",
            "if",
            "else",
            "for",
            "while",
            "break",
            "import",
            "overwrite",
            "foreach",
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
            "throw"
    );

    public static final Set<String> operations = Set.of(
            "+", "-", "*", "/", "%",
            "++", "--",
            "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=",
            "==", "!=", "<", ">", "<=", ">=",
            "&&", "||",
            "&", "|", "^", "~",
            "<<", ">>",
            "$", ":", "!", "?"
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
        return keywords.contains(s) || s.startsWith("$");
    }

    public static boolean stringIsDelimiter(String string) {
        return delimiters.contains(string);
    }

    public static boolean stringIsOperation(String string) {
        return operations.contains(string);
    }
}
