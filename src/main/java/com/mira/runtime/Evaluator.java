package com.mira.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.mira.error.runtime.RuntimeError.MismatchedParenthesesError;
import com.mira.error.runtime.RuntimeError.UnknownOperatorError;
import com.mira.error.runtime.RuntimeError.UnknownSymbolError;

public class Evaluator {

    private static final Environment environment = Interpreter.getGlobalEnvironment();

    public static Double evaluate(String expression) {
        List<String> tokens = tokenize(expression);
        List<String> rpn = toRPN(tokens);
        return evalRPN(rpn);
    }

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;

        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isDigit(c)) {
                int j = i;
                boolean dotSeen = false;

                while (j < expr.length()) {
                    char ch = expr.charAt(j);

                    if (Character.isDigit(ch)) {
                        j++;
                        continue;
                    }

                    if (ch == '.' && !dotSeen) {
                        dotSeen = true;
                        j++;
                        continue;
                    }

                    break;
                }

                tokens.add(expr.substring(i, j));
                i = j;
                continue;
            }

            if (Character.isLetter(c) || c == '$') {
                int j = i;
                while (j < expr.length()
                        && (Character.isLetterOrDigit(expr.charAt(j)) || expr.charAt(j) == '_' || expr.charAt(j) == '$')) {
                    j++;
                }
                tokens.add(expr.substring(i, j));
                i = j;
                continue;
            }

            if ("+-*/()".indexOf(c) != -1) {
                tokens.add(Character.toString(c));
                i++;
                continue;
            }

            throw new UnknownSymbolError(c);
        }

        return tokens;
    }

    private static List<String> toRPN(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> operators = new ArrayDeque<>();

        Map<String, Integer> precedence = Map.of(
                "+", 1,
                "-", 1,
                "*", 2,
                "/", 2,
                "u-", 3
        );

        boolean unaryContext = true;

        for (String token : tokens) {

            if (isNumber(token) || isVariable(token)) {
                output.add(token);
                unaryContext = false;
                continue;
            }

            if ("+-*/".contains(token)) {
                String op = token;

                if (token.equals("-") && unaryContext) {
                    op = "u" + token;
                }

                while (!operators.isEmpty()
                        && !"(".equals(operators.peek())
                        && precedence.getOrDefault(operators.peek(), 0) >= precedence.get(op)) {
                    output.add(operators.pop());
                }

                operators.push(op);
                unaryContext = true;
                continue;
            }

            if ("(".equals(token)) {
                operators.push(token);
                unaryContext = true;
                continue;
            }

            if (")".equals(token)) {
                while (!operators.isEmpty() && !"(".equals(operators.peek())) {
                    output.add(operators.pop());
                }
                if (operators.isEmpty()) {
                    throw new MismatchedParenthesesError();
                }
                operators.pop();
                unaryContext = false;
            }
        }

        while (!operators.isEmpty()) {
            String op = operators.pop();
            if ("()".contains(op)) {
                throw new MismatchedParenthesesError();
            }
            output.add(op);
        }

        return output;
    }

    private static double evalRPN(List<String> rpn) {

        Deque<Double> stack = new ArrayDeque<>();

        for (String token : rpn) {

            if (isNumber(token)) {
                stack.push(Double.valueOf(token));
                continue;
            }

            if (isVariable(token)) {
                Object val = environment.get(token);
                if (val == null) {
                    throw new UnknownSymbolError(token);
                }

                stack.push(Double.valueOf(val.toString()));
                continue;
            }

            if (token.equals("u-")) {
                stack.push(-stack.pop());
                continue;
            }

            double b = stack.pop();
            double a = stack.pop();

            switch (token) {

                case "+" ->
                    stack.push(a + b);
                case "-" ->
                    stack.push(a - b);
                case "*" ->
                    stack.push(a * b);
                case "/" ->
                    stack.push(a / b);

                default ->
                    throw new UnknownOperatorError(token);
            }
        }

        return stack.pop();
    }

    private static boolean isNumber(String s) {
        return s.matches("\\d+(\\.\\d+)?");
    }

    private static boolean isVariable(String s) {
        return s.matches("[$A-Za-z_][A-Za-z0-9_]*");
    }
}
