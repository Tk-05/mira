package com.mira.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mira.error.parser.ParserError.LexemeMismatchError;
import com.mira.error.parser.ParserError.TypeMismatchError;
import com.mira.error.parser.ParserError.UnexpectedToken;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.vocabulary.Vocabulary;

public class Parser {

    private List<Token> tokens;
    private int index;
    private int parsingDepth = 0;

    private void reset() {
        index = 0;
    }

    public List<Node> parseTokens(List<Token> tokens) {
        reset();
        this.tokens = tokens;

        List<Node> asts = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF) {
            asts.add(parseStatement(true));
        }

        return asts;
    }

    private void increaseDepth() {
        parsingDepth++;
    }

    private void decreaseDepth() {
        if (parsingDepth > 0) {
            parsingDepth--;
        }
    }

    private Token consume() {
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token peekNext() {
        return tokens.get(index + 1);
    }

    private Token peekNextSafe() {
        if (index + 1 >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(index + 1);
    }

    private Token peekOffset(int offset) {
        if (index + offset >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(index + offset);
    }

    private void consumeExpected(String lexeme) {
        if (!peek().getLexeme().equals(lexeme)) {
            throw new LexemeMismatchError(peek(), "Expected '" + lexeme + "'");
        }
        consume();
    }

    private boolean expectLexeme(String expectedLexeme) {
        if (!peek().getLexeme().equals(expectedLexeme)) {
            throw new LexemeMismatchError(peek(), "Expected '" + expectedLexeme + "'");
        }
        return true;
    }

    private Token matchLexeme(String expectedLexeme) {
        if (peek().getLexeme().equals(expectedLexeme)) {
            return consume();
        }
        throw new LexemeMismatchError(peek(), "Expected '" + expectedLexeme + "'");
    }

    private Token matchType(TokenType expectedType) {
        if (peek().getTokenType() == TokenType.EOF) {
            return null;
        }
        if (peek().getTokenType() == expectedType) {
            return consume();
        }
        throw new TypeMismatchError(peek(), "Expected an expression");
    }

    private boolean isExpressionToken(Token token) {
        return token.getTokenType() == TokenType.EXPRESSION
                || token.getTokenType() == TokenType.STRING_LITERAL
                || isBooleanLiteral(token)
                || isNullLiteral(token);
    }

    private boolean isBooleanLiteral(Token token) {
        return token.getTokenType() == TokenType.KEYWORD
                && (token.getLexeme().equals("true") || token.getLexeme().equals("false"));
    }

    private boolean isNullLiteral(Token token) {
        return token.getTokenType() == TokenType.KEYWORD
                && token.getLexeme().equals("null");
    }

    private boolean isStructuralDelimiter(Token token) {
        if (token.getTokenType() == TokenType.STRING_LITERAL) {
            return false;
        }
        return switch (token.getLexeme()) {
            case ";", ")", ",", "]", "}" ->
                true;
            default ->
                false;
        };
    }

    private Token matchExpression() {
        if (isExpressionToken(peek())) {
            return consume();
        }
        throw new TypeMismatchError(peek(), "Expected an expression");
    }

    private Node parseStatement(boolean expectSemicolon) {
        Node node;

        switch (peek().getLexeme()) {
            case "var" -> {
                node = parseVarDecl(false);
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "const" -> {
                node = parseVarDecl(true);
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "fn" -> {
                increaseDepth();
                node = parseFuncDecl();
                decreaseDepth();
            }
            case "return" -> {
                node = parseReturn();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "if" -> {
                node = parseIf();
            }
            case "for" -> {
                node = parseFor();
            }
            case "foreach" -> {
                node = parseForeach();
            }
            case "do" -> {
                node = parseDoWhile();
                matchLexeme(";");
            }
            case "while" -> {
                node = parseWhile();
            }
            case "break" -> {
                node = parseBreak();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "continue" -> {
                node = parseContinue();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "$" -> {
                node = isAssignment() ? parseAssign() : parseExpression();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "{" -> {
                node = parseBlock();
            }
            case "module" -> {
                node = parseModuleDecl();
            }
            case "import" -> {
                node = parseImportExpression();
                matchLexeme(";");
            }
            case "switch" -> {
                node = parseSwitch();
            }
            case "overwrite" -> {
                node = parseOverwrite();
                matchLexeme(";");
            }
            case "enum" -> {
                node = parseEnumDecl();
            }
            case "try" -> {
                node = parseTryCatch();
            }
            case "throw" -> {
                node = parseThrow();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            default -> {
                node = parseExpression();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
        }

        return node;
    }

    private boolean isWhitespaceToken(Token token) {
        return token.getTokenType() == TokenType.EXPRESSION && token.getLexeme().isBlank();
    }

    private void skipWhitespaceTokens() {
        while (peek().getTokenType() != TokenType.EOF && isWhitespaceToken(peek())) {
            consume();
        }
    }

    private int binaryOperatorBP(String op) {
        return switch (op) {
            case "|>", "||" ->
                1;
            case "&&" ->
                2;
            case "|" ->
                3;
            case "^" ->
                4;
            case "&" ->
                5;
            case "==", "!=" ->
                6;
            case "<", ">", "<=", ">=" ->
                7;
            case "<<", ">>" ->
                8;
            case "+", "-" ->
                9;
            case "*", "/", "%" ->
                10;
            default ->
                0;
        };
    }

    private Expression parsePrimary() {
        skipWhitespaceTokens();
        Token current = peek();
        Expression expr;

        if (current.getLexeme().equals("$")) {
            Expression unary = parseUnaryExpression();
            expr = maybeParseFieldAccess(unary);

        } else if ((current.getLexeme().equals("!")
                || current.getLexeme().equals("-")
                || current.getLexeme().equals("+")
                || current.getLexeme().equals("~"))
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            Token op = consume();
            expr = new UnaryExpression(op, parsePrimary());

        } else if (current.getLexeme().equals("{")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            if (peekOffset(1).getLexeme().equals("var") || peekOffset(1).getLexeme().equals("const")) {
                expr = parseObjectExpression();
            } else if (peekOffset(1).getTokenType() == TokenType.STRING_LITERAL && peekOffset(2).getLexeme().equals(":")) {
                expr = parseMap();
            } else {
                expr = parseList();
            }

        } else if (current.getLexeme().equals("[")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            expr = parseTuple();

        } else if (current.getLexeme().equals("(")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            consume();
            expr = parseExpression();
            consumeExpected(")");

        } else if (current.getLexeme().equals("fn")
                && current.getTokenType() == TokenType.KEYWORD) {
            expr = parseLambdaExpression();

        } else if ((current.getLexeme().equals("true") || current.getLexeme().equals("false"))
                && current.getTokenType() == TokenType.KEYWORD) {
            expr = new DumbExpression(consume());

        } else if (isExpressionToken(current)
                && peekNextSafe().getLexeme().equals("(")
                && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL) {
            expr = parseCallExpression();

        } else if (isExpressionToken(current)
                && peekNextSafe().getLexeme().equals(".")
                && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL
                && peekOffset(3).getLexeme().equals("(")) {
            expr = parseNamespaceCallExpression();

        } else {
            expr = parseDumbExpression();
            expr = maybeParseFieldAccess(expr);
        }

        expr = maybeParseAccess(expr);
        expr = parsePostfix(expr);
        return expr;
    }

    private Expression parsePratt(int minBP) {
        Expression left = parsePrimary();

        while (true) {
            skipWhitespaceTokens();
            Token opToken = peek();

            if (opToken.getLexeme().equals("?") && opToken.getTokenType() != TokenType.STRING_LITERAL) {
                if (minBP > 0) {
                    break;
                }
                consume();
                Expression thenExpr = parsePratt(0);
                matchLexeme(":");
                Expression elseExpr = parsePratt(0);
                left = new TernaryExpression(left, thenExpr, elseExpr);
                break;
            }

            int lbp = binaryOperatorBP(opToken.getLexeme());
            if (lbp == 0 || lbp <= minBP) {
                break;
            }
            consume();
            Expression right = parsePratt(lbp);
            left = new BinaryExpression(left, opToken, right);
        }

        return left;
    }

    private Expression parseExpression() {
        List<Expression> items = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF && !isStructuralDelimiter(peek())) {
            if (isWhitespaceToken(peek())) {
                consume();
                continue;
            }
            items.add(parsePratt(0));
        }

        if (items.isEmpty()) {
            throw new UnexpectedToken(peek(), "Expected an expression, but the statement is empty");
        }

        return items.size() == 1 ? items.get(0) : new ComplexExpression(items);
    }

    private Expression maybeParseFieldAccess(Expression base) {
        while (peek().getLexeme().equals(".")
                && peek().getTokenType() != TokenType.STRING_LITERAL
                && isExpressionToken(peekOffset(1))
                && !peekOffset(2).getLexeme().equals("(")) {
            consume();
            String fieldName = matchExpression().getLexeme();
            base = new FieldAccessExpression(base, fieldName);
        }
        return base;
    }

    private Expression maybeParseAccess(Expression base) {
        if (peek().getLexeme().equals("[") && peek().getTokenType() != TokenType.STRING_LITERAL
                || peek().getLexeme().equals("{") && peek().getTokenType() != TokenType.STRING_LITERAL) {
            return parseAccessExpression(base);
        }
        return base;
    }

    private Expression parsePostfix(Expression expr) {
        while (peek().getLexeme().equals("++") || peek().getLexeme().equals("--")) {
            Token op = consume();
            expr = new UnaryExpression(op, expr);
        }
        return expr;
    }

    private Expression parseUnaryExpression() {
        Token operation = matchType(TokenType.OPERATION);
        Expression rhs;
        if (peek().getTokenType() != TokenType.OPERATION) {
            rhs = parseDumbExpression();
        } else {
            rhs = parseExpression();
        }
        return new UnaryExpression(operation, rhs);
    }

    private Expression parseDumbExpression() {
        Token token = peek();
        if (isExpressionToken(token)) {
            consume();
            return new DumbExpression(token);
        }
        throw new TypeMismatchError(token, "Expected EXPRESSION or STRING_LITERAL");
    }

    private Expression parseObjectExpression() {
        matchLexeme("{");
        List<VarDecl> fields = new ArrayList<>();

        while (!peek().getLexeme().equals("}")) {
            boolean isConst = peek().getLexeme().equals("const");
            VarDecl field = (VarDecl) parseVarDecl(isConst);
            matchLexeme(";");
            fields.add(field);
        }

        matchLexeme("}");
        return new ObjectExpression(fields);
    }

    private Expression parseNamespaceCallExpression() {
        String alias = matchExpression().getLexeme();
        matchLexeme(".");
        String functionName = matchExpression().getLexeme();
        matchLexeme("(");

        List<Expression> args = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {
            args.add(parseExpression());
            if (peek().getLexeme().equals(",")) {
                matchLexeme(",");
            } else if (!peek().getLexeme().equals(")")) {
                throw new UnexpectedToken(peek(), "Expected ',' or ')'");
            }
        }

        matchLexeme(")");
        return new NamespaceCallExpression(alias, functionName, args);
    }

    private Expression parseCallExpression() {
        Token referencedFunction = matchExpression();
        matchLexeme("(");

        List<Expression> args = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {
            args.add(parseExpression());
            if (peek().getLexeme().equals(",")) {
                matchLexeme(",");
            } else if (!peek().getLexeme().equals(")")) {
                throw new UnexpectedToken(peek(), "Expected ',' or ')'");
            }
        }

        matchLexeme(")");
        return new CallExpression(new DumbExpression(referencedFunction), args);
    }

    private Expression parseAccessExpression(Expression accessedExpression) {
        List<Expression> indices = new ArrayList<>();

        while ((peek().getLexeme().equals("[") && peek().getTokenType() != TokenType.STRING_LITERAL)
                || (peek().getLexeme().equals("{") && peek().getTokenType() != TokenType.STRING_LITERAL)) {
            consume();

            Expression indexExpr = parseExpression();
            indices.add(indexExpr);

            if (peek().getLexeme().equals("}")) {
                matchLexeme("}");
            } else {
                matchLexeme("]");
            }
        }

        return new AccessExpression(accessedExpression, indices);
    }

    private Node parseModuleDecl() {
        matchLexeme("module");
        String name = matchExpression().getLexeme();
        matchLexeme(";");
        return new ModuleDecl(name);
    }

    private Node parseImportExpression() {
        if (parsingDepth != 0) {
            throw new AssertionError("Imports must be declared in global context!");
        }
        matchLexeme("import");

        if (peek().getLexeme().equals("native")) {
            consume();
            String path = matchExpression().getLexeme();
            String alias = null;
            if (peek().getLexeme().equals("as")) {
                consume();
                alias = matchExpression().getLexeme();
            }
            if (alias == null || alias.isBlank()) {
                throw new LexemeMismatchError(peek(),
                        "'import native' requires an alias: import native \"path.jar\" as name;");
            }
            return new ImportExpression(new DumbExpression(new Token(TokenType.STRING_LITERAL, path, 0, 0)), alias, ImportKind.NATIVE);
        }

        if (peek().getLexeme().equals("module")) {
            consume();
            String path = matchExpression().getLexeme();
            String alias = null;
            if (peek().getLexeme().equals("as")) {
                consume();
                alias = matchExpression().getLexeme();
            }
            return new ImportExpression(new DumbExpression(new Token(TokenType.STRING_LITERAL, path, 0, 0)), alias, ImportKind.MODULE);
        }

        Expression libExpr = new DumbExpression(matchExpression());
        String libAlias = null;
        if (peek().getLexeme().equals("as")) {
            consume();
            libAlias = matchExpression().getLexeme();
        }
        return new ImportExpression(libExpr, libAlias, ImportKind.STDLIB);
    }

    private Expression parseMap() {
        matchLexeme("{");
        LinkedHashMap<String, Expression> entries = new LinkedHashMap<>();
        while (!peek().getLexeme().equals("}")) {
            String key = matchExpression().getLexeme();
            matchLexeme(":");
            Expression value = parseExpression();
            entries.put(key, value);
            if (!peek().getLexeme().equals("}")) {
                matchLexeme(",");
            }
        }
        matchLexeme("}");
        return new MapExpression(entries);
    }

    private Expression parseList() {
        matchLexeme("{");

        List<Expression> members = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            members.add(parseExpression());
            if (!peek().getLexeme().equals("}")) {
                matchLexeme(",");
            }
        }
        matchLexeme("}");

        return new ListExpression(members);
    }

    private Expression parseTuple() {
        matchLexeme("[");

        List<Expression> members = new ArrayList<>();
        while (!peek().getLexeme().equals("]")) {
            members.add(parseExpression());
            if (!peek().getLexeme().equals("]")) {
                matchLexeme(",");
            }
        }
        matchLexeme("]");

        return new TupleExpression(members);
    }

    private Node parseVarDecl(boolean isConst) {
        consume();
        String identifier = consume().getLexeme();
        switch (peek().getLexeme()) {
            case ":" -> {
                consume();
                Expression expr = parseExpression();
                return new VarDecl(identifier, expr, isConst);
            }
            case ";" -> {
                if (isConst) {
                    throw new UnexpectedToken(peek(), "const '" + identifier + "' must have an initializer");
                }
                return new VarDecl(identifier, null, false);
            }
            case "in" -> {
                return new VarDecl(identifier, null, false);
            }
            default -> {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }
        }
    }

    private Node parseFuncDecl() {
        matchLexeme("fn");
        String name = matchExpression().getLexeme();
        matchLexeme("(");

        List<DumbExpression> parameters = new ArrayList<>();
        String variadicParam = null;
        while (!peek().getLexeme().equals(")")) {
            if (peek().getLexeme().equals(",")) {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }

            if (peek().getLexeme().equals("...")) {
                consume();
                variadicParam = matchExpression().getLexeme();
                break;
            }

            if (peekNext().getLexeme().equals(")")) {
                parameters.add(new DumbExpression(matchExpression()));
                break;
            } else {
                parameters.add(new DumbExpression(matchExpression()));
                matchLexeme(",");
            }
        }
        matchLexeme(")");

        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            if (peek().getLexeme().equals("}")) {
                throw new UnexpectedToken(peek(), "Unexpected Token in function body");
            }
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new FuncDecl(name, parameters, body, variadicParam);
    }

    private Expression parseLambdaExpression() {
        matchLexeme("fn");
        matchLexeme("(");

        List<DumbExpression> parameters = new ArrayList<>();
        String variadicParam = null;
        while (!peek().getLexeme().equals(")")) {
            if (peek().getLexeme().equals(",")) {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }

            if (peek().getLexeme().equals("...")) {
                consume();
                variadicParam = matchExpression().getLexeme();
                break;
            }

            if (peekNext().getLexeme().equals(")")) {
                parameters.add(new DumbExpression(matchExpression()));
                break;
            } else {
                parameters.add(new DumbExpression(matchExpression()));
                matchLexeme(",");
            }
        }
        matchLexeme(")");

        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new LambdaExpression(parameters, body, variadicParam);
    }

    private Node parseReturn() {
        consume();
        Expression value;
        if (isExpressionToken(peek()) || peek().getTokenType() == TokenType.OPERATION) {
            value = parseExpression();
        } else {
            value = new DumbExpression(new Token(null, "0.0", -1, -1));
        }
        return new Return(value);
    }

    private Node parseAssign() {
        Expression reference = parseUnaryExpression();

        while (peek().getLexeme().equals(".")
                && peek().getTokenType() != TokenType.STRING_LITERAL
                && isExpressionToken(peekOffset(1))
                && !peekOffset(2).getLexeme().equals("(")) {
            consume();
            String fieldName = matchExpression().getLexeme();
            reference = new FieldAccessExpression(reference, fieldName);
        }
        if (peek().getLexeme().contains("[")) {
            reference = parseAccessExpression(reference);
        }

        String op = peek().getLexeme();
        if (op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=")
                || op.equals("%=") || op.equals("&=") || op.equals("|=") || op.equals("^=")) {
            consume();
            Expression rhs = parseExpression();
            Token arithOp = new Token(TokenType.OPERATION, op.substring(0, 1), 0, 0);
            return new Assign(reference, new BinaryExpression(reference, arithOp, rhs));
        }

        matchLexeme(":");
        Expression expression = parseExpression();
        return new Assign(reference, expression);
    }

    private boolean isAssignment() {
        if (!peek().getLexeme().equals("$")) {
            return false;
        }

        int offset = 1;

        if (!isExpressionToken(peekOffset(offset))) {
            return false;
        }
        offset++;

        while (peekOffset(offset).getLexeme().equals(".")) {
            offset++;
            if (!isExpressionToken(peekOffset(offset))) {
                return false;
            }
            offset++;
        }

        while (peekOffset(offset).getLexeme().equals("[")) {
            offset++;

            int bracketDepth = 1;

            while (bracketDepth > 0) {
                String lex = peekOffset(offset).getLexeme();
                if (lex.equals("[")) {
                    bracketDepth++;
                }
                if (lex.equals("]")) {
                    bracketDepth--;
                }
                offset++;
            }
        }

        String lex = peekOffset(offset).getLexeme();
        return lex.equals(":") || lex.equals("+=") || lex.equals("-=") || lex.equals("*=") || lex.equals("/=")
                || lex.equals("%=") || lex.equals("&=") || lex.equals("|=") || lex.equals("^=");
    }

    private Node parseIf() {
        matchLexeme("if");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");

        matchLexeme("{");
        List<Node> thenBody = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            if (peek().getLexeme().equals("}")) {
                throw new UnexpectedToken(peek(), "Unexpected Token in if body");
            }
            thenBody.add(parseStatement(true));
        }
        matchLexeme("}");

        if (peek().getLexeme().equals("else")) {
            matchLexeme("else");
            if (peek().getLexeme().equals("if")) {
                List<Node> elseIfBody = new ArrayList<>();
                elseIfBody.add(parseIf());
                return new If(condition, thenBody, elseIfBody);
            }
            matchLexeme("{");
            List<Node> elseBody = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                if (peek().getLexeme().equals("}")) {
                    throw new UnexpectedToken(peek(), "Unexpected Token in else body");
                }
                elseBody.add(parseStatement(true));
            }
            matchLexeme("}");
            return new If(condition, thenBody, elseBody);
        } else {
            return new If(condition, thenBody, null);
        }
    }

    private Node parseFor() {
        matchLexeme("for");
        matchLexeme("(");

        if (peek().getLexeme().equals("var")
                && peekOffset(2).getLexeme().equals("in")
                && peekOffset(3).getLexeme().equals("<")) {

            matchLexeme("var");
            String iteratorName = consume().getLexeme();
            matchLexeme("in");
            Expression range = parseRangeExpression();
            matchLexeme(")");

            matchLexeme("{");
            List<Node> body = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                body.add(parseStatement(true));
            }
            matchLexeme("}");

            return new Foreach(new VarDecl(iteratorName, null, false), range, body);
        }

        List<Node> varDecls = new ArrayList<>();
        boolean loop = true;
        while (loop) {
            switch (peek().getLexeme()) {
                case ";" -> {
                    loop = false;
                    matchLexeme(";");
                }
                case "," -> {
                    matchLexeme(",");
                    expectLexeme("var");
                }
                default -> {
                    if (!isStructuralDelimiter(peek())) {
                        varDecls.add(parseVarDecl(false));
                    } else {
                        loop = false;
                    }
                }
            }
        }

        if (!peek().getLexeme().equals(")")) {
            Expression condition = null;
            if (!peek().getLexeme().equals(";")) {
                condition = parseExpression();
            }
            matchLexeme(";");

            List<Node> postExpressions = new ArrayList<>();
            while (!peek().getLexeme().equals(")")) {
                postExpressions.add(parseStatement(false));
            }
            matchLexeme(")");

            matchLexeme("{");
            List<Node> body = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                body.add(parseStatement(true));
            }
            matchLexeme("}");

            return new For(varDecls, condition, postExpressions, body);
        } else {
            matchLexeme(")");
            matchLexeme("{");
            List<Node> body = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                body.add(parseStatement(true));
            }
            matchLexeme("}");

            return new For(varDecls, null, null, body);
        }
    }

    private Node parseDoWhile() {
        matchLexeme("do");
        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        matchLexeme("while");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");

        return new While(condition, body, true);
    }

    private Node parseWhile() {
        matchLexeme("while");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");
        matchLexeme("{");

        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new While(condition, body, false);
    }

    private Node parseBreak() {
        matchLexeme("break");
        return new Break();
    }

    private Node parseContinue() {
        matchLexeme("continue");
        return new Continue();
    }

    private Node parseThrow() {
        matchLexeme("throw");
        Expression value = parseExpression();
        return new Throw(value);
    }

    private Node parseTryCatch() {
        matchLexeme("try");
        matchLexeme("{");
        List<Node> tryBody = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            tryBody.add(parseStatement(true));
        }
        matchLexeme("}");

        matchLexeme("catch");
        matchLexeme("(");
        String catchParam = matchExpression().getLexeme();
        matchLexeme(")");
        matchLexeme("{");
        List<Node> catchBody = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            catchBody.add(parseStatement(true));
        }
        matchLexeme("}");

        return new TryCatch(tryBody, catchParam, catchBody);
    }

    private Node parseBlock() {
        matchLexeme("{");

        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new Block(body);
    }

    private Node parseSwitch() {
        matchLexeme("switch");
        matchLexeme("(");
        Expression subject = parseExpression();
        matchLexeme(")");
        matchLexeme("{");

        List<SwitchCase> cases = new ArrayList<>();
        List<Node> defaultBody = null;

        while (!peek().getLexeme().equals("}")) {
            switch (peek().getLexeme()) {
                case "case" -> {
                    matchLexeme("case");
                    matchLexeme("(");
                    Expression value = parseExpression();
                    matchLexeme(")");
                    matchLexeme("{");
                    List<Node> body = new ArrayList<>();
                    while (!peek().getLexeme().equals("}")) {
                        body.add(parseStatement(true));
                    }
                    matchLexeme("}");
                    cases.add(new SwitchCase(value, body));
                }
                case "default" -> {
                    matchLexeme("default");
                    matchLexeme("{");
                    defaultBody = new ArrayList<>();
                    while (!peek().getLexeme().equals("}")) {
                        defaultBody.add(parseStatement(true));
                    }
                    matchLexeme("}");
                }
                default ->
                    throw new UnexpectedToken(peek(), "Expected 'case' or 'default' in switch body");
            }
        }

        matchLexeme("}");
        return new Switch(subject, cases, defaultBody);
    }

    private Node parseOverwrite() {
        matchLexeme("overwrite");
        matchLexeme("(");
        String stmt = matchExpression().getLexeme();
        matchLexeme(")");
        return new Overwrite(stmt);
    }

    private Node parseForeach() {
        matchLexeme("foreach");
        matchLexeme("(");
        VarDecl iterator = (VarDecl) parseVarDecl(false);
        matchLexeme("in");

        Expression collection = peek().getLexeme().equals("<")
                ? parseRangeExpression()
                : parseExpression();

        matchLexeme(")");

        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.add(parseStatement(true));
        }
        matchLexeme("}");

        return new Foreach(iterator, collection, body);
    }

    private Expression parseRangeExpression() {
        matchLexeme("<");
        Expression start = parseRangeOperand();
        matchLexeme("..");
        Expression end = parseRangeOperand();

        Expression stepsize = null;
        if (peek().getLexeme().equals(",")) {
            consume();
            stepsize = parseRangeOperand();
        }

        matchLexeme(">");
        return new RangeExpression(start, end, stepsize);
    }

    private Expression parseRangeOperand() {
        List<Expression> expressions = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF
                && !peek().getLexeme().equals("..")
                && !peek().getLexeme().equals(">")
                && !isStructuralDelimiter(peek())) {

            Token current = peek();

            if (current.getLexeme().equals("$")) {
                Expression unary = parseUnaryExpression();
                expressions.add(parsePostfix(maybeParseAccess(unary)));

            } else if (isExpressionToken(current)
                    && peekNextSafe().getLexeme().equals("(")
                    && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL) {

                expressions.add(parsePostfix(maybeParseAccess(parseCallExpression())));

            } else if (isExpressionToken(current)
                    && peekNextSafe().getLexeme().equals(".")
                    && !peekOffset(2).getLexeme().equals(".")
                    && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL) {

                expressions.add(parsePostfix(parseNamespaceCallExpression()));

            } else if (Vocabulary.stringIsOperation(current.getLexeme())
                    && !current.getLexeme().equals("$")
                    && !current.getLexeme().equals("++")
                    && !current.getLexeme().equals("--")) {

                expressions.add(new UnaryExpression(consume(), null));

            } else {
                expressions.add(parsePostfix(maybeParseAccess(parseDumbExpression())));
            }
        }

        if (expressions.isEmpty()) {
            throw new UnexpectedToken(peek(), "Expected range operand but got '" + peek().getLexeme() + "'");
        }

        return expressions.size() > 1 ? new ComplexExpression(expressions) : expressions.get(0);
    }

    private Node parseEnumDecl() {
        matchLexeme("enum");
        String identifier = matchType(TokenType.EXPRESSION).getLexeme();
        matchLexeme("{");

        Map<String, Object> values = new LinkedHashMap<>();
        int autoIndex = 0;

        while (!peek().getLexeme().equals("}")) {
            String name = matchType(TokenType.EXPRESSION).getLexeme();

            Object value;
            if (peek().getLexeme().equals(":")) {
                consume();
                value = consume().getLexeme();
            } else {
                value = String.valueOf(autoIndex);
            }

            values.put(name, value);
            autoIndex++;

            if (peek().getLexeme().equals(",")) {
                consume();
            }
        }

        matchLexeme("}");

        return new EnumDecl(values, identifier);
    }
}
