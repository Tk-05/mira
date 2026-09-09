package com.mira.lsp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.mira.lib.NativeInterfaceManifest;
import com.mira.lib.NativeInterfaceManifest.Signature;
import com.mira.lib.NativeLibLocator;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.utils.ModuleResolver;

public class SignatureHelpProvider {

    private static final Pattern STDLIB_PARAM_PATTERN = Pattern.compile("\\*\\*[\\w.]+\\(([^)]*)\\)\\*\\*");

    public static SignatureHelp provide(List<Node> ast, String content, Position pos, Path docPath,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        int offset = toOffset(content, pos);
        CallContext ctx = findCallContext(content, offset);
        if (ctx == null || ctx.callTarget().isEmpty()) {
            return emptyHelp();
        }

        int dot = ctx.callTarget().lastIndexOf('.');
        String funcName;
        List<String> paramNames;
        int cursorLine = pos.getLine() + 1;

        if (dot > 0) {
            String receiver = ctx.callTarget().substring(0, dot);
            funcName = ctx.callTarget().substring(dot + 1);
            paramNames = resolveMethodParams(ast, receiver, funcName, docPath, workspaceIndex, openDocumentsByUri,
                    cursorLine);
        } else {
            funcName = ctx.callTarget();
            FuncDecl f = findTopLevelFunc(ast, funcName);
            if (f != null) {
                paramNames = paramNamesOf(f);
            } else if (docPath != null) {
                paramNames = resolveDirectBoundFuncParams(ast, funcName, docPath, workspaceIndex, openDocumentsByUri);
            } else {
                paramNames = null;
            }
        }

        if (paramNames == null) {
            paramNames = stdlibParams(funcName);
        }
        if (paramNames == null) {
            return emptyHelp();
        }
        return buildHelp(funcName, paramNames, ctx.commaCount());
    }

    private static List<String> paramNamesOf(FuncDecl f) {
        List<String> names = new ArrayList<>();
        for (Parameter p : f.getParameters()) {
            names.add(p.name() + (p.type() != null ? " : " + p.type() : ""));
        }
        return names;
    }

