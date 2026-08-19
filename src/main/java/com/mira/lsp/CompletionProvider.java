package com.mira.lsp;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.mira.lexer.Tokenizer;
import com.mira.lib.Lib;
import com.mira.lib.LibIndex;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.expression.Expression.StructInitExpression;
import com.mira.parser.nodes.expression.Expression.UnaryExpression;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Lock;
import com.mira.parser.nodes.statement.Statement.Loop;
import com.mira.parser.nodes.statement.Statement.Switch;
import com.mira.parser.nodes.statement.Statement.TryCatch;
import com.mira.parser.nodes.statement.Statement.TypeAliasDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.VarDestructure;
import com.mira.parser.nodes.statement.Statement.While;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.utils.ModuleResolver;

public class CompletionProvider {

    private static final List<String> KEYWORDS = List.of(
            "var", "const", "fn", "return", "if", "else", "while", "for",
            "in", "break", "continue", "switch", "case", "default", "do",
            "try", "catch", "finally", "throw", "import", "module", "as",
            "enum", "async", "await", "typeof", "spawn", "pure", "lock", "true", "false", "null",
            "exec", "exec isolated", "comptime", "static_assert", "pub", "struct"
    );

    private static final List<String> GLOBALS = List.copyOf(LibIndex.GLOBAL_NAMES);

    private static final List<String> BUILTIN_TYPE_NAMES = List.of(
            "Number", "String", "Bool", "List", "Array", "Map", "Object", "Fn", "Null", "Any");

    private static final Map<String, List<String>> STDLIB;
    private static final Map<String, String> STDLIB_PARAMS;

