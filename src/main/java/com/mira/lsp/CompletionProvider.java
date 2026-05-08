package com.mira.lsp;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.statement.Statement;

public class CompletionProvider {

    private static final List<String> KEYWORDS = List.of(
            "var", "const", "fn", "return", "if", "else", "while", "for", "foreach",
            "in", "break", "continue", "switch", "case", "default", "do",
            "try", "catch", "finally", "throw", "import", "module", "as",
            "enum", "async", "await", "typeof", "spawn", "pure", "true", "false", "null"
    );

    private static final List<String> GLOBALS = List.of(
            "print", "println", "scan", "eval", "length", "assert", "exit",
            "exec", "readFile", "writeFile", "args"
    );

    private static final Map<String, List<String>> STDLIB = Map.ofEntries(
            Map.entry("string", List.of("charAt", "indexOf", "trim", "split", "substr", "strEqual", "replace")),
            Map.entry("collection", List.of("size", "push", "pop", "first", "last", "contains", "indexOf",
                    "slice", "reverse", "concat", "flatten", "join", "newList", "remove")),
            Map.entry("map", List.of("newMap", "mapSize", "mapHas", "mapRemove", "mapKeys", "mapValues", "mapSet", "mapGet")),
            Map.entry("math", List.of("pow", "max", "min", "abs", "rand", "randInt", "round", "floor",
                    "ceil", "sqrt", "cbrt", "log", "log10", "log2", "sin", "cos", "tan",
                    "asin", "acos", "atan", "atan2", "toRad", "toDeg", "sign", "clamp",
                    "isNaN", "isInf", "pi", "e", "inf", "nan")),
            Map.entry("net", List.of("httpGet", "httpPost", "httpStatus", "httpHeader", "httpDownload")),
            Map.entry("json", List.of("jsonGet", "jsonHas", "jsonArray", "jsonBuild", "jsonFormat",
                    "jsonNested", "jsonIndexOf")),
            Map.entry("dateTime", List.of("now", "timestamp", "timestampMs", "dateFormat", "year", "month",
                    "day", "hour", "minute", "second", "dayOfWeek", "dayOfYear",
                    "secondsSince", "fromEpoch")),
            Map.entry("shell", List.of("execute", "executeCode", "getenv", "hasenv", "osName",
                    "isWindows", "isLinux", "isMac", "cwd", "username", "homedir")),
            Map.entry("io", List.of("readFile", "writeFile")),
            Map.entry("regex", List.of("matches", "contains", "findFirst", "findAll", "replaceAll",
                    "replaceFirst", "split", "capture", "countMatches")),
            Map.entry("process", List.of("processStart", "processAlive", "processWait", "processKill",
                    "processOutput", "processExitCode", "pid", "listProcesses",
                    "processInfo", "sleep")),
            Map.entry("thread", List.of("newMutex"))
    );

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

        collectFromNodes(ast, items);
        collectFromImports(ast, documentUri, items);
        return items;
    }

    private static void collectFromNodes(List<Node> nodes, List<CompletionItem> items) {
        for (Node node : nodes) {
            if (node instanceof Statement.VarDecl v) {
                CompletionItem item = new CompletionItem("$" + v.getName());
                item.setKind(CompletionItemKind.Variable);
                items.add(item);
            } else if (node instanceof Statement.FuncDecl f) {
                CompletionItem item = new CompletionItem(f.getName());
                item.setKind(CompletionItemKind.Function);
                String prefix = f.isPure() ? "pure fn " : "fn ";
                item.setDetail(prefix + f.getName() + "("
                        + f.getParameters().stream()
                                .map(Parameter::name)
                                .collect(Collectors.joining(", ")) + ")");
                items.add(item);
                collectFromNodes(f.getBody(), items);
            }
        }
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
            if (alias == null || alias.isBlank()) {
                continue;
            }

            switch (imp.getKind()) {
                case STDLIB -> {
                    String modName = imp.getModule().replace("\"", "");
                    List<String> fns = STDLIB.get(modName);
                    if (fns != null) {
                        for (String fn : fns) {
                            items.add(namespaceItem(alias, fn, "", false));
                        }
                    }
                }
                case MODULE -> {
                    if (docPath != null) {
                        String rawPath = imp.getModule().replace("\"", "");
                        Path modulePath = docPath.getParent().resolve(rawPath).normalize();
                        addModuleFunctions(modulePath, alias, items);
                    }
                }
                case NATIVE -> {
                } // runtime-only, skip
            }
        }
    }

    private static void addModuleFunctions(Path modulePath, String alias, List<CompletionItem> items) {
        try {
            String src = Files.readString(modulePath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            for (Node n : modAst) {
                if (n instanceof Statement.FuncDecl f && !f.getName().equals("main")) {
                    String params = f.getParameters().stream()
                            .map(Parameter::name)
                            .collect(Collectors.joining(", "));
                    items.add(namespaceItem(alias, f.getName(), params, f.isPure()));
                }
            }
        } catch (Exception ignored) {
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
