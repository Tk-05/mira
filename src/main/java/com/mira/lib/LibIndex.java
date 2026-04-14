package com.mira.lib;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class LibIndex {

    public record GlobalFunction(String name, int arity, boolean pure) {

    }

    public static final List<GlobalFunction> GLOBALS = List.of(
            new GlobalFunction("print", 1, false),
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
            "io", "shell", "net", "process", "dateTime", "collection"
    );
}