    static {
        Map<String, List<String>> stdlib = new LinkedHashMap<>();
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, Lib> entry : LibIndex.STDLIB_LIBS.entrySet()) {
            Environment env = new Environment();
            entry.getValue().loadLib(env);
            List<String> fns = new ArrayList<>(env.getDefinedNames());
            stdlib.put(entry.getKey(), fns);
            for (String fn : fns) {
                Object val = env.getOrNull(fn);
                if (val instanceof Callable c && !c.getParamHint().isEmpty()) {
                    params.putIfAbsent(fn, c.getParamHint());
                }
            }
        }
        STDLIB = Collections.unmodifiableMap(stdlib);
        STDLIB_PARAMS = Collections.unmodifiableMap(params);
    }

    public static List<CompletionItem> provide(List<Node> ast, String documentUri) {
        List<CompletionItem> items = new ArrayList<>();

        for (String kw : KEYWORDS) {
            CompletionItem item = new CompletionItem(kw);
            item.setKind(CompletionItemKind.Keyword);
            items.add(item);
        }

        for (String fn : GLOBALS) {
            CompletionItem item = new CompletionItem(fn);
            item.setKind(CompletionItemKind.Function);
            items.add(item);
        }

        for (String type : BUILTIN_TYPE_NAMES) {
            CompletionItem item = new CompletionItem(type);
            item.setKind(CompletionItemKind.Class);
            items.add(item);
        }

        collectFromNodes(ast, ast, items);
        collectFromImports(ast, documentUri, items);
        return items;
    }

    private static void collectFromNodes(List<Node> nodes, List<Node> rootAst, List<CompletionItem> items) {
        for (Node node : nodes) {
            switch (node) {
                case VarDecl v -> {
                    CompletionItem item = new CompletionItem(v.getName());
                    item.setKind(CompletionItemKind.Variable);
                    items.add(item);
                    if (v.getInitializer() instanceof ObjectExpression obj) {
                        for (VarDecl f : obj.getVarDecls()) {
                            CompletionItem fi = new CompletionItem(v.getName() + "." + f.getName());
                            fi.setKind(CompletionItemKind.Field);
                            fi.setDetail((f.isConst() ? "const" : "var") + " " + f.getName());
                            items.add(fi);
                        }
                        for (FuncDecl m : obj.getMethods()) {
                            String params = m.getParameters().stream()
                                    .map(Parameter::name).collect(Collectors.joining(", "));
                            CompletionItem mi = new CompletionItem(v.getName() + "." + m.getName());
                            mi.setKind(CompletionItemKind.Method);
                            mi.setDetail("fn " + m.getName() + "(" + params + ")");
                            items.add(mi);
                        }
                    } else if (v.getInitializer() instanceof StructExpression st) {
                        addStructMemberItems(v.getName(), st, items);
                    } else if (v.getInitializer() instanceof StructInitExpression si) {
                        String templateName = extractName(si.getTarget());
                        if (templateName != null && resolveStructTemplate(rootAst, templateName) instanceof StructExpression st) {
                            addStructMemberItems(v.getName(), st, items);
                        }
                    }
                }
                case FuncDecl f -> {
                    CompletionItem item = new CompletionItem(f.getName());
                    item.setKind(CompletionItemKind.Function);
                    String prefix = f.isPure() ? "pure fn " : "fn ";
                    item.setDetail(prefix + f.getName() + "("
                            + f.getParameters().stream()
                                    .map(Parameter::name)
                                    .collect(Collectors.joining(", ")) + ")");
                    items.add(item);
                    collectFromNodes(f.getBody(), rootAst, items);
                }
                case VarDestructure vd -> {
                    for (String n : vd.getNames()) {
                        CompletionItem item = new CompletionItem(n);
                        item.setKind(CompletionItemKind.Variable);
                        items.add(item);
                    }
                }
                case EnumDecl e -> {
                    CompletionItem item = new CompletionItem(e.getIdentifier());
                    item.setKind(CompletionItemKind.Class);
                    item.setDetail("enum " + e.getIdentifier());
                    items.add(item);
                }
                case TypeAliasDecl t -> {
                    CompletionItem item = new CompletionItem(t.getName());
                    item.setKind(CompletionItemKind.Class);
                    item.setDetail("type " + t.getName() + " : " + t.getAliasedType());
                    items.add(item);
                }
                case ComptimeBlock comptime -> {
                    for (Node bodyNode : comptime.getBody()) {
                        if (bodyNode instanceof VarDecl v) {
                            CompletionItem item = new CompletionItem(v.getName());
                            item.setKind(CompletionItemKind.Constant);
                            item.setDetail("comptime const " + v.getName());
                            items.add(item);
                        }
                    }
                }
                case If s -> {
                    collectFromNodes(s.getThenBody(), rootAst, items);
                    if (s.getElseBody() != null) {
                        collectFromNodes(s.getElseBody(), rootAst, items);
                    }
                }
                case Loop s -> {
                    if (s.isForeach()) {
                        CompletionItem item = new CompletionItem(s.getIterator().getName());
                        item.setKind(CompletionItemKind.Variable);
                        items.add(item);
                    } else {
                        collectFromNodes(s.getVarDecls(), rootAst, items);
                    }
                    collectFromNodes(s.getBody(), rootAst, items);
                }
                case While s ->
                    collectFromNodes(s.getBody(), rootAst, items);
                case Block s ->
                    collectFromNodes(s.getBody(), rootAst, items);
                case Switch s -> {
                    for (Switch.SwitchCase sc : s.getCases()) {
                        collectFromNodes(sc.getBody(), rootAst, items);
                    }
                    if (s.getDefaultBody() != null) {
                        collectFromNodes(s.getDefaultBody(), rootAst, items);
                    }
                }
                case TryCatch s -> {
                    collectFromNodes(s.getTryBody(), rootAst, items);
                    for (TryCatch.CatchClause cc : s.getCatchClauses()) {
                        collectFromNodes(cc.getBody(), rootAst, items);
                    }
                    if (s.getFinallyBody() != null) {
                        collectFromNodes(s.getFinallyBody(), rootAst, items);
                    }
                }
                case Lock s ->
                    collectFromNodes(s.getBody(), rootAst, items);
                default -> {
                }
            }
        }
    }

    private static void addStructMemberItems(String varName, StructExpression st, List<CompletionItem> items) {
        for (VarDecl f : st.getVarDecls()) {
            CompletionItem fi = new CompletionItem(varName + "." + f.getName());
            fi.setKind(CompletionItemKind.Field);
            fi.setDetail((f.isConst() ? "const" : "var") + " " + f.getName() + " (struct)");
            items.add(fi);
        }
        for (FuncDecl m : st.getMethods()) {
            String params = m.getParameters().stream()
                    .map(Parameter::name).collect(Collectors.joining(", "));
            CompletionItem mi = new CompletionItem(varName + "." + m.getName());
            mi.setKind(CompletionItemKind.Method);
            mi.setDetail("fn " + m.getName() + "(" + params + ") (struct)");
            items.add(mi);
        }
    }

    private static String extractName(Expression expr) {
        if (expr instanceof UnaryExpression u
                && "$".equals(u.getOperation().getLexeme())
                && u.getRight() instanceof DumbExpression d) {
            return d.getValue();
        }
        if (expr instanceof DumbExpression d) {
            return d.getValue();
        }
        return null;
    }

    private static Node resolveStructTemplate(List<Node> ast, String templateName) {
        for (Node n : ast) {
            if (n instanceof VarDecl vd && vd.getName().equals(templateName)) {
                return vd.getInitializer();
            }
            if (n instanceof FuncDecl f) {
                Node t = resolveStructTemplate(f.getBody(), templateName);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static void collectFromImports(List<Node> ast, String documentUri, List<CompletionItem> items) {
        Path docPath = null;
        if (documentUri != null) {
            try {
                docPath = Paths.get(new URI(documentUri));
            } catch (Exception ignored) {
            }
        }

        for (Node node : ast) {
            if (!(node instanceof Expression.ImportExpression imp)) {
                continue;
            }
            String alias = imp.getNamespace();

            switch (imp.getKind()) {
                case STDLIB -> {
                    String modName = imp.getModule().replace("\"", "");
                    List<String> fns = STDLIB.get(modName);
                    if (fns != null) {
                        for (String fn : fns) {
                            String params = STDLIB_PARAMS.getOrDefault(fn, "");
                            if (alias == null || alias.isBlank()) {
                                CompletionItem item = new CompletionItem(fn);
                                item.setKind(CompletionItemKind.Function);
                                item.setDetail("fn " + fn + "(" + params + ")");
                                items.add(item);
                            } else {
                                items.add(namespaceItem(alias, fn, params, false));
                            }
                        }
                    }
                }
                case MODULE -> {
                    if (docPath != null) {
                        Path modulePath = ModuleResolver.resolveModulePath(imp.getModule(), docPath);
                        List<String> selected = imp.isSelective() ? imp.getSelectedFunctions() : null;
                        addModuleFunctions(modulePath, alias, selected, items);
                    }
                }
                case NATIVE -> {
                    if (docPath != null) {
                        String rawPath = imp.getModule().replace("\"", "");
                        Path jarPath = docPath.getParent().resolve(rawPath).normalize();
                        addNativeCompletions(jarPath, alias, items);
                    }
                }
            }
        }
    }

    private static void addModuleFunctions(Path modulePath, String alias, List<String> selectedNames,
            List<CompletionItem> items) {
        boolean bare = alias == null || alias.isBlank();
        try {
            String src = Files.readString(modulePath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            for (Node n : modAst) {
                if (n instanceof FuncDecl f && !f.getName().equals("main") && f.isPublic()) {
                    if (selectedNames != null && !selectedNames.contains(f.getName())) {
                        continue;
                    }
                    String params = f.getParameters().stream()
                            .map(Parameter::name)
                            .collect(Collectors.joining(", "));
                    if (bare) {
                        CompletionItem item = new CompletionItem(f.getName());
                        item.setKind(CompletionItemKind.Function);
                        String prefix = f.isPure() ? "pure fn " : "fn ";
                        item.setDetail(prefix + f.getName() + "(" + params + ")");
                        items.add(item);
                    } else {
                        items.add(namespaceItem(alias, f.getName(), params, f.isPure()));
                    }
                } else if (n instanceof VarDecl v && v.isPublic()) {
                    if (selectedNames != null && !selectedNames.contains(v.getName())) {
                        continue;
                    }
                    String label = bare ? v.getName() : alias + "." + v.getName();
                    CompletionItem item = new CompletionItem(label);
                    item.setKind(CompletionItemKind.Variable);
                    item.setDetail((v.isConst() ? "const" : "var") + " " + v.getName());
                    items.add(item);
                    if (v.getInitializer() instanceof Expression.ObjectExpression obj) {
                        for (VarDecl f : obj.getVarDecls()) {
                            CompletionItem fi = new CompletionItem(label + "." + f.getName());
                            fi.setKind(CompletionItemKind.Field);
                            fi.setDetail((f.isConst() ? "const" : "var") + " " + f.getName());
                            items.add(fi);
                        }
                        for (FuncDecl m : obj.getMethods()) {
                            String params = m.getParameters().stream()
                                    .map(Parameter::name).collect(Collectors.joining(", "));
                            CompletionItem mi = new CompletionItem(label + "." + m.getName());
                            mi.setKind(CompletionItemKind.Method);
                            mi.setDetail("fn " + m.getName() + "(" + params + ")");
                            items.add(mi);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LSP] Failed to parse module '" + modulePath + "': " + e.getMessage());
        }
    }

    private static void addNativeCompletions(Path jarPath, String alias, List<CompletionItem> items) {
        if (!Files.exists(jarPath)) {
            return;
        }
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarUrl}, CompletionProvider.class.getClassLoader());
            var found = ServiceLoader.load(Lib.class, loader).findFirst();
            if (found.isEmpty()) {
                return;
            }
            Lib lib = found.get();
            Environment env = new Environment(null);
            lib.loadLib(env);
            for (Map.Entry<String, Object> entry : env.getLocalValues().entrySet()) {
                String name = entry.getKey();
                Object val = entry.getValue();
                CompletionItem item = new CompletionItem(alias + "." + name);
                if (val instanceof NativeFunction) {
                    item.setKind(CompletionItemKind.Function);
                    item.setDetail("fn " + name + "(...)");
                } else {
                    item.setKind(CompletionItemKind.Constant);
                    item.setDetail(String.valueOf(val));
                }
                items.add(item);
            }
        } catch (Exception e) {
            System.err.println("[LSP] Failed to load native lib '" + jarPath + "': " + e.getMessage());
        }
    }

    private static CompletionItem namespaceItem(String alias, String fn, String params, boolean isPure) {
        CompletionItem item = new CompletionItem(alias + "." + fn);
        item.setKind(CompletionItemKind.Function);
        String prefix = isPure ? "pure fn " : "fn ";
        item.setDetail(prefix + alias + "." + fn + "(" + params + ")");
        return item;
    }
}
