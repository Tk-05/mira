package com.mira.error.runtime;

import com.mira.error.MiraError;

public class RuntimeError extends MiraError {

    protected RuntimeError(String errorCode, String message, String hint) {
        super(errorCode, message, hint);
    }

    public static class ObjectAlreadyDefinedInScope extends RuntimeError {

        public ObjectAlreadyDefinedInScope(String name) {
            super("E201",
                    "'" + name + "' is already defined in this scope",
                    "Rename the variable, or remove the duplicate declaration");
        }
    }

    public static class UndefinedVariableError extends RuntimeError {

        public UndefinedVariableError(String identifier) {
            super("E202",
                    "Undefined variable '" + identifier + "'",
                    "Declare '" + identifier + "' with 'var' or 'const' before using it");
        }

        public UndefinedVariableError(String identifier, String hint) {
            super("E202", "Undefined variable '" + identifier + "'", hint);
        }
    }

    public static class ArgMismatchError extends RuntimeError {

        public ArgMismatchError(String function, int expected, int actual) {
            super("E203",
                    "'" + function + "' expects " + expected + " argument" + (expected == 1 ? "" : "s")
                    + ", but got " + actual,
                    "Check the function signature and the number of arguments you are passing");
        }

        public ArgMismatchError(String function, int expected, int actual, String signature) {
            super("E203",
                    "'" + function + "' expects " + expected + " argument" + (expected == 1 ? "" : "s")
                    + ", but got " + actual,
                    signature);
        }
    }

    public static class UndefinedReferenceError extends RuntimeError {

        public UndefinedReferenceError(String identifier) {
            super("E204",
                    "'" + identifier + "' is not defined",
                    "Make sure '" + identifier + "' is imported or declared before use");
        }

        public UndefinedReferenceError(String identifier, String hint) {
            super("E204", "'" + identifier + "' is not defined", hint);
        }
    }

    public static class ReferenceIsImmutableError extends RuntimeError {

        public ReferenceIsImmutableError(String name) {
            super("E205",
                    "Cannot reassign constant '" + name + "'",
                    "Declare with 'var' instead of 'const' if the value needs to change");
        }
    }

    public static class UnknownSymbolError extends RuntimeError {

        public UnknownSymbolError(char c) {
            super("E206", "Unknown symbol '" + c + "'", null);
        }

        public UnknownSymbolError(String s) {
            super("E206", "Unknown symbol '" + s + "'", null);
        }
    }

    public static class MismatchedParenthesesError extends RuntimeError {

        public MismatchedParenthesesError() {
            super("E207", "Mismatched parentheses",
                    "Make sure every '(' has a matching ')'");
        }
    }

    public static class UnknownOperatorError extends RuntimeError {

        public UnknownOperatorError(String token) {
            super("E208", "Unknown operator '" + token + "'",
                    "Check the list of supported operators in the language reference");
        }
    }

    public static class PostUnaryError extends RuntimeError {

        public PostUnaryError(String operation) {
            super("E209",
                    "'" + operation + "' requires a variable reference (e.g. $x" + operation + ")",
                    null);
        }
    }

    public static class PostExprNaNError extends RuntimeError {

        public PostExprNaNError(String ident) {
            super("E210",
                    "Cannot apply numeric increment/decrement to '" + ident + "' — it is not a number",
                    null);
        }
    }

    public static class AssertionFailedError extends RuntimeError {

        public AssertionFailedError() {
            super("E211", "Assertion failed", null);
        }

        public AssertionFailedError(String message) {
            super("E211", "Assertion failed: " + message, null);
        }
    }

    public static class NotCallableError extends RuntimeError {

        public NotCallableError(String name) {
            super("E212",
                    "'" + name + "' is not a function and cannot be called",
                    "Make sure '" + name + "' is declared as a function with 'fn'");
        }
    }

    public static class NotANamespaceError extends RuntimeError {

