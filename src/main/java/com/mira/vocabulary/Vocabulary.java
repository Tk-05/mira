package com.mira.vocabulary;

import java.util.Set;

public class Vocabulary {

    private static final Set<String> keywords = Set.of(
            "var",
            "ret",
            "fn"
    );

    public static final Set<String> reservedFunctionNames = Set.of(
            "print",
            "eval"
    );

    public static final Set<String> operations = Set.of(
            "+",
            "-",
            "/",
            "*",
            ":",
            "$"
    );

    public static final Set<String> delimiters = Set.of(
            "(",
            ")",
            "{",
            "}",
            ";",
            ","
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