    private static FuncDecl findTopLevelFunc(List<Node> ast, String name) {
        for (Node n : ast) {
            if (n instanceof FuncDecl f && f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static List<String> resolveMethodParams(List<Node> ast, String receiver, String member, Path docPath,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri, int cursorLine) {
        Node type = DefinitionProvider.resolveObjectType(ast, receiver, cursorLine);
        if (type != null) {
            FuncDecl m = DefinitionProvider.findMethodInType(type, member);
            if (m != null) {
                return paramNamesOf(m);
            }
        }
        if (docPath == null) {
            return null;
        }
        for (Node n : ast) {
            if (!(n instanceof ImportExpression imp) || !receiver.equals(imp.getNamespace())) {
                continue;
            }
            if (imp.getKind() == ImportExpression.ImportKind.MODULE) {
                Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), docPath);
                List<Node> modAst = workspaceIndex != null
                        ? workspaceIndex.getAst(modPath, openDocumentsByUri)
                        : parseFile(modPath);
                FuncDecl f = findTopLevelFunc(modAst, member);
                if (f != null) {
                    return paramNamesOf(f);
                }
            } else if (imp.isNativeJar()) {
                List<String> params = resolveNativeParams(imp, member, docPath);
                if (params != null) {
                    return params;
                }
            }
        }
        return null;
    }

    /**
     * Reads the declared signature for a native lib call straight out of its jar's
     * classloading-free manifest (see {@link NativeInterfaceManifest}) - never
     * loads the jar's actual Java classes just to show a signature hint.
     */
    private static List<String> resolveNativeParams(ImportExpression imp, String member, Path docPath) {
        String rawPath = imp.getModule().replace("\"", "");
        Path jarPath = NativeLibLocator.locate(rawPath, docPath);
        if (jarPath == null) {
            return null;
        }
        Signature sig = NativeInterfaceManifest.readFromJar(jarPath).get(member);
        if (sig == null) {
            return null;
        }
        List<String> params = new ArrayList<>();
        for (int i = 0; i < sig.paramTypes().size(); i++) {
            params.add("#" + (i + 1) + " : " + sig.paramTypes().get(i));
        }
        return params;
    }

    /**
     * Looks up {@code funcName} among functions brought into scope bare (no
     * namespace prefix) by a selective, non-aliased module import - e.g.
     * {@code import module "lib.mira" {greet};} binds {@code greet} directly,
     * unlike an aliased import which only exposes {@code alias.greet}.
     */
    private static List<String> resolveDirectBoundFuncParams(List<Node> ast, String funcName, Path docPath,
            WorkspaceIndex workspaceIndex, Map<String, String> openDocumentsByUri) {
        for (Node n : ast) {
            if (!(n instanceof ImportExpression imp) || imp.getKind() != ImportExpression.ImportKind.MODULE) {
                continue;
            }
            if (imp.getNamespace() != null) {
                continue;
            }
            if (imp.isSelective() && !imp.getSelectedFunctions().contains(funcName)) {
                continue;
            }
            Path modPath = ModuleResolver.resolveModulePath(imp.getModule(), docPath);
            List<Node> modAst = workspaceIndex != null
                    ? workspaceIndex.getAst(modPath, openDocumentsByUri)
                    : parseFile(modPath);
            FuncDecl f = findTopLevelFunc(modAst, funcName);
            if (f != null) {
                return paramNamesOf(f);
            }
        }
        return null;
    }

    private static List<Node> parseFile(Path path) {
        try {
            String src = Files.readString(path);
            return new com.mira.parser.Parser().parseTokens(new com.mira.lexer.Tokenizer().tokenize(src, false));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> stdlibParams(String name) {
        String doc = HoverProvider.STDLIB_DOCS.get(name);
        if (doc == null) {
            return null;
        }
        Matcher m = STDLIB_PARAM_PATTERN.matcher(doc);
        if (!m.find()) {
            return List.of();
        }
        String params = m.group(1).trim();
        if (params.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(params.split(",")).map(String::trim).toList();
    }

    private static SignatureHelp buildHelp(String name, List<String> paramNames, int commaCount) {
        String label = name + "(" + String.join(", ", paramNames) + ")";
        SignatureInformation sig = new SignatureInformation(label);
        List<ParameterInformation> params = new ArrayList<>();
        for (String p : paramNames) {
            params.add(new ParameterInformation(p));
        }
        sig.setParameters(params);

        SignatureHelp help = new SignatureHelp();
        help.setSignatures(List.of(sig));
        help.setActiveSignature(0);
        help.setActiveParameter(paramNames.isEmpty() ? 0 : Math.min(commaCount, paramNames.size() - 1));
        return help;
    }

    private static SignatureHelp emptyHelp() {
        SignatureHelp help = new SignatureHelp();
        help.setSignatures(List.of());
        return help;
    }

    private record CallContext(String callTarget, int commaCount) {

    }

    private static CallContext findCallContext(String content, int offset) {
        boolean[] inString = computeInStringMask(content, offset);
        int depth = 0;
        int parenPos = -1;
        int commas = 0;
        for (int i = offset - 1; i >= 0; i--) {
            if (inString[i]) {
                continue;
            }
            char c = content.charAt(i);
            if (c == ')' || c == ']' || c == '}') {
                depth++;
            } else if (c == '(') {
                if (depth == 0) {
                    parenPos = i;
                    break;
                }
                depth--;
            } else if (c == '[' || c == '{') {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        if (parenPos < 0) {
            return null;
        }

        int idx = parenPos;
        while (idx > 0 && Character.isWhitespace(content.charAt(idx - 1))) {
            idx--;
        }
        int end = idx;
        int start = end;
        while (start > 0 && (isWordChar(content.charAt(start - 1)) || content.charAt(start - 1) == '.')) {
            start--;
        }
        return new CallContext(content.substring(start, end), commas);
    }

    private static boolean[] computeInStringMask(String content, int upTo) {
        boolean[] mask = new boolean[content.length()];
        boolean inString = false;
        char quoteChar = 0;
        int limit = Math.min(upTo, content.length());
        for (int i = 0; i < limit; i++) {
            char c = content.charAt(i);
            if (inString) {
                mask[i] = true;
                if (c == '\\') {
                    if (i + 1 < mask.length) {
                        mask[i + 1] = true;
                    }
                    i++;
                    continue;
                }
                if (c == quoteChar) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c;
                mask[i] = true;
            }
        }
        return mask;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int toOffset(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        int offset = 0;
        for (int i = 0; i < pos.getLine() && i < lines.length; i++) {
            offset += lines[i].length() + 1;
        }
        if (pos.getLine() < lines.length) {
            offset += Math.min(pos.getCharacter(), lines[pos.getLine()].length());
        }
        return offset;
    }
}
