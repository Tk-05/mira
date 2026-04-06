package com.mira.error.runtime;

public class RuntimeError extends RuntimeException {

    public RuntimeError(String message) {
        super(message);
    }

    public static class ObjectAlreadyDefinedInScope extends RuntimeError {

        public ObjectAlreadyDefinedInScope(String name) {
            super("Object '" + name + "' is alreay defined in scope!");
        }
    }

    public static class UndefinedVariableError extends RuntimeError {

        public UndefinedVariableError(String identifier) {
            super("Undefined variable '" + identifier + "'");
        }
    }

    public static class ArgMismatchError extends RuntimeError {

        public ArgMismatchError(String function, int expected, int actual) {
            super(function + " expected " + expected + " args but got " + actual);
        }
    }

    public static class UndefinedReferenceError extends RuntimeError {

        public UndefinedReferenceError(String identifier) {
            super("Referenced object " + identifier + " is undefined");
        }
    }

    public static class ReferenceIsImmutableError extends RuntimeError {

        public ReferenceIsImmutableError(String message) {
            super(message);
        }
    }

    public static class UnknownSymbolError extends RuntimeError {

        public UnknownSymbolError(char c) {
            super("Unknown symbol: " + c);
        }

        public UnknownSymbolError(String s) {
            super("Unknown symbol: " + s);
        }
    }

    public static class MismatchedParenthesesError extends RuntimeError {

        public MismatchedParenthesesError() {
            super("Mismatched parentheses");
        }
    }

    public static class UnknownOperatorError extends RuntimeError {

        public UnknownOperatorError(String token) {
            super("Unknown operator: " + token);
        }
    }

    public static class PostUnaryError extends RuntimeError {

        public PostUnaryError(String operation) {
            super(operation + "requires a variable reference like $x");
        }
    }

    public static class PostExprNaNError extends RuntimeError {

        public PostExprNaNError(String ident) {
            super("Cannot change non-number: " + ident);
        }
    }

    public static class AssertionFailedError extends RuntimeError {

        public AssertionFailedError() {
            super("Assertion failed");
        }

        public AssertionFailedError(String message) {
            super("Assertion failed: " + message);
        }
    }
}
