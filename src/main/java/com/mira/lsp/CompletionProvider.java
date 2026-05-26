package com.mira.lsp;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.mira.lexer.Tokenizer;
import com.mira.lib.Lib;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class CompletionProvider {

    private static final List<String> KEYWORDS = List.of(
            "var", "const", "fn", "return", "if", "else", "while", "for", "foreach",
            "in", "break", "continue", "switch", "case", "default", "do",
            "try", "catch", "finally", "throw", "import", "module", "as",
            "enum", "async", "await", "typeof", "spawn", "pure", "lock", "true", "false", "null",
            "exec", "exec isolated", "comptime"
    );

    private static final List<String> GLOBALS = List.of(
            "print", "println", "scan", "eval", "length", "assert", "exit",
            "readFile", "writeFile", "args"
    );

    private static final Map<String, String> STDLIB_PARAMS = Map.ofEntries(
            // string
            Map.entry("charAt", "str, index"),
            Map.entry("indexOf", "str, sub"),
            Map.entry("trim", "str"),
            Map.entry("split", "str, delimiter"),
            Map.entry("substr", "str, start, end"),
            Map.entry("strEqual", "a, b"),
            Map.entry("replace", "str, old, new"),
            Map.entry("upper", "str"),
            Map.entry("lower", "str"),
            Map.entry("startsWith", "str, prefix"),
            Map.entry("endsWith", "str, suffix"),
            Map.entry("contains", "col, val"),
            Map.entry("repeat", "str, n"),
            Map.entry("toNumber", "str"),
            Map.entry("padLeft", "str, width"),
            Map.entry("padRight", "str, width"),
            Map.entry("isNumeric", "str"),
            // collection
            Map.entry("size", "col"),
            Map.entry("push", "col, val"),
            Map.entry("pop", "col"),
            Map.entry("first", "col"),
            Map.entry("last", "col"),
            Map.entry("slice", "col, start, end"),
            Map.entry("reverse", "col"),
            Map.entry("concat", "col1, col2"),
            Map.entry("flatten", "col"),
            Map.entry("join", "col, sep"),
            Map.entry("newList", ""),
            Map.entry("remove", "col, index"),
            Map.entry("map", "col, fn"),
            Map.entry("filter", "col, fn"),
            Map.entry("reduce", "col, fn, init"),
            Map.entry("any", "col, fn"),
            Map.entry("all", "col, fn"),
            Map.entry("count", "col, fn"),
            Map.entry("sortBy", "col, fn"),
            Map.entry("sort", "col"),
            Map.entry("unique", "col"),
            Map.entry("sum", "col"),
            Map.entry("avg", "col"),
            Map.entry("zip", "col1, col2"),
            Map.entry("fill", "n, val"),
            Map.entry("min", "col"),
            Map.entry("max", "col"),
            Map.entry("take", "col, n"),
            Map.entry("drop", "col, n"),
            Map.entry("findFirst", "col, fn"),
            Map.entry("chunk", "col, size"),
            Map.entry("groupBy", "col, fn"),
            // map
            Map.entry("newMap", ""),
            Map.entry("mapSize", "map"),
            Map.entry("mapHas", "map, key"),
            Map.entry("mapRemove", "map, key"),
            Map.entry("mapKeys", "map"),
            Map.entry("mapValues", "map"),
            Map.entry("mapSet", "map, key, value"),
            Map.entry("mapGet", "map, key"),
            Map.entry("mapEntries", "map"),
            Map.entry("mapMerge", "map1, map2"),
            Map.entry("mapFromLists", "keys, values"),
            // math
            Map.entry("pow", "base, exp"),
            Map.entry("abs", "x"),
            Map.entry("rand", ""),
            Map.entry("randInt", "min, max"),
            Map.entry("round", "x"),
            Map.entry("floor", "x"),
            Map.entry("ceil", "x"),
            Map.entry("sqrt", "x"),
            Map.entry("cbrt", "x"),
            Map.entry("log", "x"),
            Map.entry("log10", "x"),
            Map.entry("log2", "x"),
            Map.entry("sin", "x"),
            Map.entry("cos", "x"),
            Map.entry("tan", "x"),
            Map.entry("asin", "x"),
            Map.entry("acos", "x"),
            Map.entry("atan", "x"),
            Map.entry("atan2", "y, x"),
            Map.entry("toRad", "deg"),
            Map.entry("toDeg", "rad"),
            Map.entry("sign", "x"),
            Map.entry("clamp", "val, min, max"),
            Map.entry("isNaN", "x"),
            Map.entry("isInf", "x"),
            Map.entry("gcd", "a, b"),
            Map.entry("lcm", "a, b"),
            Map.entry("factorial", "n"),
            Map.entry("trunc", "x"),
            Map.entry("hypot", "a, b"),
            // net
            Map.entry("httpGet", "url"),
            Map.entry("httpPost", "url, body, contentType"),
            Map.entry("httpPut", "url, body, contentType"),
            Map.entry("httpDelete", "url"),
            Map.entry("httpStatus", "url"),
            Map.entry("httpHeader", "url, header"),
            Map.entry("httpDownload", "url, path"),
            Map.entry("urlEncode", "str"),
            Map.entry("urlDecode", "str"),
            // io
            Map.entry("readFile", "path"),
            Map.entry("writeFile", "path, content"),
            Map.entry("fileExists", "path"),
            Map.entry("appendFile", "path, content"),
            Map.entry("listDir", "path"),
            Map.entry("mkdir", "path"),
            Map.entry("deleteFile", "path"),
            // dateTime
            Map.entry("now", ""),
            Map.entry("timestamp", ""),
            Map.entry("timestampMs", ""),
            Map.entry("dateFormat", "date, pattern"),
            Map.entry("year", ""),
            Map.entry("month", ""),
            Map.entry("day", ""),
            Map.entry("hour", ""),
            Map.entry("minute", ""),
            Map.entry("second", ""),
            Map.entry("dayOfWeek", ""),
            Map.entry("dayOfYear", ""),
            Map.entry("secondsSince", "date"),
            Map.entry("fromEpoch", "seconds"),
            Map.entry("addDays", "date, n"),
            Map.entry("dateDiff", "date1, date2"),
            Map.entry("isLeapYear", "year"),
            // shell
            Map.entry("execute", "command"),
            Map.entry("executeCode", "code"),
            Map.entry("getenv", "name"),
            Map.entry("hasenv", "name"),
            Map.entry("osName", ""),
            Map.entry("isWindows", ""),
            Map.entry("isLinux", ""),
            Map.entry("isMac", ""),
            Map.entry("cwd", ""),
            Map.entry("username", ""),
            Map.entry("homedir", ""),
            // json
            Map.entry("jsonGet", "json, key"),
            Map.entry("jsonHas", "json, key"),
            Map.entry("jsonArray", "json, key"),
            Map.entry("jsonBuild", "keys, values"),
            Map.entry("jsonFormat", "json"),
            Map.entry("jsonNested", "json, parent, key"),
            Map.entry("jsonIndexOf", "list, val"),
            Map.entry("jsonKeys", "json"),
            Map.entry("jsonSize", "json"),
            Map.entry("jsonSet", "json, key, value"),
            // regex
            Map.entry("matches", "pattern, str"),
            Map.entry("contains_regex", "pattern, str"),
            Map.entry("replaceAll", "pattern, str, replacement"),
            Map.entry("replaceFirst", "pattern, str, replacement"),
            Map.entry("split_regex", "pattern, str"),
            Map.entry("capture", "pattern, str"),
            Map.entry("countMatches", "pattern, str"),
            // process
            Map.entry("processStart", "command"),
            Map.entry("processAlive", "process"),
            Map.entry("processWait", "process"),
            Map.entry("processKill", "process"),
            Map.entry("processOutput", "process"),
            Map.entry("processExitCode", "process"),
            Map.entry("pid", ""),
            Map.entry("listProcesses", ""),
            Map.entry("processInfo", "pid"),
            Map.entry("sleep", "ms"),
            // thread
            Map.entry("newMutex", "")
    );

    private static final Map<String, List<String>> STDLIB = Map.ofEntries(
            Map.entry("string", List.of("charAt", "indexOf", "trim", "split", "substr", "strEqual", "replace",
                    "upper", "lower", "startsWith", "endsWith", "contains", "repeat",
                    "toNumber", "padLeft", "padRight", "isNumeric")),
            Map.entry("collection", List.of("size", "push", "pop", "first", "last", "contains", "indexOf",
                    "slice", "reverse", "concat", "flatten", "join", "newList", "remove",
                    "map", "filter", "reduce", "any", "all", "count", "sortBy",
                    "sort", "unique", "sum", "avg", "zip", "fill", "min", "max",
                    "take", "drop", "findFirst", "chunk", "groupBy")),
            Map.entry("map", List.of("newMap", "mapSize", "mapHas", "mapRemove", "mapKeys", "mapValues",
                    "mapSet", "mapGet", "mapEntries", "mapMerge", "mapFromLists")),
            Map.entry("math", List.of("pow", "max", "min", "abs", "rand", "randInt", "round", "floor",
                    "ceil", "sqrt", "cbrt", "log", "log10", "log2", "sin", "cos", "tan",
                    "asin", "acos", "atan", "atan2", "toRad", "toDeg", "sign", "clamp",
                    "isNaN", "isInf", "pi", "e", "inf", "nan",
                    "gcd", "lcm", "factorial", "trunc", "hypot")),
            Map.entry("net", List.of("httpGet", "httpPost", "httpStatus", "httpHeader", "httpDownload",
                    "httpPut", "httpDelete", "urlEncode", "urlDecode")),
            Map.entry("json", List.of("jsonGet", "jsonHas", "jsonArray", "jsonBuild", "jsonFormat",
                    "jsonNested", "jsonIndexOf", "jsonKeys", "jsonSize", "jsonSet")),
            Map.entry("dateTime", List.of("now", "timestamp", "timestampMs", "dateFormat", "year", "month",
                    "day", "hour", "minute", "second", "dayOfWeek", "dayOfYear",
                    "secondsSince", "fromEpoch", "addDays", "dateDiff", "isLeapYear")),
            Map.entry("shell", List.of("execute", "executeCode", "getenv", "hasenv", "osName",
                    "isWindows", "isLinux", "isMac", "cwd", "username", "homedir")),
            Map.entry("io", List.of("readFile", "writeFile", "fileExists", "appendFile", "listDir",
                    "mkdir", "deleteFile")),
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
            switch (node) {
                case VarDecl v -> {
                    CompletionItem item = new CompletionItem("$" + v.getName());
                    item.setKind(CompletionItemKind.Variable);
                    items.add(item);
                    if (v.getInitializer() instanceof ObjectExpression obj) {
                        for (VarDecl f : obj.getVarDecls()) {
                            CompletionItem fi = new CompletionItem("$" + v.getName() + "." + f.getName());
                            fi.setKind(CompletionItemKind.Field);
                            fi.setDetail((f.isConst() ? "const" : "var") + " " + f.getName());
                            items.add(fi);
                        }
                        for (FuncDecl m : obj.getMethods()) {
                            String params = m.getParameters().stream()
                                    .map(Parameter::name).collect(Collectors.joining(", "));
                            CompletionItem mi = new CompletionItem("$" + v.getName() + "." + m.getName());
                            mi.setKind(CompletionItemKind.Method);
                            mi.setDetail("fn " + m.getName() + "(" + params + ")");
                            items.add(mi);
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
                    collectFromNodes(f.getBody(), items);
                }
                case ComptimeBlock comptime -> {
                    for (Node bodyNode : comptime.getBody()) {
                        if (bodyNode instanceof VarDecl v) {
                            CompletionItem item = new CompletionItem("$" + v.getName());
                            item.setKind(CompletionItemKind.Constant);
                            item.setDetail("comptime const " + v.getName());
                            items.add(item);
                        }
                    }
                }
                default -> {
                }
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
                            String params = STDLIB_PARAMS.getOrDefault(fn, "");
                            items.add(namespaceItem(alias, fn, params, false));
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
                    if (docPath != null) {
                        String rawPath = imp.getModule().replace("\"", "");
                        Path jarPath = docPath.getParent().resolve(rawPath).normalize();
                        addNativeCompletions(jarPath, alias, items);
                    }
                }
            }
        }
    }

    private static void addModuleFunctions(Path modulePath, String alias, List<CompletionItem> items) {
        try {
            String src = Files.readString(modulePath);
            List<Node> modAst = new Parser().parseTokens(new Tokenizer().tokenize(src, false));
            for (Node n : modAst) {
                if (n instanceof FuncDecl f && !f.getName().equals("main")) {
                    String params = f.getParameters().stream()
                            .map(Parameter::name)
                            .collect(Collectors.joining(", "));
                    items.add(namespaceItem(alias, f.getName(), params, f.isPure()));
                } else if (n instanceof VarDecl v) {
                    CompletionItem item = new CompletionItem(alias + "." + v.getName());
                    item.setKind(CompletionItemKind.Variable);
                    item.setDetail((v.isConst() ? "const" : "var") + " " + v.getName());
                    items.add(item);
                    if (v.getInitializer() instanceof Expression.ObjectExpression obj) {
                        for (VarDecl f : obj.getVarDecls()) {
                            CompletionItem fi = new CompletionItem(alias + "." + v.getName() + "." + f.getName());
                            fi.setKind(CompletionItemKind.Field);
                            fi.setDetail((f.isConst() ? "const" : "var") + " " + f.getName());
                            items.add(fi);
                        }
                        for (FuncDecl m : obj.getMethods()) {
                            String params = m.getParameters().stream()
                                    .map(Parameter::name).collect(Collectors.joining(", "));
                            CompletionItem mi = new CompletionItem(alias + "." + v.getName() + "." + m.getName());
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
