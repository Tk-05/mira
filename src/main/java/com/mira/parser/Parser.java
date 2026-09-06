package com.mira.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mira.error.MiraError;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.error.parser.ParserError;
import com.mira.error.parser.ParserError.LexemeMismatchError;
import com.mira.error.parser.ParserError.TypeMismatchError;
import com.mira.error.parser.ParserError.UnexpectedToken;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AssignExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.parser.nodes.expression.Expression.LambdaExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.parser.nodes.expression.Expression.MethodCallExpression;
import com.mira.parser.nodes.expression.Expression.NamespaceCallExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.RangeExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.StructInitExpression;
import com.mira.parser.nodes.expression.Expression.SwitchExpression;
import com.mira.parser.nodes.expression.Expression.TernaryExpression;
import com.mira.parser.nodes.expression.Expression.ThrownException;
import com.mira.parser.nodes.expression.Expression.TypeofExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.CatchClause;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.StaticAssert;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.SwitchCase;
import com.mira.parser.nodes.statement.Statement.TestCall;
import com.mira.parser.nodes.statement.Statement.Throw;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.vocabulary.Vocabulary;

public class Parser {

    private List<Token> tokens;
    private int index;
    private int parsingDepth = 0;
    private int lastClosingBraceLine = 0;
    private final List<MiraError> errors = new ArrayList<>();
    private Token lastConsumed = null;

    /**
     * Bareword names bound by "import ... as alias" seen so far in this parse.
     * Needed to tell "alias.function(...)" (a static namespace call) apart
     * from "variable.method(...)" (a normal method call) now that both are
     * spelled identically without a "$" sigil to mark plain variables.
     */
    private final java.util.Set<String> knownAliases = new java.util.HashSet<>();

    public List<Node> parseTokens(List<Token> tokens) {
        reset();
        this.tokens = tokens;

        List<Node> asts = new ArrayList<>();

        while (peek().getTokenType() != TokenType.EOF) {
            try {
                asts.addAll(parseStatement(true));
            } catch (ParserError e) {
                errors.add(e);
                synchronize();
            }
        }

        if (!errors.isEmpty()) {
            throw new MultipleParserErrors(errors);
        }

        return asts;
    }

    private void synchronize() {
        while (peek().getTokenType() != TokenType.EOF) {
            Token t = peek();
            if (t.getTokenType() == TokenType.DELIMITER) {
                if (t.getLexeme().equals(";")) {
                    consume();
                    return;
                }
                if (t.getLexeme().equals("}")) {
                    consume();
                    return;
                }
            }
            if (t.getTokenType() == TokenType.KEYWORD) {
                String lex = t.getLexeme();
                if (lex.equals("var") || lex.equals("fn") || lex.equals("if")
                        || lex.equals("for") || lex.equals("while") || lex.equals("return")
                        || lex.equals("import") || lex.equals("export") || lex.equals("class")
                        || lex.equals("type") || lex.equals("async") || lex.equals("pure")) {
                    return;
                }
            }
            consume();
        }
    }

