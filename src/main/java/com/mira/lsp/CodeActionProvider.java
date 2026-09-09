package com.mira.lsp;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.mira.format.AstWalker;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;

public class CodeActionProvider {

    private static final Pattern UNUSED_PATTERN = Pattern
            .compile("^'([^']+)' is (?:imported but never used|declared but never used|defined but never called)$");
    private static final Pattern CONST_REASSIGN_PATTERN = Pattern.compile("^Cannot reassign constant '([^']+)'$");
    private static final Pattern CONST_MODIFY_PATTERN = Pattern
            .compile("^Cannot modify element of constant '([^']+)'$");
    private static final Pattern PRIVATE_ACCESS_PATTERN = Pattern.compile("^'([^']+)' is private in module '([^']+)'$");
    private static final Pattern PRIVATE_IMPORT_PATTERN = Pattern
            .compile("^Cannot import private symbol '([^']+)' from module '([^']+)'$");
    private static final Pattern UNDECLARED_VAR_PATTERN = Pattern
            .compile("^Variable '([^']+)' is used but never declared$");
    private static final Pattern UNDEFINED_FUNCTION_PATTERN = Pattern
            .compile("^Function '([^']+)' is called but never defined$");

    private static String codeOf(Diagnostic d) {
        var code = d.getCode();
        return code != null && code.isLeft() ? code.getLeft() : null;
    }

    private static String firstLine(Diagnostic d) {
        int nl = d.getMessage().indexOf('\n');
        return nl < 0 ? d.getMessage() : d.getMessage().substring(0, nl);
    }

    public static List<Either<Command, CodeAction>> provide(CodeActionParams params, List<Node> ast, String uri,
            String content, Path docPath, WorkspaceIndex workspaceIndex, Path workspaceRoot,
            Map<String, String> openDocumentsByUri) {
        List<Either<Command, CodeAction>> actions = new ArrayList<>();
        if (params.getContext() == null) {
            return actions;
        }
        for (Diagnostic d : params.getContext().getDiagnostics()) {
            CodeAction action = buildUnusedSymbolFix(d, ast, uri, content);
            if (action == null) {
                action = buildConstToVarFix(d, ast, uri, content);
            }
            if (action == null) {
                action = buildMissingModuleDeclFix(d, uri);
            }
            if (action == null) {
                action = buildMarkPubFix(d, docPath, workspaceIndex, workspaceRoot, openDocumentsByUri);
            }
            if (action == null) {
                action = buildDeclareVarFix(d, uri, content);
            }
            if (action != null) {
                actions.add(Either.forRight(action));
            }
            for (CodeAction importFix : buildAddMissingImportFix(d, uri, ast, docPath, workspaceIndex, workspaceRoot,
                    openDocumentsByUri)) {
                actions.add(Either.forRight(importFix));
            }
        }
        return actions;
    }

