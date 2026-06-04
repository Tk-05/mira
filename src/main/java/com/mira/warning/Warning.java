package com.mira.warning;

import com.mira.error.DiagnosticFormatter;
import com.mira.lexer.token.Token;

public record Warning(WarningLevel level, String message, int line, int column, int span) {

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
        return DiagnosticFormatter.formatWarning(this);
    }
}
