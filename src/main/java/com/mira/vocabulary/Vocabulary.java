package com.mira.vocabulary;

import java.util.ArrayList;
import java.util.List;

public class Vocabulary {
    public static final List<String> keywords = new ArrayList<>(java.util.Arrays.asList(
        "print",
        "var",
        "eval",
        "ret",
        "fn"
    ));

    public static final List<String> delimiters = new ArrayList<>(java.util.Arrays.asList(
        "(",
        ")",
        "{",
        "}",
        ":",
        ";",
        ","
    ));

    public static boolean stringIsKeyword(String string) {
        for (String keyword : keywords) {
            if (keyword.equals(string)) {
                return true;
            }
        }
        return false;
    }

    public static boolean stringIsDelimiter(String string) {
        for (String delimiter : delimiters) {
            if (delimiter.equals(string)) {
                return true;
            }
        }
        return false;
    }
}
