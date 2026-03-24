package com.mira.runtime.lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.lib.internal.Internal;
import com.mira.runtime.lib.std.Math;
import com.mira.runtime.lib.std.Strings;

public class ImportResolver {

    private static final Internal internal = new Internal();
    private static final Map<String, Lib> libs = new HashMap<>();

    static {
        libs.put("math", new Math());
        libs.put("string", new Strings());
        libs.put("list", new com.mira.runtime.lib.std.List());
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
