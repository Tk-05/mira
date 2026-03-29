package com.mira.lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mira.Flags;
import com.mira.lib.internal.Internal;
import com.mira.lib.std.Collection;
import com.mira.lib.std.DateTime;
import com.mira.lib.std.IO;
import com.mira.lib.std.Json;
import com.mira.lib.std.Math;
import com.mira.lib.std.Net;
import com.mira.lib.std.Regex;
import com.mira.lib.std.Shell;
import com.mira.lib.std.Strings;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.runtime.interpreter.Environment;

public class ImportResolver {

    private static final Internal internal = new Internal();
    private static final Map<String, Lib> libs = new HashMap<String, Lib>() {{
        put("math", new Math());
        put("string", new Strings());
        put("io", new IO());
        put("shell", new Shell());
        put("dateTime", new DateTime());
        put("collection", new Collection());
        put("json", new Json());
        put("net", new Net());
        put("process", new com.mira.lib.std.Process());
        put("regex", new Regex());
    }};

    public static void resolveImports(List<ImportExpression> imports, Environment environment) {
        long start = System.currentTimeMillis();
        
        internal.loadLib(environment);

        for (ImportExpression expr : imports) {
            try {
                libs.get(expr.getLib()).loadLib(environment);
            } catch (NullPointerException nullPointerException) {
                throw new RuntimeException("Import '" + expr.getLib() + "' could not be resolved");
            }
        }

        if (Flags.libInfo) {
            System.out.println("Resolving of imports took " + (System.currentTimeMillis() - start) + " ms");   
        }
    }
}