    private Token consume() {
        lastConsumed = tokens.get(index);
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
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
        Token found = peek();
        if (lastConsumed != null && found.getTokenType() != TokenType.EOF) {
            throw new LexemeMismatchError(found, lastConsumed, "Expected '" + expectedLexeme + "'");
        }
        throw new LexemeMismatchError(found, "Expected '" + expectedLexeme + "'");
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

    private Token matchExpression() {
        if (isExpressionToken(peek())) {
            return consume();
        }
        throw new TypeMismatchError(peek(), "Expected an expression");
    }

    private Token matchIdentifier() {
        Token token = consume();
        if (token.getTokenType() == TokenType.KEYWORD) {
            throw new UnexpectedToken(token,
                    "'" + token.getLexeme() + "' is a reserved keyword and cannot be used as an identifier",
                    "Choose a different name");
        }
        return token;
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

    /**
     * True for a bareword token that names a variable (C-style: no sigil) —
     * an EXPRESSION token that isn't a numeric literal. NUMBER and IDENT share
     * the same token type, so the distinction is "does it start with a digit."
     */
    private boolean isVariableNameToken(Token token) {
        if (token.getTokenType() != TokenType.EXPRESSION) {
            return false;
        }
        String lex = token.getLexeme();
        return !lex.isEmpty() && !Character.isDigit(lex.charAt(0));
    }

    /**
     * Wraps a bareword identifier token as a variable reference, reusing the
     * existing internal "$" unary node shape so every downstream visitor
     * (interpreter, compiler, resolver, tooling) needs no change.
     */
    private Expression wrapAsVariableRef(Token nameToken) {
        Token dollar = new Token(TokenType.OPERATION, "$", nameToken.getLine(), nameToken.getColumn());
        return new UnaryExpression(dollar, new DumbExpression(nameToken));
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

    private boolean isWhitespaceToken(Token token) {
        return token.getTokenType() == TokenType.EXPRESSION && token.getLexeme().isBlank();
    }

    private boolean isBareColon(Token token) {
        return token.getLexeme().equals(":") && token.getTokenType() != TokenType.STRING_LITERAL;
    }

    private boolean isAssignment() {
        if (!isVariableNameToken(peek())) {
            return false;
        }

        int offset = 1;

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
        return lex.equals(":") || lex.equals("+:") || lex.equals("-:") || lex.equals("*:") || lex.equals("/:")
                || lex.equals("%:") || lex.equals("&:") || lex.equals("|:") || lex.equals("^:")
                || lex.equals("**:") || lex.equals("\\%:");
    }

    private void reset() {
        index = 0;
        errors.clear();
        lastConsumed = null;
        knownAliases.clear();
    }

    private void increaseDepth() {
        parsingDepth++;
    }

    private void decreaseDepth() {
        if (parsingDepth > 0) {
            parsingDepth--;
        }
    }

    private void skipWhitespaceTokens() {
        while (peek().getTokenType() != TokenType.EOF && isWhitespaceToken(peek())) {
            consume();
        }
    }

    private Expression parseAssignmentExpression() {
        Expression left = parsePratt(0);
        if (left instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d
                && (Character.isLetter(d.getValue().charAt(0)) || d.getValue().charAt(0) == '_')
                && peek().getLexeme().equals(":")
                && peek().getTokenType() != TokenType.STRING_LITERAL) {
            consume();
            Expression val = parseAssignmentExpression();
            return new AssignExpression(left, val);
        }
        return left;
    }

    private Expression parseExpression() {
        skipWhitespaceTokens();
        if (peek().getTokenType() == TokenType.EOF || isStructuralDelimiter(peek()) || isBareColon(peek())) {
            throw new UnexpectedToken(peek(), "Expected an expression, but the statement is empty");
        }
        return parseAssignmentExpression();
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
                Expression thenExpr = parseTernaryBranch();
                matchLexeme(":");
                Expression elseExpr = parseTernaryBranch();
                left = new TernaryExpression(left, thenExpr, elseExpr);
                break;
            }

            if (opToken.getTokenType() == TokenType.STRING_LITERAL) {
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

    private Expression parseTernaryBranch() {
        skipWhitespaceTokens();
        if (peek().getTokenType() == TokenType.EOF || isStructuralDelimiter(peek()) || isBareColon(peek())) {
            throw new UnexpectedToken(peek(), "Expected expression in ternary branch");
        }
        return parsePratt(0);
    }

    private int binaryOperatorBP(String op) {
        return Vocabulary.OPERATOR_PRECEDENCE.getOrDefault(op, 0);
    }

    private Expression parsePrimary() {
        skipWhitespaceTokens();
        Token current = peek();
        Expression expr;

        if (current.getLexeme().equals("<")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            expr = parseRangeExpression();

        } else if ((current.getLexeme().equals("++") || current.getLexeme().equals("--"))
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            Token op = consume();
            expr = new UnaryExpression(op, parsePrimary(), true);

        } else if ((current.getLexeme().equals("!")
                || current.getLexeme().equals("-")
                || current.getLexeme().equals("+")
                || current.getLexeme().equals("~"))
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            Token op = consume();
            expr = new UnaryExpression(op, parsePrimary());

        } else if (current.getLexeme().equals("{")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            if (peekOffset(1).getLexeme().equals("var") || peekOffset(1).getLexeme().equals("const") || peekOffset(1).getLexeme().equals("fn")) {
                expr = parseObjectExpression();
            } else if (peekOffset(1).getTokenType() == TokenType.STRING_LITERAL && peekOffset(2).getLexeme().equals(":")) {
                expr = parseMap();
            } else {
                expr = parseList();
            }

        } else if (current.getLexeme().equals("[")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            expr = parseArray();

        } else if (current.getLexeme().equals("(")
                && current.getTokenType() != TokenType.STRING_LITERAL) {
            if (isArrowLambda()) {
                expr = parseArrowLambdaExpression();
            } else {
                consume();
                Expression first = parseExpression();
                matchLexeme(")");
                expr = first;
            }

        } else if (current.getLexeme().equals("struct")
                && current.getTokenType() == TokenType.KEYWORD) {
            consume();
            expr = parseStructExpression();

        } else if (current.getLexeme().equals("typeof")
                && current.getTokenType() == TokenType.KEYWORD) {
            consume();
            expr = new TypeofExpression(parsePrimary());

        } else if (current.getLexeme().equals("switch")
                && current.getTokenType() == TokenType.KEYWORD) {
            consume();
            expr = parseSwitchExpression();

        } else if (current.getLexeme().equals("await")
                && current.getTokenType() == TokenType.KEYWORD) {
            consume();
            expr = new AwaitExpression(parsePrimary());

        } else if (current.getLexeme().equals("async")
                && current.getTokenType() == TokenType.KEYWORD
                && peekNextSafe().getLexeme().equals("fn")) {
            consume();
            expr = parseLambdaExpression(true);

        } else if (current.getLexeme().equals("fn")
                && current.getTokenType() == TokenType.KEYWORD) {
            expr = parseLambdaExpression(false);

        } else if ((current.getLexeme().equals("true") || current.getLexeme().equals("false"))
                && current.getTokenType() == TokenType.KEYWORD) {
            expr = new DumbExpression(consume());

        } else if (isExpressionToken(current)
                && current.getLexeme().equals("exec")
                && (peekNextSafe().getLexeme().equals("{")
                || peekNextSafe().getLexeme().equals("isolated"))) {
            consume();
            boolean isolated = false;
            if (peek().getLexeme().equals("isolated")) {
                consume();
                isolated = true;
            }
            Token open = matchLexeme("{");
            List<Node> body = parseBlockBody(open);
            expr = new ExecBlock(body, isolated);

        } else if (isExpressionToken(current)
                && peekNextSafe().getLexeme().equals("(")
                && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL) {
            expr = parseCallExpression();

        } else if (isExpressionToken(current)
                && knownAliases.contains(current.getLexeme())
                && peekNextSafe().getLexeme().equals(".")
                && peekNextSafe().getTokenType() != TokenType.STRING_LITERAL
                && peekOffset(3).getLexeme().equals("(")) {
            expr = parseNamespaceCallExpression();

        } else {
            expr = parseDumbExpression();
            expr = maybeParseFieldAccess(expr);
        }

        Expression prev;
        do {
            prev = expr;
            expr = maybeParseFieldAccess(expr);
            expr = maybeParseAccess(expr);
            expr = maybeParseCallOnExpr(expr);
        } while (expr != prev);
        expr = parsePostfix(expr);
        return expr;
    }

    private Expression parseDumbExpression() {
        Token token = peek();
        if (!isExpressionToken(token)) {
            throw new TypeMismatchError(token, "Expected EXPRESSION or STRING_LITERAL");
        }
        consume();

        if (token.getTokenType() == TokenType.STRING_LITERAL) {
            return new DumbExpression(spliceAdjacentStringLiterals(token));
        }

        if (isVariableNameToken(token)) {
            return wrapAsVariableRef(token);
        }

        return new DumbExpression(token);
    }

    /**
     * C-style adjacent string literal concatenation: "a" "b" -> "ab", spliced
     * at parse time. Safe to do unconditionally since both sides are
     * unambiguously STRING_LITERAL tokens — no operator, no precedence, no
     * juxtaposition-vs-binary-op ambiguity involved.
     */
    private Token spliceAdjacentStringLiterals(Token first) {
        if (peek().getTokenType() != TokenType.STRING_LITERAL) {
            return first;
        }
        StringBuilder combined = new StringBuilder(first.getLexeme());
        while (peek().getTokenType() == TokenType.STRING_LITERAL) {
            combined.append(consume().getLexeme());
        }
        return new Token(TokenType.STRING_LITERAL, combined.toString(), first.getLine(), first.getColumn());
    }

    private Expression parsePostfix(Expression expr) {
        while (peek().getLexeme().equals("++") || peek().getLexeme().equals("--")) {
            Token op = consume();
            expr = new UnaryExpression(op, expr);
        }
        return expr;
    }

    private Expression maybeParseFieldAccess(Expression base) {
        while (true) {
            boolean isOptionalDot = peek().getLexeme().equals("?.")
                    && peek().getTokenType() != TokenType.STRING_LITERAL
                    && isExpressionToken(peekOffset(1));
            boolean isNormalDot = peek().getLexeme().equals(".")
                    && peek().getTokenType() != TokenType.STRING_LITERAL
                    && isExpressionToken(peekOffset(1));
            if (!isOptionalDot && !isNormalDot) {
                break;
            }
            boolean optional = isOptionalDot;
            consume();
            String memberName = matchExpression().getLexeme();
            if (peek().getLexeme().equals("(") && peek().getTokenType() != TokenType.STRING_LITERAL) {
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
                base = new MethodCallExpression(base, memberName, args, optional);
            } else {
                base = new FieldAccessExpression(base, memberName, optional);
            }
        }
        return base;
    }

    private Expression maybeParseAccess(Expression base) {
        if (peek().getLexeme().equals("{") && peek().getTokenType() != TokenType.STRING_LITERAL
                && (peekOffset(1).getLexeme().equals("}")
                || (isVariableNameToken(peekOffset(1)) && peekOffset(2).getLexeme().equals(":")))) {
            return parseStructInit(base);
        }
        if (peek().getLexeme().equals("[") && peek().getTokenType() != TokenType.STRING_LITERAL
                || peek().getLexeme().equals("{") && peek().getTokenType() != TokenType.STRING_LITERAL) {
            return parseAccessExpression(base);
        }
        return base;
    }

    private Expression maybeParseCallOnExpr(Expression base) {
        if (peek().getLexeme().equals("(") && peek().getTokenType() != TokenType.STRING_LITERAL) {
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
            CallExpression ce = new CallExpression(base, args);
            ce.line = base.line;
            return ce;
        }
        return base;
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
        CallExpression ce = new CallExpression(new DumbExpression(referencedFunction), args);
        ce.line = referencedFunction.getLine();
        return ce;
    }

    private Expression parseNamespaceCallExpression() {
        var aliasToken = matchExpression();
        String alias = aliasToken.getLexeme();
        int callLine = aliasToken.getLine();
        matchLexeme(".");
        var fnToken = matchExpression();
        String functionName = fnToken.getLexeme();
        int callColumn = fnToken.getColumn();
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
        return new NamespaceCallExpression(alias, functionName, args, callLine, callColumn);
    }

    private Expression parseObjectExpression() {
        FieldsAndMethods fm = parseFieldsAndMethods();
        return new ObjectExpression(fm.fields(), fm.methods());
    }

    private Expression parseStructExpression() {
        FieldsAndMethods fm = parseFieldsAndMethods();
        return new StructExpression(fm.fields(), fm.methods());
    }

    private record FieldsAndMethods(List<VarDecl> fields, List<FuncDecl> methods) {

    }

    private FieldsAndMethods parseFieldsAndMethods() {
        matchLexeme("{");
        List<VarDecl> fields = new ArrayList<>();
        List<FuncDecl> methods = new ArrayList<>();

        while (!peek().getLexeme().equals("}")) {
            if (peek().getLexeme().equals("fn")) {
                FuncDecl method = (FuncDecl) parseFuncDecl(false);
                methods.add(method);
            } else {
                Token startToken = peek();
                boolean isConst = startToken.getLexeme().equals("const");
                for (Node n : parseVarDecl(isConst)) {
                    if (n instanceof VarDecl vd) {
                        vd.line = startToken.getLine();
                        vd.column = startToken.getColumn();
                    }
                    fields.add((VarDecl) n);
                }
                matchLexeme(";");
            }
        }

        matchLexeme("}");
        return new FieldsAndMethods(fields, methods);
    }

    private Expression parseStructInit(Expression target) {
        matchLexeme("{");
        LinkedHashMap<String, Expression> overrides = new LinkedHashMap<>();
        while (!peek().getLexeme().equals("}")) {
            String fieldName = matchIdentifier().getLexeme();
            matchLexeme(":");
            Expression value = parseExpression();
            overrides.put(fieldName, value);
            if (!peek().getLexeme().equals("}")) {
                matchLexeme(",");
            }
        }
        matchLexeme("}");
        return new StructInitExpression(target, overrides);
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

    private Expression parseArray() {
        matchLexeme("[");

        List<Expression> members = new ArrayList<>();
        while (!peek().getLexeme().equals("]")) {
            members.add(parseExpression());
            if (!peek().getLexeme().equals("]")) {
                matchLexeme(",");
            }
        }
        matchLexeme("]");

        return new ArrayExpression(members);
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

    // Precedence of '<'/'>'/'<='/'>=' (see Vocabulary.OPERATOR_PRECEDENCE) — used as the
    // Pratt parser's minBP for range operands so a bare '>' is never consumed as "greater
    // than" and is left for parseRangeExpression() to match as the closing bracket instead.
    // Comparison/logical/pipe operators (precedence <= this) are therefore not usable
    // directly inside a range operand; everything tighter (+ - * / % \% ** << >>) is.
    private static final int RANGE_OPERAND_MIN_BP = 7;

    private Expression parseRangeOperand() {
        return parsePratt(RANGE_OPERAND_MIN_BP);
    }

    private Expression parseLambdaExpression(boolean isAsync) {
        matchLexeme("fn");
        matchLexeme("(");

        String[] variadicHolder = {null};
        List<Parameter> parameters = parseParameterList(variadicHolder);
        matchLexeme(")");

        Token lambdaOpen = matchLexeme("{");
        List<Node> body = parseBlockBody(lambdaOpen);

        return new LambdaExpression(parameters, body, variadicHolder[0], isAsync);
    }

    private boolean isArrowLambda() {
        int depth = 0;
        int offset = 0;
        while (index + offset < tokens.size()) {
            String lex = peekOffset(offset).getLexeme();
            if (lex.equals("(")) {
                depth++;
            } else if (lex.equals(")")) {
                depth--;
                if (depth == 0) {
                    return peekOffset(offset + 1).getLexeme().equals("->");
                }
            }
            offset++;
        }
        return false;
    }

    private Expression parseArrowLambdaExpression() {
        matchLexeme("(");
        String[] variadicHolder = {null};
        List<Parameter> parameters = parseParameterList(variadicHolder);
        matchLexeme(")");
        matchLexeme("->");

        List<Node> body = new ArrayList<>();
        if (peek().getLexeme().equals("{")) {
            Token arrowOpen = matchLexeme("{");
            body = parseBlockBody(arrowOpen);
        } else {
            Expression result = parseExpression();
            body.add(new Return(result));
        }

        return new LambdaExpression(parameters, body, variadicHolder[0], false, true);
    }

    private Node parseImportExpression() {
        if (parsingDepth != 0) {
            throw new UnexpectedToken(peek(), "Imports must be declared at the top level, not inside functions or blocks");
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
            knownAliases.add(alias);
            return new ImportExpression(new DumbExpression(new Token(TokenType.STRING_LITERAL, path, 0, 0)), alias, ImportKind.NATIVE);
        }

        if (peek().getLexeme().equals("module")) {
            consume();
            String path = matchExpression().getLexeme();
            List<String> selected = null;
            if (peek().getLexeme().equals("{")) {
                consume();
                selected = new ArrayList<>();
                selected.add(matchExpression().getLexeme());
                while (peek().getLexeme().equals(",")) {
                    consume();
                    selected.add(matchExpression().getLexeme());
                }
                matchLexeme("}");
            }
            String alias = null;
            if (peek().getLexeme().equals("as")) {
                consume();
                alias = matchExpression().getLexeme();
                knownAliases.add(alias);
            }
            return new ImportExpression(new DumbExpression(new Token(TokenType.STRING_LITERAL, path, 0, 0)), alias, ImportKind.MODULE, selected);
        }

        Expression libExpr = new DumbExpression(matchExpression());
        List<String> selected = null;
        if (peek().getLexeme().equals(":") || peek().getLexeme().equals("{")) {
            boolean hasBrace = peek().getLexeme().equals("{");
            consume();
            selected = new ArrayList<>();
            selected.add(matchExpression().getLexeme());
            while (peek().getLexeme().equals(",")) {
                consume();
                selected.add(matchExpression().getLexeme());
            }
            if (hasBrace) {
                matchLexeme("}");
            }
        }
        String libAlias = null;
        if (peek().getLexeme().equals("as")) {
            consume();
            libAlias = matchExpression().getLexeme();
            knownAliases.add(libAlias);
        }
        return new ImportExpression(libExpr, libAlias, ImportKind.STDLIB, selected);
    }

    private List<Node> parseStatement(boolean expectSemicolon) {
        int line = peek().getLine();
        int column = peek().getColumn();
        lastClosingBraceLine = 0;
        Node node;

        if (peek().getLexeme().equals("comptime") && peek().getTokenType() == TokenType.KEYWORD) {
            Token comptimeToken = peek();
            consume();
            if (parsingDepth > 0) {
                throw new UnexpectedToken(comptimeToken, "'comptime' is only allowed at the top level", "Move this block outside of any function or block body");
            }
            Token open = matchLexeme("{");
            List<Node> body = parseBlockBody(open);
            ComptimeBlock comptimeBlock = new ComptimeBlock(body);
            comptimeBlock.line = line;
            comptimeBlock.column = column;
            comptimeBlock.endLine = lastClosingBraceLine;
            return List.of(comptimeBlock);
        }

        boolean isPublic = false;
        if (peek().getLexeme().equals("pub") && peek().getTokenType() == com.mira.lexer.token.TokenType.KEYWORD) {
            if (parsingDepth != 0) {
                throw new UnexpectedToken(peek(), "'pub' is only allowed at the top level, not inside functions or blocks");
            }
            consume();
            isPublic = true;
        }

        if (peek().getLexeme().equals("pure") && peek().getTokenType() == com.mira.lexer.token.TokenType.KEYWORD) {
            consume();
            if (!peek().getLexeme().equals("fn")) {
                throw new UnexpectedToken(peek(), "Expected 'fn' after 'pure'");
            }
            increaseDepth();
            node = parseFuncDecl(false, true, isPublic);
            decreaseDepth();
            if (node instanceof Statement stmt) {
                stmt.line = line;
                stmt.column = column;
                stmt.endLine = lastClosingBraceLine;
            }
            return List.of(node);
        }

        switch (peek().getLexeme()) {
            case "var" -> {
                List<Node> decls = parseVarDecl(false, isPublic);
                if (expectSemicolon) {
                    matchLexeme(";");
                }
                for (Node n : decls) {
                    if (n instanceof Statement s) {
                        s.line = line;
                        s.column = column;
                        s.endLine = lastConsumed != null ? lastConsumed.getLine() : 0;
                    }
                }
                return decls;
            }
            case "const" -> {
                List<Node> decls = parseVarDecl(true, isPublic);
                if (expectSemicolon) {
                    matchLexeme(";");
                }
                for (Node n : decls) {
                    if (n instanceof Statement s) {
                        s.line = line;
                        s.column = column;
                        s.endLine = lastConsumed != null ? lastConsumed.getLine() : 0;
                    }
                }
                return decls;
            }
            case "fn" -> {
                increaseDepth();
                node = parseFuncDecl(false, false, isPublic);
                decreaseDepth();
            }
            case "async" -> {
                consume();
                if (!peek().getLexeme().equals("fn")) {
                    throw new UnexpectedToken(peek(), "Expected 'fn' after 'async'");
                }
                increaseDepth();
                node = parseFuncDecl(true, false, isPublic);
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
            case "{" -> {
                node = parseBlock();
            }
            case "module" -> {
                node = parseModuleDecl();
            }
            case "import" -> {
                node = parseImportExpression();
                if (node instanceof ImportExpression imp) {
                    imp.line = line;
                }
                matchLexeme(";");
            }
            case "switch" -> {
                node = parseSwitch();
            }
            case "enum" -> {
                node = parseEnumDecl(isPublic);
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
            case "lock" -> {
                node = parseLock();
            }
            case "test" -> {
                node = parseTestCall();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            case "static_assert" -> {
                node = parseStaticAssert();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
            default -> {
                node = isAssignment() ? parseAssign() : parseExpression();
                if (expectSemicolon) {
                    matchLexeme(";");
                }
            }
        }

        if (node instanceof Statement stmt) {
            stmt.line = line;
            stmt.column = column;
            stmt.endLine = lastConsumed != null ? lastConsumed.getLine() : lastClosingBraceLine;
        }
        return List.of(node);
    }

    private Node parseTestCall() {
        Token testToken = peek();
        matchLexeme("test");
        if (parsingDepth > 0) {
            throw new UnexpectedToken(testToken, "'test' is only allowed at the top level", "Move this test block outside of any function or block body");
        }
        matchLexeme("(");
        Expression name = parseExpression();
        matchLexeme(",");
        Expression testFn = parseExpression();
        matchLexeme(")");
        return new TestCall(name, testFn);
    }

    private Node parseStaticAssert() {
        matchLexeme("static_assert");
        matchLexeme("(");
        Expression condition = parseExpression();
        Expression message = null;
        if (peek().getLexeme().equals(",")) {
            matchLexeme(",");
            message = parseExpression();
        }
        matchLexeme(")");
        return new StaticAssert(condition, message);
    }

    private Node parseModuleDecl() {
        matchLexeme("module");
        String name = matchExpression().getLexeme();
        matchLexeme(";");
        return new ModuleDecl(name);
    }

    private List<Node> parseVarDecl(boolean isConst) {
        return parseVarDecl(isConst, false);
    }

    private List<Node> parseVarDecl(boolean isConst, boolean isPublic) {
        consume();
        if (peek().getLexeme().equals("(")) {
            consume();
            List<String> names = new ArrayList<>();
            List<Integer> nameColumns = new ArrayList<>();
            while (!peek().getLexeme().equals(")")) {
                Token nameToken = matchExpression();
                names.add(nameToken.getLexeme());
                nameColumns.add(nameToken.getColumn());
                if (!peek().getLexeme().equals(")")) {
                    matchLexeme(",");
                }
            }
            matchLexeme(")");
            matchLexeme(":");
            Expression initializer = parseExpression();
            return List.of(new VarDestructure(names, nameColumns, initializer));
        }
        List<Node> decls = new ArrayList<>();
        while (true) {
            skipWhitespaceTokens();
            Token nameToken = matchIdentifier();
            String identifier = nameToken.getLexeme();
            Expression initializer = null;
            if (peek().getLexeme().equals(":")) {
                consume();
                initializer = parseExpression();
            } else if (isConst) {
                throw new UnexpectedToken(peek(), "const '" + identifier + "' must have an initializer");
            } else if (!peek().getLexeme().equals(";") && !peek().getLexeme().equals("in")
                    && !peek().getLexeme().equals(")") && !peek().getLexeme().equals(",")) {
                throw new UnexpectedToken(peek(), "Unexpected token");
            }
            VarDecl vd = new VarDecl(identifier, initializer, isConst, isPublic);
            vd.nameColumn = nameToken.getColumn();
            decls.add(vd);
            if (peek().getLexeme().equals(",") && !peekOffset(1).getLexeme().equals("var")) {
                consume();
            } else {
                break;
            }
        }
        return decls;
    }

    private List<Parameter> parseParameterList(String variadicParamHolder[]) {
        List<Parameter> parameters = new ArrayList<>();
        while (!peek().getLexeme().equals(")")) {
            if (peek().getLexeme().equals("...")) {
                consume();
                variadicParamHolder[0] = matchExpression().getLexeme();
                break;
            }
            Token paramToken = matchExpression();
            String paramName = paramToken.getLexeme();
            Expression defaultValue = null;
            if (peek().getLexeme().equals(":")) {
                consume();
                defaultValue = parseExpression();
            }
            parameters.add(new Parameter(paramName, defaultValue, paramToken.getColumn()));
            if (!peek().getLexeme().equals(")")) {
                matchLexeme(",");
            }
        }
        return parameters;
    }

    private Node parseFuncDecl(boolean isAsync) {
        return parseFuncDecl(isAsync, false, false);
    }

    private Node parseFuncDecl(boolean isAsync, boolean isPure) {
        return parseFuncDecl(isAsync, isPure, false);
    }

    private Node parseFuncDecl(boolean isAsync, boolean isPure, boolean isPublic) {
        Token kwToken = matchLexeme("fn");
        requireNotIncomplete(kwToken, "name(params) { body }");
        Token nameToken = matchExpression();
        String name = nameToken.getLexeme();
        matchLexeme("(");

        String[] variadicHolder = {null};
        List<Parameter> parameters = parseParameterList(variadicHolder);
        matchLexeme(")");

        Token open = matchLexeme("{");
        List<Node> body = parseBlockBody(open);

        FuncDecl decl = new FuncDecl(name, parameters, body, variadicHolder[0], isAsync, isPure, isPublic);
        decl.line = kwToken.getLine();
        decl.nameColumn = nameToken.getColumn();
        return decl;
    }

    private Node parseReturn() {
        consume();
        Expression value;
        boolean startsExpression = isExpressionToken(peek())
                || peek().getTokenType() == TokenType.OPERATION
                || peek().getLexeme().equals("{")
                || peek().getLexeme().equals("(")
                || (peek().getTokenType() == TokenType.KEYWORD
                && (peek().getLexeme().equals("switch")
                || peek().getLexeme().equals("await")
                || peek().getLexeme().equals("async")
                || peek().getLexeme().equals("fn")));
        if (startsExpression) {
            value = parseExpression();
        } else {
            value = new DumbExpression(new Token(null, "0.0", -1, -1));
        }
        return new Return(value);
    }

    private Node parseAssign() {
        Expression reference = wrapAsVariableRef(matchExpression());

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
        if (op.equals("+:") || op.equals("-:") || op.equals("*:") || op.equals("/:")
                || op.equals("%:") || op.equals("&:") || op.equals("|:") || op.equals("^:")
                || op.equals("**:") || op.equals("\\%:")) {
            consume();
            Expression rhs = parseExpression();
            Token arithOp = new Token(TokenType.OPERATION, op.substring(0, op.length() - 1), 0, 0);
            return new Assign(reference, new BinaryExpression(reference, arithOp, rhs));
        }

        matchLexeme(":");
        Expression expression = parseExpression();
        return new Assign(reference, expression);
    }

    private Node parseIf() {
        Token kwToken = matchLexeme("if");
        requireNotIncomplete(kwToken, "(condition) { body }");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");

        List<Node> thenBody = parseBody();

        if (peek().getLexeme().equals("else")) {
            matchLexeme("else");
            if (peek().getLexeme().equals("if")) {
                List<Node> elseIfBody = new ArrayList<>();
                elseIfBody.add(parseIf());
                return new If(condition, thenBody, elseIfBody);
            }
            List<Node> elseBody = parseBody();
            return new If(condition, thenBody, elseBody);
        } else {
            return new If(condition, thenBody, null);
        }
    }

    private Node parseFor() {
        Token kwToken = matchLexeme("for");
        requireNotIncomplete(kwToken, "(init; condition; post) { body }");
        matchLexeme("(");

        if (peek().getLexeme().equals("<")) {
            Expression range = parseRangeExpression();
            matchLexeme(")");
            List<Node> body = parseBody();
            return Loop.foreachStyle(new VarDecl("_", null, false), range, body);
        }

        if (peek().getLexeme().equals("var") && peekOffset(2).getLexeme().equals("in")) {
            VarDecl iterator = (VarDecl) parseVarDecl(false).getFirst();
            matchLexeme("in");

            Expression collection = peek().getLexeme().equals("<")
                    ? parseRangeExpression()
                    : parseExpression();

            matchLexeme(")");

            List<Node> body = parseBody();

            return Loop.foreachStyle(iterator, collection, body);
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
                        varDecls.addAll(parseVarDecl(false));
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
                postExpressions.addAll(parseStatement(false));
            }
            matchLexeme(")");

            List<Node> body = parseBody();

            return Loop.cStyle(varDecls, condition, postExpressions, body);
        } else {
            matchLexeme(")");
            List<Node> body = parseBody();

            return Loop.cStyle(varDecls, null, null, body);
        }
    }

    private Node parseWhile() {
        Token kwToken = matchLexeme("while");
        requireNotIncomplete(kwToken, "(condition) { body }");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");
        List<Node> body = parseBody();

        return new While(condition, body, false);
    }

    private Node parseDoWhile() {
        Token kwToken = matchLexeme("do");
        requireNotIncomplete(kwToken, "{ body } while (condition)");
        List<Node> body = parseBody();

        matchLexeme("while");
        matchLexeme("(");
        Expression condition = parseExpression();
        matchLexeme(")");

        return new While(condition, body, true);
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
        String identifier = matchExpression().getLexeme();
        matchLexeme("(");
        Expression value = null;
        if (peek().getTokenType() != TokenType.DELIMITER) {
            value = parseExpression();
        }
        matchLexeme(")");

        return new Throw(new ThrownException(identifier, value));
    }

    private Node parseLock() {
        matchLexeme("lock");
        matchLexeme("(");
        Expression mutex = parseExpression();
        matchLexeme(")");
        matchLexeme("{");
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            body.addAll(parseStatement(true));
        }
        lastClosingBraceLine = matchLexeme("}").getLine();
        return new Statement.Lock(mutex, body);
    }

    private Node parseTryCatch() {
        Token kwToken = matchLexeme("try");
        requireNotIncomplete(kwToken, "{ body } catch (Error e) { handler }");
        matchLexeme("{");
        List<Node> tryBody = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            tryBody.addAll(parseStatement(true));
        }
        lastClosingBraceLine = matchLexeme("}").getLine();

        List<CatchClause> catchClauses = new ArrayList<>();
        while (peek().getLexeme().equals("catch")) {
            matchLexeme("catch");

            String typeFilter = null;
            String paramName = null;

            if (peek().getLexeme().equals("(")) {
                matchLexeme("(");
                skipWhitespaceTokens();
                String firstToken = matchExpression().getLexeme();
                skipWhitespaceTokens();
                if (!peek().getLexeme().equals(")")) {
                    typeFilter = firstToken;
                    paramName = matchExpression().getLexeme();
                    skipWhitespaceTokens();
                } else {
                    typeFilter = null;
                    paramName = firstToken;
                }
                matchLexeme(")");
            }

            matchLexeme("{");
            List<Node> catchBody = new ArrayList<>();
            while (!peek().getLexeme().equals("}")) {
                catchBody.addAll(parseStatement(true));
            }
            lastClosingBraceLine = matchLexeme("}").getLine();

            catchClauses.add(new CatchClause(typeFilter, paramName, catchBody));
        }

        List<Node> finallyBody = new ArrayList<>();
        if (peek().getLexeme().equals("finally")) {
            matchLexeme("finally");
            matchLexeme("{");
            while (!peek().getLexeme().equals("}")) {
                finallyBody.addAll(parseStatement(true));
            }
            lastClosingBraceLine = matchLexeme("}").getLine();
        }

        return new TryCatch(tryBody, catchClauses, finallyBody);
    }

    private List<Node> parseBody() {
        if (peek().getLexeme().equals("{")) {
            Token open = matchLexeme("{");
            return parseBlockBody(open);
        }
        return new ArrayList<>(parseStatement(true));
    }

    private List<Node> parseBlockBody(Token open) {
        List<Node> body = new ArrayList<>();
        while (!peek().getLexeme().equals("}")) {
            if (peek().getTokenType() == TokenType.EOF) {
                throw new LexemeMismatchError(peek(),
                        "Expected '}' to close block opened at line " + open.getLine() + ", column " + open.getColumn());
            }
            try {
                body.addAll(parseStatement(true));
            } catch (ParserError e) {
                errors.add(e);
                synchronizeInBlock();
            }
        }
        lastClosingBraceLine = matchLexeme("}").getLine();
        return body;
    }

    private void synchronizeInBlock() {
        while (peek().getTokenType() != TokenType.EOF) {
            Token t = peek();
            if (t.getTokenType() == TokenType.DELIMITER) {
                if (t.getLexeme().equals(";")) {
                    consume();
                    return;
                }
                if (t.getLexeme().equals("}")) {
                    return;
                }
            }
            if (t.getTokenType() == TokenType.KEYWORD) {
                String lex = t.getLexeme();
                if (lex.equals("var") || lex.equals("const") || lex.equals("fn")
                        || lex.equals("if") || lex.equals("for")
                        || lex.equals("while") || lex.equals("return") || lex.equals("switch")
                        || lex.equals("try") || lex.equals("throw") || lex.equals("break")
                        || lex.equals("continue") || lex.equals("async") || lex.equals("lock")) {
                    return;
                }
            }
            consume();
        }
    }

    private Node parseBlock() {
        Token open = matchLexeme("{");
        return new Block(parseBlockBody(open));
    }

    private void requireNotIncomplete(Token kwToken, String expectedSyntax) {
        int offset = 0;
        Token next;
        do {
            next = peekOffset(offset++);
        } while (isWhitespaceToken(next));
        if (next.getTokenType() == TokenType.EOF
                || (next.getTokenType() == TokenType.DELIMITER && next.getLexeme().equals("}"))) {
            throw new UnexpectedToken(kwToken,
                    "Incomplete '" + kwToken.getLexeme() + "' statement",
                    "Expected: " + kwToken.getLexeme() + " " + expectedSyntax);
        }
    }

    private Node parseSwitchArrowBody() {
        skipWhitespaceTokens();
        String lex = peek().getLexeme();
        if (isAssignment()) {
            Node assign = parseAssign();
            matchLexeme(";");
            return assign;
        }

        if (peek().getTokenType() == TokenType.KEYWORD) {
            return switch (lex) {
                case "var", "const", "return", "break", "continue", "throw" ->
                    parseStatement(true).getFirst();
                case "if" ->
                    parseIf();
                case "while" ->
                    parseWhile();
                case "for" ->
                    parseFor();
                default ->
                    parsePratt(0);
            };
        }

        return parsePratt(0);
    }

    private Expression parseSwitchExpression() {
        matchLexeme("(");
        Expression subject = parseExpression();
        matchLexeme(")");
        matchLexeme("{");

        List<SwitchExpression.SwitchExprCase> cases = new ArrayList<>();
        Expression defaultExpr = null;

        while (!peek().getLexeme().equals("}")) {
            switch (peek().getLexeme()) {
                case "case" -> {
                    matchLexeme("case");
                    matchLexeme("(");
                    Expression value = parseExpression();
                    matchLexeme(")");
                    matchLexeme("->");
                    skipWhitespaceTokens();
                    Expression result = parsePratt(0);
                    cases.add(new SwitchExpression.SwitchExprCase(value, result));
                }
                case "default" -> {
                    matchLexeme("default");
                    matchLexeme("->");
                    skipWhitespaceTokens();
                    defaultExpr = parsePratt(0);
                }
                default ->
                    throw new UnexpectedToken(peek(), "Expected 'case' or 'default' in switch expression");
            }
        }

        matchLexeme("}");
        return new SwitchExpression(subject, cases, defaultExpr);
    }

    private Node parseSwitch() {
        Token kwToken = matchLexeme("switch");
        requireNotIncomplete(kwToken, "(expression) { case ... }");
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
                    List<Node> body = new ArrayList<>();
                    if (peek().getLexeme().equals("->")) {
                        matchLexeme("->");
                        body.add(parseSwitchArrowBody());
                    } else {
                        matchLexeme("{");
                        while (!peek().getLexeme().equals("}")) {
                            body.addAll(parseStatement(true));
                        }
                        lastClosingBraceLine = matchLexeme("}").getLine();
                    }
                    cases.add(new SwitchCase(value, body));
                }
                case "default" -> {
                    matchLexeme("default");
                    defaultBody = new ArrayList<>();
                    if (peek().getLexeme().equals("->")) {
                        matchLexeme("->");
                        defaultBody.add(parseSwitchArrowBody());
                    } else {
                        matchLexeme("{");
                        while (!peek().getLexeme().equals("}")) {
                            defaultBody.addAll(parseStatement(true));
                        }
                        lastClosingBraceLine = matchLexeme("}").getLine();
                    }
                }
                default ->
                    throw new UnexpectedToken(peek(), "Expected 'case' or 'default' in switch body");
            }
        }

        lastClosingBraceLine = matchLexeme("}").getLine();
        return new Switch(subject, cases, defaultBody);
    }

    private Node parseEnumDecl() {
        return parseEnumDecl(false);
    }

    private Node parseEnumDecl(boolean isPublic) {
        matchLexeme("enum");
        String identifier = matchType(TokenType.EXPRESSION).getLexeme();
        matchLexeme("{");

        Map<String, Expression> values = new LinkedHashMap<>();
        int autoIndex = 0;

        while (!peek().getLexeme().equals("}")) {
            String name = matchType(TokenType.EXPRESSION).getLexeme();

            Expression value;
            if (peek().getLexeme().equals(":")) {
                consume();
                value = parseExpression();
            } else {
                value = new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(autoIndex), 0, 0));
            }

            values.put(name, value);
            autoIndex++;

            if (peek().getLexeme().equals(",")) {
                consume();
            }
        }

        matchLexeme("}");

        return new EnumDecl(values, identifier, isPublic);
    }
}