        public NotANamespaceError(String alias) {
            super("E213",
                    "'" + alias + "' is not a namespace",
                    "Import the module before accessing it with '.'");
        }
    }

    public static class ImmutableCollectionError extends RuntimeError {

        public ImmutableCollectionError() {
            super("E214",
                    "Cannot modify an immutable collection",
                    "Only mutable lists (declared with '{...}') support index assignment");
        }
    }

    public static class FieldAccessError extends RuntimeError {

        public FieldAccessError(String field) {
            super("E215",
                    "Cannot access field '" + field + "' on a non-object value",
                    "Make sure the value is an object literal before using '.' field access");
        }
    }

    public static class TypeConversionError extends RuntimeError {

        public TypeConversionError(Object value) {
            super("E216",
                    "Cannot convert '" + value + "' to a number",
                    "Make sure the value is a numeric string or a number before using it in arithmetic");
        }
    }

    public static class RangeStepZeroError extends RuntimeError {

        public RangeStepZeroError() {
            super("E217",
                    "Range step cannot be zero",
                    "Provide a non-zero step value");
        }
    }

    public static class NotIterableError extends RuntimeError {

        public NotIterableError() {
            super("E218",
                    "Value is not iterable — expected a list, tuple, or range",
                    "Use a list '{...}', a tuple '[...]', or a range expression as the collection");
        }
    }

    public static class NoModuleDeclarationError extends RuntimeError {

        public NoModuleDeclarationError() {
            super("E219",
                    "Entry file is missing a 'module' declaration",
                    "Add 'module <name>;' as the first statement in your file");
        }
    }

    public static class InvalidArgumentError extends RuntimeError {

        public InvalidArgumentError(String function, String detail) {
            super("E220",
                    "Invalid argument to '" + function + "': " + detail,
                    null);
        }
    }

    public static class LibImportConflictError extends RuntimeError {

        public LibImportConflictError(String libA, String libB, java.util.Set<String> conflicting) {
            super("E221",
                    "Import conflict between '" + libA + "' and '" + libB + "': "
                    + "conflicting names: " + conflicting,
                    "Use 'import " + libB + " as <alias>;' to avoid name collisions");
        }
    }

    public static class NativeLibNotFoundError extends RuntimeError {

        public NativeLibNotFoundError(String path) {
            super("E222",
                    "Native JAR not found: '" + path + "'",
                    "Check that the file path is correct and the JAR exists");
        }
    }

    public static class NativeLibNoImplementationError extends RuntimeError {

        public NativeLibNoImplementationError(String path) {
            super("E223",
                    "No Lib implementation found in native JAR: '" + path + "'",
                    "Ensure the JAR contains META-INF/services/com.mira.lib.Lib "
                    + "and that the listed class implements com.mira.lib.Lib");
        }
    }

    public static class NativeLibLoadError extends RuntimeError {

        public NativeLibLoadError(String path, Throwable cause) {
            super("E224",
                    "Failed to load native JAR '" + path + "': " + cause.getMessage(),
                    "Verify the JAR is a valid archive and its classes are compatible "
                    + "with the current interpreter version");
        }
    }

    public static class ModuleMissingDeclarationError extends RuntimeError {

        public ModuleMissingDeclarationError(String module) {
            super("E225",
                    "Module '" + module + "' is missing a 'module' declaration",
                    "Add 'module <name>;' as the first statement in '" + module + "'");
        }
    }

    public static class ModuleNameMismatchError extends RuntimeError {

        public ModuleNameMismatchError(String file, String expected, String found) {
            super("E226",
                    "Module name mismatch in '" + file + "': expected '" + expected + "' but found '" + found + "'",
                    "Rename either the file or the 'module' declaration so they match");
        }
    }

    public static class LocalCallableError extends RuntimeError {

        public LocalCallableError(String name) {
            super("E227",
                    "'" + name + "' is a local variable, not a global function",
                    "Use $" + name + "() to call a variable");
        }
    }
}
