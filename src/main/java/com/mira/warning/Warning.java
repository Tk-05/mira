package com.mira.warning;

import com.mira.lexer.token.Token;

public record Warning(WarningLevel level, String message, int line, int column, int span) {

    private static final String ANSI_YELLOW = "[33m";
    private static final String ANSI_CYAN   = "[36m";
    private static final String ANSI_RESET  = "[0m";

    public Warning(WarningLevel level, String message) {
        this(level, message, -1, -1, 1);
    }

    public Warning(WarningLevel level, String message, int line, int column) {
        this(level, message, line, column, 1);
    }

    public Warning(WarningLevel level, String message, Token token) {
        this(level, message, token.getLine(), token.getColumn(), token.getLexeme().length());
    }

    public String format() {
        String color = level == WarningLevel.WARNING ? ANSI_YELLOW : ANSI_CYAN;
        String tag   = color + "[" + level + "]" + ANSI_RESET;
        if (line > 0) {
            return tag + " " + message + " (line " + line + ":" + column + ")";
        }
        return tag + " " + message;
    }
}
