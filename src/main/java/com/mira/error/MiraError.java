package com.mira.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MiraError extends RuntimeException {

    private final String errorCode;
    private final int line;
    private final int column;
    private final int span;
    private final String hint;
    private String sourceFile = null;
    private final List<String> importChain = new ArrayList<>();
    private int runtimeLine = -1;
    private int runtimeCol = -1;

    protected MiraError(String errorCode, String message, int line, int column, int span, String hint) {
        super(message);
        this.errorCode = errorCode;
        this.line = line;
        this.column = column;
        this.span = Math.max(1, span);
        this.hint = hint;
    }

    protected MiraError(String errorCode, String message, int line, int column, String hint) {
        this(errorCode, message, line, column, 1, hint);
    }

    protected MiraError(String errorCode, String message, int line, int column) {
        this(errorCode, message, line, column, 1, null);
    }

    protected MiraError(String errorCode, String message) {
        this(errorCode, message, -1, -1, 1, null);
    }

    protected MiraError(String errorCode, String message, String hint) {
        this(errorCode, message, -1, -1, 1, hint);
    }

    public MiraError withLocation(int line, int column) {
        this.runtimeLine = line;
        this.runtimeCol = column;
        return this;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getLine() {
        return runtimeLine >= 0 ? runtimeLine : line;
    }

    public int getColumn() {
        return runtimeCol >= 0 ? runtimeCol : column;
    }

    public int getSpan() {
        return span;
    }

    public String getHint() {
        return hint;
    }

    public MiraError withSourceFile(String file) {
        this.sourceFile = file;
        return this;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void addImportChain(String file) {
        importChain.add(file);
    }

    public List<String> getImportChain() {
        return Collections.unmodifiableList(importChain);
    }
}
