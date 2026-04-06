package com.mira.error;

public abstract class MiraError extends RuntimeException {

    private final String errorCode;
    private final int line;
    private final int column;
    private final String hint;

    protected MiraError(String errorCode, String message, int line, int column, String hint) {
        super(message);
        this.errorCode = errorCode;
        this.line = line;
        this.column = column;
        this.hint = hint;
    }

    protected MiraError(String errorCode, String message, int line, int column) {
        this(errorCode, message, line, column, null);
    }

    protected MiraError(String errorCode, String message) {
        this(errorCode, message, -1, -1, null);
    }

    protected MiraError(String errorCode, String message, String hint) {
        this(errorCode, message, -1, -1, hint);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getHint() {
        return hint;
    }
}
