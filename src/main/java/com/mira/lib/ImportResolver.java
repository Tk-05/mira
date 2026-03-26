package com.mira.lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.lib.internal.Internal;
import com.mira.lib.std.IO;
import com.mira.lib.std.Math;
import com.mira.lib.std.Strings;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.runtime.interpreter.Environment;

public class ImportResolver {

    private static final Internal internal = new Internal();
    private static final Map<String, Lib> libs = new HashMap<>();

    static {
        libs.put("math", new Math());
        libs.put("string", new Strings());
        libs.put("list", new com.mira.lib.std.List());
        libs.put("io", new IO());
    }

    public static void resolveImports(List<ImportExpression> imports, Environment environment) {
        internal.loadLib(environment);

        for (ImportExpression expr : imports) {
            try {
                libs.get(expr.getLib()).loadLib(environment);
            } catch (NullPointerException nullPointerException) {
                throw new RuntimeException("Import '" + expr.getLib() + "' could not be resolved");
            }
        }
    }
}