    private static List<CodeAction> buildAddMissingImportFix(Diagnostic d, String uri, List<Node> ast, Path docPath,
            WorkspaceIndex workspaceIndex, Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        if (!"E302".equals(codeOf(d)) || docPath == null || workspaceIndex == null || workspaceRoot == null) {
            return List.of();
        }
        Matcher m = UNDEFINED_FUNCTION_PATTERN.matcher(firstLine(d));
        if (!m.matches() || alreadyImportsName(ast, m.group(1))) {
            return List.of();
        }
        String name = m.group(1);
        int insertLine = importInsertionLine(ast);

        List<CodeAction> actions = new ArrayList<>();
        for (Path candidate : workspaceIndex.allMiraFiles(workspaceRoot)) {
            if (candidate.equals(docPath)
                    || !hasPublicFunction(workspaceIndex.getAst(candidate, openDocumentsByUri), name)) {
                continue;
            }
            String importPath = relativeImportPath(docPath, candidate);
            TextEdit edit = new TextEdit(new Range(new Position(insertLine, 0), new Position(insertLine, 0)),
                    "import module \"" + importPath + "\" {" + name + "};\n");
            WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(uri, List.of(edit)));

            String fileLabel = candidate.getFileName() != null
                    ? candidate.getFileName().toString()
                    : candidate.toString();
            CodeAction action = new CodeAction("Import '" + name + "' from '" + fileLabel + "'");
            action.setKind(CodeActionKind.QuickFix);
            action.setDiagnostics(List.of(d));
            action.setEdit(workspaceEdit);
            actions.add(action);
        }
        return actions;
    }

    private static boolean alreadyImportsName(List<Node> ast, String name) {
        for (Node n : ast) {
            if (n instanceof ImportExpression imp && imp.isSelective() && imp.getSelectedFunctions() != null
                    && imp.getSelectedFunctions().contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPublicFunction(List<Node> ast, String name) {
        for (Node n : ast) {
            if (n instanceof FuncDecl f && f.isPublic() && f.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static int importInsertionLine(List<Node> ast) {
        int lastImportLine = 0;
        int moduleDeclLine = 0;
        for (Node n : ast) {
            if (n instanceof ImportExpression imp) {
                lastImportLine = Math.max(lastImportLine, imp.line);
            } else if (n instanceof ModuleDecl md) {
                moduleDeclLine = md.line;
            }
        }
        return lastImportLine > 0 ? lastImportLine : moduleDeclLine;
    }

    private static String relativeImportPath(Path docPath, Path target) {
        Path fromDir = docPath.getParent();
        if (fromDir == null) {
            return target.getFileName() != null ? target.getFileName().toString() : target.toString();
        }
        return fromDir.relativize(target).toString().replace('\\', '/');
    }

    private static CodeAction buildUnusedSymbolFix(Diagnostic d, List<Node> ast, String uri, String content) {
        Matcher m = UNUSED_PATTERN.matcher(d.getMessage());
        if (!m.matches()) {
            return null;
        }
        String name = m.group(1);
        String[] lines = content.split("\n", -1);
        int line = d.getRange().getStart().getLine();
        if (line < 0 || line >= lines.length) {
            return null;
        }

        TextEdit edit = buildSurgicalRemoval(ast, name, line, lines[line]);
        if (edit == null) {
            Range deleteRange = new Range(new Position(line, 0), new Position(line + 1, 0));
            edit = new TextEdit(deleteRange, "");
        }
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(uri, List.of(edit)));

        CodeAction action = new CodeAction("Remove unused '" + name + "'");
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(d));
        action.setEdit(workspaceEdit);
        return action;
    }

    /**
     * When {@code name}'s declaration shares its source line with other still-
     * relevant names - a comma-separated {@code var a, b;}, a destructuring
     * {@code var (a, b) : expr;}, or a selective {@code import ... {a, b};} -
     * deleting the whole line (the default fix) would silently remove those other,
     * still-used bindings too. In that case, edit out just {@code name} instead.
     * Returns null when the declaration is alone on its line, meaning the
     * whole-line delete is safe.
     */
    private static TextEdit buildSurgicalRemoval(List<Node> ast, String name, int lineIdx, String lineText) {
        int declLine = lineIdx + 1;
        Deque<Node> queue = new ArrayDeque<>(ast);
        int siblingVarDecls = 0;
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof VarDestructure vdx && vdx.line == declLine && vdx.getNames().contains(name)) {
                if (vdx.getNames().size() <= 1) {
                    return null;
                }
                return renameWordToUnderscore(lineIdx, lineText, name);
            }
            if (n instanceof ImportExpression imp && imp.line == declLine && imp.isSelective()
                    && imp.getSelectedFunctions().contains(name)) {
                if (imp.getSelectedFunctions().size() <= 1) {
                    return null;
                }
                return removeFromSelectiveImport(lineIdx, lineText, name);
            }
            if (n instanceof VarDecl vd && vd.line == declLine) {
                siblingVarDecls++;
            }
            AstWalker.children(n, queue);
        }
        return siblingVarDecls > 1 ? renameWordToUnderscore(lineIdx, lineText, name) : null;
    }

    private static TextEdit renameWordToUnderscore(int lineIdx, String lineText, String name) {
        Matcher wm = Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(lineText);
        if (!wm.find()) {
            return null;
        }
        Range range = new Range(new Position(lineIdx, wm.start()), new Position(lineIdx, wm.end()));
        return new TextEdit(range, "_");
    }

    private static TextEdit removeFromSelectiveImport(int lineIdx, String lineText, String name) {
        int braceStart = lineText.indexOf('{');
        int braceEnd = braceStart < 0 ? -1 : lineText.indexOf('}', braceStart + 1);
        if (braceStart < 0 || braceEnd < 0) {
            return null;
        }
        String inner = lineText.substring(braceStart + 1, braceEnd);
        List<String> parts = new ArrayList<>();
        for (String part : inner.split(",")) {
            if (!part.trim().equals(name)) {
                parts.add(part.trim());
            }
        }
        String replacement = "{" + String.join(", ", parts) + "}";
        Range range = new Range(new Position(lineIdx, braceStart), new Position(lineIdx, braceEnd + 1));
        return new TextEdit(range, replacement);
    }

    private static CodeAction buildConstToVarFix(Diagnostic d, List<Node> ast, String uri, String content) {
        String code = codeOf(d);
        if (!"E304".equals(code) && !"E321".equals(code)) {
            return null;
        }
        String firstLine = firstLine(d);
        Matcher m = CONST_REASSIGN_PATTERN.matcher(firstLine);
        if (!m.matches()) {
            m = CONST_MODIFY_PATTERN.matcher(firstLine);
            if (!m.matches()) {
                return null;
            }
        }
        String name = m.group(1);
        VarDecl decl = findConstDecl(ast, name);
        if (decl == null) {
            return null;
        }

        String[] lines = content.split("\n", -1);
        int lineIdx = Math.max(decl.line - 1, 0);
        if (lineIdx >= lines.length) {
            return null;
        }
        String lineText = lines[lineIdx];
        int nameCol = decl.nameColumn > 0 ? decl.nameColumn - 1 : lineText.indexOf(name);
        int constIdx = lineText.lastIndexOf("const", Math.max(nameCol, 0));
        if (constIdx < 0) {
            return null;
        }

        Range range = new Range(new Position(lineIdx, constIdx), new Position(lineIdx, constIdx + "const".length()));
        TextEdit edit = new TextEdit(range, "var");
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(uri, List.of(edit)));

        CodeAction action = new CodeAction("Change 'const " + name + "' to 'var'");
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(d));
        action.setEdit(workspaceEdit);
        return action;
    }

    private static VarDecl findConstDecl(List<Node> ast, String name) {
        Deque<Node> queue = new ArrayDeque<>(ast);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n == null) {
                continue;
            }
            if (n instanceof VarDecl v && v.isConst() && v.getName().equals(name)) {
                return v;
            }
            AstWalker.children(n, queue);
        }
        return null;
    }

    private static CodeAction buildMissingModuleDeclFix(Diagnostic d, String uri) {
        if (!"E309".equals(codeOf(d))) {
            return null;
        }
        String name = deriveModuleName(uri);
        TextEdit edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "module " + name + ";\n");
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(uri, List.of(edit)));

        CodeAction action = new CodeAction("Add 'module " + name + ";' declaration");
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(d));
        action.setEdit(workspaceEdit);
        return action;
    }

    private static String deriveModuleName(String uri) {
        Path path = DocumentService.uriToPath(uri);
        if (path == null || path.getFileName() == null) {
            return "main";
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.isBlank() ? "main" : base;
    }

    private static CodeAction buildMarkPubFix(Diagnostic d, Path docPath, WorkspaceIndex workspaceIndex,
            Path workspaceRoot, Map<String, String> openDocumentsByUri) {
        if (!"E318".equals(codeOf(d)) || docPath == null || workspaceIndex == null) {
            return null;
        }
        Matcher m = PRIVATE_ACCESS_PATTERN.matcher(firstLine(d));
        if (!m.matches()) {
            m = PRIVATE_IMPORT_PATTERN.matcher(firstLine(d));
            if (!m.matches()) {
                return null;
            }
        }
        String symbol = m.group(1);
        String moduleFileName = m.group(2);

        Path target = resolveModuleFile(docPath, moduleFileName, workspaceIndex, workspaceRoot);
        if (target == null) {
            return null;
        }

        List<Node> targetAst = workspaceIndex.getAst(target, openDocumentsByUri);
        String targetContent = workspaceIndex.getSource(target, openDocumentsByUri);
        int declLine = findTopLevelDeclLine(targetAst, symbol);
        if (declLine < 0) {
            return null;
        }

        String[] lines = targetContent.split("\n", -1);
        int lineIdx = Math.max(declLine - 1, 0);
        if (lineIdx >= lines.length) {
            return null;
        }
        String lineText = lines[lineIdx];
        int firstNonWs = 0;
        while (firstNonWs < lineText.length() && Character.isWhitespace(lineText.charAt(firstNonWs))) {
            firstNonWs++;
        }

        String targetUri = target.toUri().toString();
        TextEdit edit = new TextEdit(new Range(new Position(lineIdx, firstNonWs), new Position(lineIdx, firstNonWs)),
                "pub ");
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(targetUri, List.of(edit)));

        CodeAction action = new CodeAction("Mark '" + symbol + "' as 'pub' in " + moduleFileName);
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(d));
        action.setEdit(workspaceEdit);
        return action;
    }

    private static Path resolveModuleFile(Path docPath, String moduleFileName, WorkspaceIndex workspaceIndex,
            Path workspaceRoot) {
        Path sameDir = docPath.getParent() != null ? docPath.getParent().resolve(moduleFileName) : null;
        if (sameDir != null && java.nio.file.Files.exists(sameDir)) {
            return sameDir;
        }
        if (workspaceRoot == null) {
            return null;
        }
        for (Path candidate : workspaceIndex.allMiraFiles(workspaceRoot)) {
            if (candidate.getFileName() != null && candidate.getFileName().toString().equals(moduleFileName)) {
                return candidate;
            }
        }
        return null;
    }

    private static int findTopLevelDeclLine(List<Node> ast, String name) {
        for (Node n : ast) {
            if (n instanceof FuncDecl f && f.getName().equals(name)) {
                return f.line;
            }
            if (n instanceof VarDecl v && v.getName().equals(name)) {
                return v.line;
            }
        }
        return -1;
    }

    private static CodeAction buildDeclareVarFix(Diagnostic d, String uri, String content) {
        Matcher m = UNDECLARED_VAR_PATTERN.matcher(firstLine(d));
        if (!"E301".equals(codeOf(d)) || !m.matches()) {
            return null;
        }
        String name = m.group(1);
        String[] lines = content.split("\n", -1);
        int line = d.getRange().getStart().getLine();
        if (line < 0 || line >= lines.length) {
            return null;
        }
        String lineText = lines[line];
        int indentEnd = 0;
        while (indentEnd < lineText.length() && Character.isWhitespace(lineText.charAt(indentEnd))) {
            indentEnd++;
        }
        String indent = lineText.substring(0, indentEnd);

        TextEdit edit = new TextEdit(new Range(new Position(line, 0), new Position(line, 0)),
                indent + "var " + name + ";\n");
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Map.of(uri, List.of(edit)));

        CodeAction action = new CodeAction("Declare 'var " + name + ";'");
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(List.of(d));
        action.setEdit(workspaceEdit);
        return action;
    }
}
