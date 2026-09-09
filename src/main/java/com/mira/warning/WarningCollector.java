package com.mira.warning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mira.cli.Flags;
import com.mira.lexer.token.Token;

public final class WarningCollector {

    private static final ThreadLocal<List<Warning>> warnings = ThreadLocal.withInitial(ArrayList::new);

    private WarningCollector() {
    }

    public static void emit(Warning warning) {
        warnings.get().add(warning);
    }

    public static void emit(WarningLevel level, String message) {
        warnings.get().add(new Warning(level, message));
    }

    public static void emit(WarningLevel level, String message, Token token) {
        warnings.get().add(new Warning(level, message, token));
    }

    public static void emit(WarningLevel level, String message, int line, int column) {
        warnings.get().add(new Warning(level, message, line, column));
    }

    public static void emit(WarningLevel level, String message, int line, int column, int span) {
        warnings.get().add(new Warning(level, message, line, column, span));
    }

    public static void emit(WarningLevel level, String message, int line, int column, int span, int endLine) {
        warnings.get().add(new Warning(level, message, line, column, span, endLine));
    }

    public static List<Warning> getWarnings() {
        return Collections.unmodifiableList(warnings.get());
    }

    public static boolean hasWarnings() {
        return !warnings.get().isEmpty();
    }

    public static void flush() {
        if (!Flags.suppressWarnings) {
            for (Warning w : warnings.get()) {
                System.err.println(w.format());
            }
        }
        warnings.get().clear();
    }

    public static void clear() {
        warnings.get().clear();
    }
}
