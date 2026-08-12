package com.mira.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mira.error.MiraError;
import com.mira.error.parser.MultipleParserErrors;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.AccessExpression;
import com.mira.parser.nodes.expression.Expression.ArrayExpression;
import com.mira.parser.nodes.expression.Expression.AssignExpression;
import com.mira.parser.nodes.expression.Expression.AwaitExpression;
import com.mira.parser.nodes.expression.Expression.BinaryExpression;
import com.mira.parser.nodes.expression.Expression.CallExpression;
import com.mira.parser.nodes.expression.Expression.ComplexExpression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ExecBlock;
import com.mira.parser.nodes.expression.Expression.FieldAccessExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
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
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.Continue;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
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
import com.mira.runtime.visitors.ExprVisitor;
import com.mira.runtime.visitors.StmtVisitor;
import com.mira.vocabulary.Vocabulary;

@SuppressWarnings("unchecked")
public class AstFormatter implements ExprVisitor<String>, StmtVisitor<String> {

    private static final int INDENT_SIZE = 4;
    private int indentLevel = 0;
    private Map<Integer, List<String>> standaloneComments = new LinkedHashMap<>();
    private Map<Integer, String> inlineComments = new LinkedHashMap<>();

    public static String format(String source) {
        // Normalize CRLF to LF before tokenizing. scanTextBlock() only skips '\n',
        // so CRLF files would leave a '\r\n' at the start of text-block lexemes,
        // causing an extra blank line when the formatter re-adds its own '\n'.
        String src = source.replace("\r\n", "\n");
        try {
            List<Token> tokens = new Tokenizer().tokenize(src, false);
            List<Node> ast = new Parser().parseTokens(tokens);
            AstFormatter formatter = new AstFormatter();
            extractComments(src, formatter.standaloneComments, formatter.inlineComments);
            return formatter.formatProgram(ast);
        } catch (MiraError | MultipleParserErrors e) {
            return Formatter.format(src);
        }
    }

