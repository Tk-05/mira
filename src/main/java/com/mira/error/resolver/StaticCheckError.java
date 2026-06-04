package com.mira.error.resolver;

import com.mira.error.MiraError;

public class StaticCheckError extends MiraError {

    protected StaticCheckError(String errorCode, String message, int line, int column, int span, String hint) {
        super(errorCode, message, line, column, span, hint);
    }

    public static class UndeclaredVariableError extends StaticCheckError {

        public UndeclaredVariableError(String name, int line, int column) {
            super("E301",
                    "Variable '$" + name + "' is used but never declared",
                    line, column, name.length(),
                    "Declare the variable with 'var " + name + "' or 'const " + name + "'");
        }
    }

    public static class UndefinedFunctionError extends StaticCheckError {

        public UndefinedFunctionError(String name, int line, int column) {
            super("E302",
                    "Function '" + name + "' is called but never defined",
                    line, column, name.length(),
                    "Define the function with 'fn " + name + "(...) { ... }' or import it");
        }
    }

    public static class UnknownNamespaceError extends StaticCheckError {

        public UnknownNamespaceError(String name, int line, int column) {
            super("E303",
                    "Namespace '" + name + "' is used but never imported",
                    line, column, name.length(),
                    "Import the module with 'import \"module\" as " + name + "'");
        }
    }

    public static class ConstReassignmentError extends StaticCheckError {

        public ConstReassignmentError(String name, int line, int column) {
            super("E304",
                    "Cannot reassign constant '" + name + "'",
                    line, column, name.length(),
                    "Declare with 'var' instead of 'const' if the value needs to change");
        }
    }

    public static class BreakOutsideLoopError extends StaticCheckError {

        public BreakOutsideLoopError(int line) {
            super("E305", "'break' used outside of a loop", line, 0, 5, null);
        }
    }

    public static class ContinueOutsideLoopError extends StaticCheckError {

        public ContinueOutsideLoopError(int line) {
            super("E305", "'continue' used outside of a loop", line, 0, 8, null);
        }
    }

    public static class DuplicateDeclarationError extends StaticCheckError {

        public DuplicateDeclarationError(String name, int line, int column) {
            super("E306",
                    "'" + name + "' is already declared in this scope",
                    line, column, name.length(),
                    "Rename the variable or remove the duplicate declaration");
        }
    }

    public static class ArityMismatchError extends StaticCheckError {

        public ArityMismatchError(String name, int expected, int actual, int line, int column) {
            super("E307",
                    "'" + name + "' expects " + expected + " argument(s) but was called with " + actual,
                    line, column, name.length(), null);
        }

        public ArityMismatchError(String name, int min, int max, int actual, int line, int column) {
            super("E307",
                    "'" + name + "' expects " + min + " to " + max + " argument(s) but was called with " + actual,
                    line, column, name.length(), null);
        }
    }
}
