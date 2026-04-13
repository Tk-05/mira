package com.mira.warning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mira.lexer.token.Token;

public final class WarningCollector {

    private static final List<Warning> warnings = new ArrayList<>();

    private WarningCollector() {
    }

    public static void emit(Warning warning) {
        warnings.add(warning);
    }

    public static void emit(WarningLevel level, String message) {
        warnings.add(new Warning(level, message));
    }

    public static void emit(WarningLevel level, String message, Token token) {
        warnings.add(new Warning(level, message, token));
    }

    public static void emit(WarningLevel level, String message, int line, int column) {
        warnings.add(new Warning(level, message, line, column));
    }

    public static List<Warning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public static boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public static void flush() {
        for (Warning w : warnings) {
            System.err.println(w.format());
        }
        warnings.clear();
    }

    public static void clear() {
        warnings.clear();
    }
}
