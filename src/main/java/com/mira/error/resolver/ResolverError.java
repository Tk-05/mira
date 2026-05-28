package com.mira.error.resolver;

import com.mira.error.MiraError;

public class ResolverError extends MiraError {

    protected ResolverError(String errorCode, String message, int line, int column, int span, String hint) {
        super(errorCode, message, line, column, span, hint);
    }

    public static class UndeclaredVariableError extends ResolverError {

        public UndeclaredVariableError(String name, int line, int column) {
            super("E301",
                    "Variable '$" + name + "' is used but never declared",
                    line, column, name.length(),
                    "Declare the variable with 'var " + name + "' or 'const " + name + "'");
        }
    }

    public static class UndefinedFunctionError extends ResolverError {

        public UndefinedFunctionError(String name, int line, int column) {
            super("E302",
                    "Function '" + name + "' is called but never defined",
                    line, column, name.length(),
                    "Define the function with 'fn " + name + "(...) { ... }' or import it");
        }
    }

    public static class UnknownNamespaceError extends ResolverError {

        public UnknownNamespaceError(String name, int line, int column) {
            super("E303",
                    "Namespace '" + name + "' is used but never imported",
                    line, column, name.length(),
                    "Import the module with 'import \"module\" as " + name + "'");
        }
    }
}
