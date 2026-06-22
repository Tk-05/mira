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

        public BreakOutsideLoopError(int line, int column) {
            super("E305", "'break' used outside of a loop", line, column, 5, null);
        }
    }

    public static class ContinueOutsideLoopError extends StaticCheckError {

        public ContinueOutsideLoopError(int line, int column) {
            super("E305", "'continue' used outside of a loop", line, column, 8, null);
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

    public static class StaticAssertFailedError extends StaticCheckError {

        public StaticAssertFailedError(String userMessage, int line, int column) {
            super("E308",
                    "static assertion failed" + (userMessage != null ? ": " + userMessage : ""),
                    line, column, "static_assert".length(), null);
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

    public static class MissingModuleDeclarationError extends StaticCheckError {

        public MissingModuleDeclarationError() {
            super("E309",
                    "Entry file is missing a 'module' declaration",
                    1, 0, 0,
                    "Add 'module <name>;' as the first statement in your file");
        }
    }

    public static class ModuleNameMismatchError extends StaticCheckError {

        public ModuleNameMismatchError(String file, String expected, String found, int line) {
            super("E310",
                    "Module name mismatch in '" + file + "': expected '" + expected + "' but found '" + found + "'",
                    line, 0, found.length(),
                    "Rename either the file or the 'module' declaration so they match");
        }
    }

    public static class StaticAssertRuntimeValueError extends StaticCheckError {

        public StaticAssertRuntimeValueError(String name, int line, int column) {
            super("E311",
                    "'static_assert' requires a compile-time expression, but '$" + name + "' is a runtime variable",
                    line, column, name.length(),
                    "Declare the variable inside a 'comptime { }' block to use it in static_assert");
        }
    }

    public static class PostUnaryStaticError extends StaticCheckError {

        public PostUnaryStaticError(String op, int line, int column) {
            super("E312",
                    "'" + op + "' can only be applied to a variable reference",
                    line, column, op.length(),
                    "Use '$variable" + op + "' to increment or decrement a variable");
        }
    }

    public static class RangeStepZeroStaticError extends StaticCheckError {

        public RangeStepZeroStaticError(int line, int column) {
            super("E313",
                    "Range step cannot be zero",
                    line, column, 1,
                    "Use a non-zero step value, e.g. '<0..10, 2>'");
        }
    }

    public static class ReturnOutsideFunctionError extends StaticCheckError {

        public ReturnOutsideFunctionError(int line, int column) {
            super("E314",
                    "'return' used outside of a function",
                    line, column, "return".length(),
                    "Move this 'return' inside a function body");
        }
    }

    public static class DivisionByZeroStaticError extends StaticCheckError {

        public DivisionByZeroStaticError(int line, int column) {
            super("E315",
                    "Division by zero",
                    line, column, 1,
                    "The divisor is the literal 0 — this will always produce Infinity at runtime");
        }
    }

    public static class LiteralNotCallableError extends StaticCheckError {

        public LiteralNotCallableError(String value, int line, int column) {
            super("E316",
                    "'" + value + "' is a literal and cannot be called as a function",
                    line, column, value.length(),
                    "Only functions and lambdas can be called with '()'");
        }
    }

    public static class NotIterableStaticError extends StaticCheckError {

        public NotIterableStaticError(int line, int column) {
            super("E317",
                    "Value is not iterable — expected a list, tuple, or range",
                    line, column, 1,
                    "Use a list '{...}', a tuple '[...]', or a range expression as the collection");
        }
    }

    public static class PrivateImportError extends StaticCheckError {

        public PrivateImportError(String symbol, String module, int line, int column) {
            super("E318",
                    "Cannot import private symbol '" + symbol + "' from module '" + module + "'",
                    line, column, symbol.length(),
                    "Mark the declaration with 'pub' in '" + module + "' to make it importable");
        }
    }

    public static class UnknownModuleSymbolError extends StaticCheckError {

        public UnknownModuleSymbolError(String symbol, String module, int line, int column) {
            super("E319",
                    "Symbol '" + symbol + "' is not defined in module '" + module + "'",
                    line, column, symbol.length(),
                    "Check the spelling and make sure the symbol is declared in '" + module + "'");
        }
    }
}
