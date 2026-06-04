package com.mira.lib;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mira.Flags;
import com.mira.lib.std.Bytes;
import com.mira.lib.std.Collection;
import com.mira.lib.std.DateTime;
import com.mira.lib.std.IO;
import com.mira.lib.std.Json;
import com.mira.lib.std.Net;
import com.mira.lib.std.Regex;
import com.mira.lib.std.Shell;
import com.mira.lib.std.Strings;
import com.mira.lib.std.ThreadLib;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.expression.Expression.ImportExpression.ImportKind;
import com.mira.runtime.functions.Callable;
import com.mira.runtime.interpreter.Environment;

public final class LibIndex {

    public record GlobalFunction(String name, int arity, boolean pure) {

    }

    public static final List<GlobalFunction> GLOBALS = List.of(
            new GlobalFunction("print", 1, false),
            new GlobalFunction("println", 1, false),
            new GlobalFunction("scan", 0, false),
            new GlobalFunction("exec", 1, false),
            new GlobalFunction("exit", 1, false),
            new GlobalFunction("readFile", 1, false),
            new GlobalFunction("writeFile", 2, false),
            new GlobalFunction("eval", 1, true),
            new GlobalFunction("length", 1, true),
            new GlobalFunction("assert", -1, true),
            new GlobalFunction("args", -1, true)
    );

    public static final Set<String> GLOBAL_NAMES = GLOBALS.stream()
            .map(GlobalFunction::name)
            .collect(Collectors.toUnmodifiableSet());

    public static final Set<String> IMPURE_GLOBAL_NAMES = GLOBALS.stream()
            .filter(f -> !f.pure())
            .map(GlobalFunction::name)
            .collect(Collectors.toUnmodifiableSet());

    public static final Set<String> PURE_GLOBAL_NAMES = GLOBALS.stream()
            .filter(GlobalFunction::pure)
            .map(GlobalFunction::name)
            .collect(Collectors.toUnmodifiableSet());

    public static final Map<String, Integer> GLOBAL_ARITIES = GLOBALS.stream()
            .collect(Collectors.toUnmodifiableMap(GlobalFunction::name, GlobalFunction::arity));

    public static final Set<String> IMPURE_NAMESPACES = Set.of(
            "io", "shell", "net", "process", "dateTime", "collection", "bytes"
    );

    public static final Set<String> INTERNAL_NAMES = Set.of(
            "toNum", "toInt", "toFloat", "toStr", "toBool",
            "chars", "toList", "toArray", "spawn", "readFile", "writeFile"
    );

    public static final Map<String, Lib> STDLIB_LIBS = new HashMap<>() {
        {
            put("math", new com.mira.lib.std.Math());
            put("string", new Strings());
            put("io", new IO());
            put("shell", new Shell());
            put("dateTime", new DateTime());
            put("collection", new Collection());
            put("json", new Json());
            put("net", new Net());
            put("process", new com.mira.lib.std.Process());
            put("regex", new Regex());
            put("map", new com.mira.lib.std.Map());
            put("thread", new ThreadLib());
            put("bytes", new Bytes());
        }
    };

    public static Set<String> getFunctionNames(String libName) {
        Lib lib = STDLIB_LIBS.get(libName);
        if (lib == null) {
            return Set.of();
        }
        Environment env = new Environment();
        lib.loadLib(env);
        return env.getDefinedNames();
    }

    public static Map<String, Integer> getFunctionArities(String libName) {
        Lib lib = STDLIB_LIBS.get(libName);
        if (lib == null) {
            return Map.of();
        }
        Environment env = new Environment();
        lib.loadLib(env);
        Map<String, Integer> arities = new HashMap<>();
        for (String name : env.getDefinedNames()) {
            Object val = env.getOrNull(name);
            if (val instanceof Callable c) {
                arities.put(name, c.getArity());
            }
        }
        return arities;
    }

    public static void printImportInfo(List<Node> asts) {
        List<ImportExpression> imports = asts.stream()
                .filter(n -> n instanceof ImportExpression)
                .map(n -> (ImportExpression) n)
                .toList();

        if (imports.isEmpty()) {
            System.out.println("No imports");
            return;
        }

        for (ImportExpression expr : imports) {
            String name = expr.getModule().replace("\"", "");
            String ns = expr.getNamespace();
            String kind = expr.getKind().name().toLowerCase();
            String label = (ns != null && !ns.isBlank()) ? name + " as " + ns : name + " (global)";
            System.out.println("[" + kind + "] " + label);

            if (Flags.libInfoFull) {
                Set<String> symbols;
                if (expr.isSelective()) {
                    symbols = new LinkedHashSet<>(expr.getSelectedFunctions());
                } else if (expr.getKind() == ImportKind.STDLIB) {
                    symbols = getFunctionNames(name);
                } else {
                    System.out.println("  → (symbols available at runtime)");
                    continue;
                }
                if (!symbols.isEmpty()) {
                    System.out.println("  → " + symbols.stream().sorted().collect(Collectors.joining(", ")));
                }
            }
        }
    }
}
