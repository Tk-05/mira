package com.mira.runtime.interpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mira.Flags;
import com.mira.lexer.Tokenizer;
import com.mira.lexer.token.Token;
import com.mira.lib.Lib;
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
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;

public class ImportResolver {

    private static final Internal internal = new Internal();
    private static final Set<String> loadedModules = new HashSet<>();
    private static final Set<String> loadedLibs = new HashSet<>();

    private static final Map<String, Lib> libs = new HashMap<String, Lib>() {
        {
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
        }
    };

    public static void resolveImports(List<ImportExpression> imports, Environment environment, Interpreter interpreter, boolean entryPoint) {
        long start = System.currentTimeMillis();

        if (entryPoint) {
            internal.loadLib(environment);
        }

        for (ImportExpression expr : imports) {
            try {
                if (expr.isExternalModule()) {
                    resolveModuleImport(interpreter, expr, environment);
                } else {
                    String libName = expr.getModule();

                    if (loadedLibs.contains(libName)) {
                        continue;
                    }

                    Lib lib = libs.get(libName);
                    if (lib == null) {
                        throw new RuntimeException("Import '" + libName + "' could not be resolved");
                    }

                    loadedLibs.add(libName);
                    lib.loadLib(environment);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Import '" + expr.getModule() + "' could not be resolved", e);
            }
        }

        if (Flags.libInfo && entryPoint) {
            System.out.println("Resolving of imports took " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    private static void resolveModuleImport(Interpreter interpreter, ImportExpression importExpression, Environment environment) {
        try {
            String rawPath = importExpression.getModule().replace("\"", "");

            if (!rawPath.endsWith(".mira")) {
                rawPath += ".mira";
            }

            Path currentFile = ((Path) Flags.inputPath).toAbsolutePath();
            Path modulePath;

            Path candidate = Paths.get(rawPath);

            if (candidate.isAbsolute()) {
                modulePath = candidate.normalize();
            } else if (rawPath.startsWith("./") || rawPath.startsWith("../")) {
                modulePath = currentFile.getParent().resolve(candidate).normalize();
            } else {
                modulePath = currentFile.getParent().resolve(candidate).normalize();
            }

            String moduleKey = modulePath.toAbsolutePath().toString();

            if (loadedModules.contains(moduleKey)) {
                return;
            }
            loadedModules.add(moduleKey);

            if (!Files.exists(modulePath)) {
                throw new RuntimeException("Module file not found: " + modulePath);
            }
            String source = Files.readString(modulePath);

            Path previousFile = (Path) Flags.inputPath;
            Flags.inputPath = modulePath;

            Tokenizer tokenizer = new Tokenizer();
            List<Token> tokens = tokenizer.tokenize(source, false);

            Parser parser = new Parser();
            List<Node> asts = parser.parseTokens(tokens);

            String declaredModuleName = validateModuleDeclaration(asts, importExpression);

            String fileName = modulePath.getFileName().toString();
            String expectedModuleName = fileName.replace(".mira", "");

            if (!declaredModuleName.equals(expectedModuleName)) {
                throw new RuntimeException(
                        "Module name mismatch: expected '" + expectedModuleName
                        + "' but found '" + declaredModuleName + "'"
                );
            }

            List<ImportExpression> imports = new ArrayList<>();
            for (Node ast : asts) {
                if (ast instanceof ImportExpression expr) {
                    imports.add(expr);
                } else if (!(ast instanceof ModuleDecl)) {
                    interpreter.loadASTIntoGlobalContext(ast);
                }
            }

            resolveImports(imports, environment, interpreter, false);

            Flags.inputPath = previousFile;

        } catch (IOException e) {
            throw new RuntimeException("Module '" + importExpression.getModule() + "' could not be loaded", e);
        }
    }

    private static String validateModuleDeclaration(List<Node> asts, ImportExpression expr) {
        if (!(asts.getFirst() instanceof ModuleDecl moduleDecl)) {
            throw new AssertionError("Module '" + expr.getModule() + "' has no module declaration");
        } else {
            return moduleDecl.getModuleName();
        }
    }

    public static void reset() {
        loadedModules.clear();
        loadedLibs.clear();
    }
}