    private static void extractComments(String source,
            Map<Integer, List<String>> standalone,
            Map<Integer, String> inline) {
        int i = 0, line = 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"') {
                i = skipString(source, i);
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int commentLine = line;
                int start = i;
                int lineStart = start;
                while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }
                boolean isInline = !source.substring(lineStart, start).isBlank();
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                String text = source.substring(start, i).stripTrailing();
                if (isInline) {
                    inline.put(commentLine, text);
                } else {
                    standalone.computeIfAbsent(commentLine, k -> new ArrayList<>()).add(text);
                }
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int commentLine = line;
                int start = i;
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        line++;
                    }
                    i++;
                }
                i += 2; // skip */
                String text = source.substring(start, i).stripTrailing();
                standalone.computeIfAbsent(commentLine, k -> new ArrayList<>()).add(text);
            } else {
                if (c == '\n') {
                    line++;
                }
                i++;
            }
        }
    }

    private static int skipString(String source, int i) {
        i++; // skip opening "
        if (i + 1 < source.length() && source.charAt(i) == '"' && source.charAt(i + 1) == '"') {
            // text block """..."""
            i += 2;
            while (i + 2 < source.length()
                    && !(source.charAt(i) == '"' && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"')) {
                i++;
            }
            i = Math.min(i + 3, source.length());
        } else {
            while (i < source.length() && source.charAt(i) != '"' && source.charAt(i) != '\n') {
                if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                    i++; // skip escaped char
                }
                i++;
            }
            if (i < source.length() && source.charAt(i) == '"') {
                i++;
            }
        }
        return i;
    }

    private void appendComments(StringBuilder sb, int fromLine, int toLine, String indentation) {
        for (int cl = fromLine; cl <= toLine; cl++) {
            for (String comment : standaloneComments.getOrDefault(cl, List.of())) {
                sb.append(indentation).append(comment).append("\n");
            }
        }
    }

    private String formatProgram(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();

        // comments before the first node (file header comments)
        if (!nodes.isEmpty()) {
            int firstLine = nodeStartLine(nodes.get(0));
            if (firstLine > 1) {
                appendComments(sb, 1, firstLine - 1, "");
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int currLine = nodeStartLine(node);

            String formatted = formatNode(node);
            if (formatted == null || formatted.isBlank()) {
                continue;
            }

            int nodeEnd = (node instanceof Statement s && s.endLine > 0) ? s.endLine : currLine;
            if (formatted.contains("\n") && nodeEnd > currLine) {
                String startCom = currLine > 0 ? inlineComments.get(currLine) : null;
                if (startCom != null) {
                    int firstNl = formatted.indexOf('\n');
                    formatted = formatted.substring(0, firstNl) + " " + startCom + formatted.substring(firstNl);
                }
                String endCom = inlineComments.get(nodeEnd);
                if (endCom != null) {
                    formatted = formatted + " " + endCom;
                }
            } else {
                String inlineCom = currLine > 0 ? inlineComments.get(currLine) : null;
                if (inlineCom != null) {
                    formatted = formatted + " " + inlineCom;
                }
            }

            sb.append(formatted).append("\n");

            if (i < nodes.size() - 1) {
                Node next = nodes.get(i + 1);
                int nextLine = nodeStartLine(next);
                boolean currIsImport = isImport(node);
                boolean nextIsImport = isImport(next);

                if (!currIsImport || !nextIsImport) {
                    sb.append("\n");
                }

                if (currLine > 0 && nextLine > 0) {
                    appendComments(sb, nodeEnd + 1, nextLine - 1, "");
                }
            }

        }
        return sb.toString().stripTrailing() + "\n";
    }

    private boolean isImport(Node node) {
        return node instanceof ImportExpression;
    }

    private String formatNode(Node node) {
        if (node instanceof ModuleDecl md) {
            return "module " + md.getModuleName() + ";";
        }
        if (node instanceof ImportExpression imp) {
            return formatImport(imp);
        }
        if (node instanceof Statement stmt) {
            return stmt.accept(this);
        }
        if (node instanceof Expression expr) {
            return expr.accept(this) + ";";
        }
        return "";
    }

    private String formatImport(ImportExpression imp) {
        StringBuilder sb = new StringBuilder("import ");
        switch (imp.getKind()) {
            case MODULE ->
                sb.append("module \"").append(imp.getModule()).append("\"");
            case NATIVE ->
                sb.append("native \"").append(imp.getModule()).append("\"");
            default ->
                sb.append(imp.getModule());
        }
        if (imp.isSelective() && imp.getSelectedFunctions() != null && !imp.getSelectedFunctions().isEmpty()) {
            sb.append(" {").append(String.join(", ", imp.getSelectedFunctions())).append("}");
        }
        if (imp.getNamespace() != null) {
            sb.append(" as ").append(imp.getNamespace());
        }
        sb.append(";");
        return sb.toString();
    }

    private String indent() {
        return " ".repeat(indentLevel * INDENT_SIZE);
    }

    private String formatBody(List<Node> body) {
        return formatBody(body, -1, -1);
    }

    private String formatBody(List<Node> body, int bodyOpenLine) {
        return formatBody(body, bodyOpenLine, -1);
    }

    private String formatBody(List<Node> body, int bodyOpenLine, int bodyCloseLine) {
        if (body == null || body.isEmpty()) {
            if (bodyOpenLine > 0 && bodyCloseLine > bodyOpenLine) {
                StringBuilder empty = new StringBuilder();
                appendComments(empty, bodyOpenLine + 1, bodyCloseLine - 1, "");
                if (!empty.isEmpty()) {
                    indentLevel++;
                    String indented = empty.toString().lines()
                            .map(line -> indent() + line)
                            .collect(Collectors.joining("\n", "", "\n"));
                    indentLevel--;
                    return "{\n" + indented + indent() + "}";
                }
            }
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{\n");
        indentLevel++;
        int prevEndLine = -1;
        for (Node node : body) {
            String formatted = formatNode(node);
            if (formatted != null && !formatted.isBlank()) {
                int currStartLine = nodeStartLine(node);
                int currEndLine = (node instanceof Statement s && s.endLine > 0) ? s.endLine : currStartLine;
                if (currStartLine > 0) {
                    // For the first node, use bodyOpenLine as the range start if known.
                    int rangeStart = prevEndLine >= 0 ? prevEndLine + 1
                            : (bodyOpenLine >= 0 ? bodyOpenLine + 1 : -1);
                    if (rangeStart >= 0) {
                        int firstCommentLine = Integer.MAX_VALUE;
                        for (int cl = rangeStart; cl < currStartLine; cl++) {
                            if (!standaloneComments.getOrDefault(cl, List.of()).isEmpty()) {
                                firstCommentLine = cl;
                                break;
                            }
                        }
                        if (firstCommentLine < Integer.MAX_VALUE) {
                            if (prevEndLine >= 0 && firstCommentLine - prevEndLine >= 2) {
                                sb.append("\n");
                            }
                            appendComments(sb, rangeStart, currStartLine - 1, indent());
                        } else if (prevEndLine >= 0 && currStartLine - prevEndLine >= 2) {
                            sb.append("\n");
                        }
                    } else if (prevEndLine >= 0 && currStartLine - prevEndLine >= 2) {
                        sb.append("\n");
                    }
                    // Block comments sitting on the same line as this node but before
                    // the code (e.g. `/* comment */ return x;`) are stored as standalone.
                    // Emit them on their own line before the node.
                    for (String comment : standaloneComments.getOrDefault(currStartLine, List.of())) {
                        sb.append(indent()).append(comment).append("\n");
                    }
                }
                if (formatted.contains("\n") && currEndLine > currStartLine) {
                    String startCom = currStartLine > 0 ? inlineComments.get(currStartLine) : null;
                    if (startCom != null) {
                        int firstNl = formatted.indexOf('\n');
                        formatted = formatted.substring(0, firstNl) + " " + startCom + formatted.substring(firstNl);
                    }
                    String endCom = inlineComments.get(currEndLine);
                    if (endCom != null) {
                        formatted = formatted + " " + endCom;
                    }
                } else {
                    String inlineCom = currStartLine > 0 ? inlineComments.get(currStartLine) : null;
                    if (inlineCom != null) {
                        formatted = formatted + " " + inlineCom;
                    }
                }
                sb.append(indent()).append(formatted).append("\n");
                prevEndLine = currEndLine;
            }
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    private static int nodeStartLine(Node node) {
        if (node instanceof Statement s) {
            return s.line;
        }
        if (node instanceof Expression e) {
            return e.line;
        }
        return -1;
    }

    private String formatParams(List<Parameter> params, String variadicParam) {
        List<String> parts = params.stream()
                .map(p -> p.hasDefault()
                ? p.name() + " : " + formatExpr(p.defaultValue())
                : p.name())
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        if (variadicParam != null) {
            parts.add("..." + variadicParam);
        }
        return String.join(", ", parts);
    }

    private String formatArgs(List<Expression> args) {
        return args.stream()
                .map(this::formatExpr)
                .collect(Collectors.joining(", "));
    }

    private String formatExpr(Expression expr) {
        if (expr == null) {
            return "";
        }
        if (expr instanceof ImportExpression imp) {
            return formatImport(imp);
        }
        return expr.accept(this);
    }

    @Override
    public String visitVarDecl(VarDecl stmt) {
        String pub = stmt.isPublic() ? "pub " : "";
        String prefix = stmt.isConst() ? "const" : "var";
        if (stmt.getInitializer() != null) {
            return pub + prefix + " " + stmt.getName() + " : " + formatExpr(stmt.getInitializer()) + ";";
        }
        return pub + prefix + " " + stmt.getName() + ";";
    }

    @Override
    public String visitFuncDecl(FuncDecl stmt) {
        StringBuilder sb = new StringBuilder();
        if (stmt.isPublic()) {
            sb.append("pub ");
        }
        if (stmt.isAsync()) {
            sb.append("async ");
        }
        if (stmt.isPure()) {
            sb.append("pure ");
        }
        sb.append("fn ").append(stmt.getName())
                .append("(").append(formatParams(stmt.getParameters(), stmt.getVariadicParam())).append(") ");
        sb.append(formatBody(stmt.getBody(), stmt.line, stmt.endLine));
        return sb.toString();
    }

    @Override
    public String visitReturn(Return stmt) {
        if (stmt.getValue() != null) {
            if (stmt.getValue() instanceof DumbExpression dumb && dumb.getLine() == -1) {
                return "return;";
            }
            return "return " + formatExpr(stmt.getValue()) + ";";
        }
        return "return;";
    }

    @Override
    public String visitAssign(Assign stmt) {
        if (stmt.getExpression() instanceof BinaryExpression bin) {
            String binOp = bin.getOperator().getLexeme();
            if (Vocabulary.COMPOUND_ASSIGNMENT_OPERATORS.contains(binOp + ":")
                    && formatExpr(bin.getLeft()).equals(formatExpr(stmt.getReference()))) {
                return formatExpr(stmt.getReference()) + " " + binOp + ": " + formatExpr(bin.getRight()) + ";";
            }
        }
        return formatExpr(stmt.getReference()) + " : " + formatExpr(stmt.getExpression()) + ";";
    }

    @Override
    public String visitIf(If stmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(formatExpr(stmt.getCondition())).append(") ");
        boolean hasElse = stmt.getElseBody() != null && !stmt.getElseBody().isEmpty();
        sb.append(formatBody(stmt.getThenBody(), stmt.line, hasElse ? -1 : stmt.endLine));
        if (hasElse) {
            sb.append(" else ");
            if (stmt.getElseBody().size() == 1 && stmt.getElseBody().get(0) instanceof If) {
                sb.append(visitIf((If) stmt.getElseBody().get(0)));
            } else {
                sb.append(formatBody(stmt.getElseBody(), -1, stmt.endLine));
            }
        }
        return sb.toString();
    }

    @Override
    public String visitLoop(Loop stmt) {
        return stmt.isForeach() ? formatForeachLoop(stmt) : formatForLoop(stmt);
    }

    private String formatForLoop(Loop stmt) {
        StringBuilder sb = new StringBuilder("for (");
        List<Node> varDecls = stmt.getVarDecls();
        if (!varDecls.isEmpty()) {
            String decls = varDecls.stream()
                    .map(n -> {
                        if (n instanceof VarDecl vd) {
                            return (vd.isConst() ? "const" : "var") + " " + vd.getName()
                                    + (vd.getInitializer() != null ? " : " + formatExpr(vd.getInitializer()) : "");
                        }
                        return formatNode(n);
                    })
                    .collect(Collectors.joining(", "));
            sb.append(decls);
        }
        sb.append("; ");
        sb.append(stmt.getCondition() != null ? formatExpr(stmt.getCondition()) : "");
        sb.append("; ");
        if (stmt.getPostExpressions() != null && !stmt.getPostExpressions().isEmpty()) {
            String post = stmt.getPostExpressions().stream()
                    .map(this::formatNode)
                    .map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1) : s)
                    .collect(Collectors.joining(", "));
            sb.append(post);
        }
        sb.append(") ");
        sb.append(formatBody(stmt.getBody(), stmt.line, stmt.endLine));
        return sb.toString();
    }

    private String formatForeachLoop(Loop stmt) {
        VarDecl iter = stmt.getIterator();
        String iterName = iter.getName();
        if ("_".equals(iterName) && iter.getInitializer() == null) {
            return "for (" + formatExpr(stmt.getCollection()) + ") " + formatBody(stmt.getBody(), stmt.line, stmt.endLine);
        }
        return "for (var " + iterName + " in " + formatExpr(stmt.getCollection()) + ") "
                + formatBody(stmt.getBody(), stmt.line, stmt.endLine);
    }

    @Override
    public String visitWhile(While stmt) {
        if (stmt.getDoModifier()) {
            return "do " + formatBody(stmt.getBody(), stmt.line, stmt.endLine) + " while (" + formatExpr(stmt.getCondition()) + ");";
        }
        return "while (" + formatExpr(stmt.getCondition()) + ") " + formatBody(stmt.getBody(), stmt.line, stmt.endLine);
    }

    @Override
    public String visitBreak(Break stmt) {
        return "break;";
    }

    @Override
    public String visitContinue(Continue stmt) {
        return "continue;";
    }

    @Override
    public String visitBlock(Block stmt) {
        return formatBody(stmt.getBody(), stmt.line, stmt.endLine);
    }

    @Override
    public String visitSwitch(Switch stmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (").append(formatExpr(stmt.getSubject())).append(") {\n");
        indentLevel++;
        for (SwitchCase sc : stmt.getCases()) {
            sb.append(indent()).append("case (").append(formatExpr(sc.getValue())).append(") ");
            sb.append(formatBody(sc.getBody())).append("\n");
        }
        if (stmt.getDefaultBody() != null && !stmt.getDefaultBody().isEmpty()) {
            sb.append(indent()).append("default ").append(formatBody(stmt.getDefaultBody())).append("\n");
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitEnum(EnumDecl stmt) {
        String pub = stmt.isPublic() ? "pub " : "";
        StringBuilder sb = new StringBuilder(pub + "enum ").append(stmt.getIdentifier()).append(" {\n");
        indentLevel++;
        List<Map.Entry<String, Expression>> entries = List.copyOf(stmt.getValues().entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Expression> entry = entries.get(i);
            sb.append(indent()).append(entry.getKey());
            Expression value = entry.getValue();
            boolean isAutoIndexed = value instanceof DumbExpression d && d.getLine() == 0 && d.getColumn() == 0;
            if (!isAutoIndexed) {
                sb.append(" : ").append(formatExpr(value));
            }
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitThrow(Throw stmt) {
        return "throw " + formatExpr(stmt.getValue()) + ";";
    }

    @Override
    public String visitTryCatch(TryCatch stmt) {
        StringBuilder sb = new StringBuilder("try ");
        sb.append(formatBody(stmt.getTryBody(), stmt.line));
        for (var clause : stmt.getCatchClauses()) {
            sb.append(" catch (");
            if (clause.getTypeFilter() != null) {
                sb.append(clause.getTypeFilter());
                if (clause.getParamName() != null) {
                    sb.append(" ").append(clause.getParamName());
                }
            } else if (clause.getParamName() != null) {
                sb.append(clause.getParamName());
            }
            sb.append(") ");
            sb.append(formatBody(clause.getBody()));
        }
        if (stmt.getFinallyBody() != null && !stmt.getFinallyBody().isEmpty()) {
            sb.append(" finally ").append(formatBody(stmt.getFinallyBody()));
        }
        return sb.toString();
    }

    @Override
    public String visitVarDestructure(VarDestructure stmt) {
        String names = "(" + String.join(", ", stmt.getNames()) + ")";
        if (stmt.getInitializer() == null) {
            return "var " + names + ";";
        }
        return "var " + names + " : " + formatExpr(stmt.getInitializer()) + ";";
    }

    @Override
    public String visitLock(Lock stmt) {
        return "lock (" + formatExpr(stmt.getMutex()) + ") " + formatBody(stmt.getBody(), stmt.line, stmt.endLine);
    }

    @Override
    public String visitComptimeBlock(ComptimeBlock stmt) {
        return "comptime " + formatBody(stmt.getBody(), stmt.line, stmt.endLine);
    }

    @Override
    public String visitTestCall(TestCall stmt) {
        return "test(" + formatExpr(stmt.getName()) + ", " + formatExpr(stmt.getTestFn()) + ");";
    }

    @Override
    public String visitStaticAssert(StaticAssert stmt) {
        if (stmt.getMessage() != null) {
            return "static_assert(" + formatExpr(stmt.getCondition()) + ", " + formatExpr(stmt.getMessage()) + ");";
        }
        return "static_assert(" + formatExpr(stmt.getCondition()) + ");";
    }

    @Override
    public <T> T visitDumbExpr(DumbExpression expression) {
        if (expression.getTokenType() == TokenType.STRING_LITERAL) {
            return (T) formatStringLiteral(expression.getValue());
        }
        return (T) expression.getValue();
    }

    private static String formatStringLiteral(String content) {
        if (content.contains("\n")) {
            long nonBlankLines = java.util.Arrays.stream(content.split("\n", -1))
                    .filter(l -> !l.isBlank())
                    .count();
            if (nonBlankLines >= 2) {
                return "\"\"\"\n" + content + "\"\"\"";
            }
        }

        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    @Override
    public <T> T visitBinaryExpr(BinaryExpression expression) {
        String op = expression.getOperator().getLexeme();
        int outerPrec = Vocabulary.OPERATOR_PRECEDENCE.getOrDefault(op, 0);

        String left = formatExpr(expression.getLeft());
        if (expression.getLeft() instanceof BinaryExpression leftBin) {
            int innerPrec = Vocabulary.OPERATOR_PRECEDENCE.getOrDefault(leftBin.getOperator().getLexeme(), 0);
            if (innerPrec < outerPrec) {
                left = "(" + left + ")";
            }
        }
        if (op.equals("|>") && expression.getLeft() instanceof ComplexExpression) {
            left = "(" + left + ")";
        }

        String right = formatExpr(expression.getRight());
        if (expression.getRight() instanceof BinaryExpression rightBin) {
            int rightPrec = Vocabulary.OPERATOR_PRECEDENCE.getOrDefault(rightBin.getOperator().getLexeme(), 0);
            boolean needsParens = rightPrec < outerPrec
                    || (rightPrec == outerPrec && (op.equals("-") || op.equals("/") || op.equals("%") || op.equals("\\%")));
            if (needsParens) {
                right = "(" + right + ")";
            }
        }

        return (T) (left + " " + op + " " + right);
    }

    @Override
    public <T> T visitUnaryExpr(UnaryExpression expression) {
        String op = expression.getOperation().getLexeme();
        if (expression.getRight() != null) {
            String right = formatExpr(expression.getRight());
            if (op.equals("++") || op.equals("--")) {
                return (T) (expression.isPrefix() ? op + right : right + op);
            }
            return (T) (op + right);
        }
        return (T) op;
    }

    @Override
    public <T> T visitAssignExpression(AssignExpression expression) {
        return (T) (formatExpr(expression.getReference()) + " : " + formatExpr(expression.getValue()));
    }

    @Override
    public <T> T visitComplexExpr(ComplexExpression expression) {
        return (T) expression.getExpressions().stream()
                .map(this::formatExpr)
                .collect(Collectors.joining(" "));
    }

    @Override
    public <T> T visitCallExpr(CallExpression expression) {
        return (T) (formatExpr(expression.getCallee()) + "(" + formatArgs(expression.getArguments()) + ")");
    }

    @Override
    public <T> T visitArrayExpr(ArrayExpression expression) {
        String members = expression.getMembers().stream()
                .map(this::formatExpr)
                .collect(Collectors.joining(", "));
        return (T) ("[" + members + "]");
    }

    @Override
    public <T> T visitAccessExpr(AccessExpression expression) {
        String indices = expression.getIndecies().stream()
                .map(this::formatExpr)
                .collect(Collectors.joining("]["));
        return (T) (formatExpr(expression.getReference()) + "[" + indices + "]");
    }

    @Override
    public <T> T visitListExpr(ListExpression expression) {
        String members = expression.getMembers().stream()
                .map(this::formatExpr)
                .collect(Collectors.joining(", "));
        return (T) ("{" + members + "}");
    }

    @Override
    public <T> T visitMapExpr(MapExpression expression) {
        String entries = expression.getEntries().entrySet().stream()
                .map(e -> formatStringLiteral(e.getKey()) + " : " + formatExpr(e.getValue()))
                .collect(Collectors.joining(", "));
        return (T) ("{" + entries + "}");
    }

    @Override
    public <T> T visitNamespaceCallExpr(NamespaceCallExpression expression) {
        return (T) (expression.getAlias() + "." + expression.getFunctionName()
                + "(" + formatArgs(expression.getArguments()) + ")");
    }

    @Override
    public <T> T visitRangeExpression(RangeExpression expression) {
        String start = formatExpr(expression.getStart());
        String end = formatExpr(expression.getEnd());
        if (expression.getStepsize() != null) {
            return (T) ("<" + start + ".." + end + ", " + formatExpr(expression.getStepsize()) + ">");
        }
        return (T) ("<" + start + ".." + end + ">");
    }

    @Override
    public <T> T visitObjectExpression(ObjectExpression expression) {
        StringBuilder sb = new StringBuilder("{\n");
        indentLevel++;
        for (VarDecl vd : expression.getVarDecls()) {
            sb.append(indent()).append(visitVarDecl(vd)).append("\n");
        }
        for (FuncDecl fd : expression.getMethods()) {
            sb.append(indent()).append(visitFuncDecl(fd)).append("\n");
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return (T) sb.toString();
    }

    @Override
    public <T> T visitStructExpression(StructExpression expression) {
        StringBuilder sb = new StringBuilder("struct {\n");
        indentLevel++;
        for (VarDecl vd : expression.getVarDecls()) {
            sb.append(indent()).append(visitVarDecl(vd)).append("\n");
        }
        for (FuncDecl fd : expression.getMethods()) {
            sb.append(indent()).append(visitFuncDecl(fd)).append("\n");
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return (T) sb.toString();
    }

    @Override
    public <T> T visitStructInitExpression(StructInitExpression expression) {
        String overrides = expression.getOverrides().entrySet().stream()
                .map(e -> "$" + e.getKey() + " : " + formatExpr(e.getValue()))
                .collect(Collectors.joining(", "));
        return (T) (formatExpr(expression.getTarget()) + "{" + overrides + "}");
    }

    @Override
    public <T> T visitFieldAccessExpression(FieldAccessExpression expression) {
        String dot = expression.isOptional() ? "?." : ".";
        return (T) (formatExpr(expression.getObject()) + dot + expression.getField());
    }

    @Override
    public <T> T visitMethodCallExpression(MethodCallExpression expression) {
        String dot = expression.isOptional() ? "?." : ".";
        return (T) (formatExpr(expression.getObject()) + dot + expression.getMethod()
                + "(" + formatArgs(expression.getArguments()) + ")");
    }

    @Override
    public <T> T visitLambdaExpr(LambdaExpression expression) {
        List<Node> body = expression.getBody();
        String params = formatParams(expression.getParameters(), expression.getVariadicParam());

        if (expression.isArrow() && body.size() == 1 && body.get(0) instanceof Return ret && ret.getValue() != null) {
            return (T) ("(" + params + ") -> " + formatExpr(ret.getValue()));
        }

        StringBuilder sb = new StringBuilder();
        if (expression.isAsync()) {
            sb.append("async ");
        }
        sb.append("fn (").append(params).append(") ");
        sb.append(formatBody(body, expression.line));
        return (T) sb.toString();
    }

    @Override
    public <T> T visitTernaryExpr(TernaryExpression expression) {
        String condition = formatExpr(expression.getCondition());
        if (expression.getCondition() instanceof TernaryExpression) {
            // a ternary can only appear in condition position if the original
            // source parenthesized it (see Parser.parsePratt's `?` handling) -
            // dropping the parens here would silently change what re-parses.
            condition = "(" + condition + ")";
        }
        return (T) (condition
                + " ? " + formatExpr(expression.getThenExpr())
                + " : " + formatExpr(expression.getElseExpr()));
    }

    @Override
    public <T> T visitThrownException(ThrownException expression) {
        return (T) (expression.getIdentifier() + "(" + formatExpr(expression.getValue()) + ")");
    }

    @Override
    public <T> T visitAwaitExpr(AwaitExpression expression) {
        return (T) ("await " + formatExpr(expression.getExpr()));
    }

    @Override
    public <T> T visitSwitchExpr(SwitchExpression expression) {
        StringBuilder sb = new StringBuilder("switch (");
        sb.append(formatExpr(expression.getSubject())).append(") {\n");
        indentLevel++;
        for (SwitchExpression.SwitchExprCase sc : expression.getCases()) {
            sb.append(indent())
                    .append("case (").append(formatExpr(sc.value())).append(") -> ")
                    .append(formatExpr(sc.result())).append("\n");
        }
        if (expression.getDefaultExpr() != null) {
            sb.append(indent()).append("default -> ").append(formatExpr(expression.getDefaultExpr())).append("\n");
        }
        indentLevel--;
        sb.append(indent()).append("}");
        return (T) sb.toString();
    }

    @Override
    public <T> T visitTypeofExpr(TypeofExpression expression) {
        return (T) ("typeof " + formatExpr(expression.getExpr()));
    }

    @Override
    public <T> T visitExecBlock(ExecBlock expression) {
        String keyword = expression.isIsolated() ? "exec isolated " : "exec ";
        return (T) (keyword + formatBody(expression.getBody(), expression.line));
    }
}
