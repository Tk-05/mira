package com.mira.runtime.interpreter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import com.mira.Flags;
import com.mira.error.runtime.RuntimeError.LibImportConflictError;
import com.mira.error.runtime.RuntimeError.NativeLibLoadError;
import com.mira.error.runtime.RuntimeError.NativeLibNoImplementationError;
import com.mira.error.runtime.RuntimeError.NativeLibNotFoundError;
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
    private static final Map<String, String> globalLibNames = new HashMap<>();
    private static final Map<String, Lib> loadedNativeLibs = new HashMap<>();
    private static final List<URLClassLoader> nativeClassLoaders = new ArrayList<>();
    private static final Tokenizer tokenizer = new Tokenizer();
    private static final Parser parser = new Parser();

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
            put("map", new com.mira.lib.std.Map());
        }
    };

    public static void resolveImports(List<ImportExpression> imports, Environment environment, Interpreter interpreter, boolean entryPoint) {
        long start = System.currentTimeMillis();

        if (entryPoint) {
            internal.loadLib(environment);
        }

        for (ImportExpression expr : imports) {
            try {
                switch (expr.getKind()) {
                    case MODULE -> resolveModuleImport(interpreter, expr, environment);
                    case NATIVE -> resolveNativeImport(expr, environment);
                    case STDLIB -> resolveStdlibImport(expr, environment);
                }
            } catch (com.mira.error.MiraError e) {
                throw e;
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

            List<Token> tokens = tokenizer.tokenize(source, false);

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

            String alias = importExpression.getNamespace();
            boolean hasAlias = alias != null && !alias.isBlank();

            Environment targetEnv = hasAlias ? new Namespace(alias) : environment;

            List<ImportExpression> nestedImports = new ArrayList<>();
            for (Node ast : asts) {
                if (ast instanceof ImportExpression expr) {
                    nestedImports.add(expr);
                } else if (!(ast instanceof ModuleDecl)) {
                    interpreter.loadASTIntoContext(ast, targetEnv);
                }
            }

            resolveImports(nestedImports, targetEnv, interpreter, false);

            if (hasAlias) {
                environment.define(alias, targetEnv);
            }

            Flags.inputPath = previousFile;

        } catch (IOException e) {
            throw new RuntimeException("Module '" + importExpression.getModule() + "' could not be loaded", e);
        }
    }

    private static String validateModuleDeclaration(List<Node> asts, ImportExpression expr) {
        if (!(asts.getFirst() instanceof ModuleDecl moduleDecl)) {
            throw new AssertionError("Module '" + expr.getModule() + "' has no module declaration");
        }
        return moduleDecl.getModuleName();
    }

    private static void resolveStdlibImport(ImportExpression expr, Environment environment) {
        String libName = expr.getModule();
        String alias = expr.getNamespace();
        boolean hasAlias = alias != null && !alias.isBlank();

        String cacheKey = hasAlias ? libName + "#" + alias : libName;
        if (loadedLibs.contains(cacheKey)) {
            return;
        }

        Lib lib = libs.get(libName);
        if (lib == null) {
            throw new RuntimeException("Import '" + libName + "' could not be resolved");
        }

        loadedLibs.add(cacheKey);

        if (hasAlias) {
            Namespace ns = new Namespace(alias);
            lib.loadLib(ns);
            environment.define(alias, ns);
        } else {
            Environment temp = new Environment();
            lib.loadLib(temp);
            Set<String> conflicts = new HashSet<>(temp.keySet());
            conflicts.retainAll(globalLibNames.keySet());
            if (!conflicts.isEmpty()) {
                String conflictingLib = globalLibNames.get(conflicts.iterator().next());
                throw new LibImportConflictError(conflictingLib, libName, conflicts);
            }
            for (String name : temp.keySet()) {
                environment.define(name, temp.get(name));
                globalLibNames.put(name, libName);
            }
        }
    }

    private static void resolveNativeImport(ImportExpression expr, Environment environment) {
        String rawPath = expr.getModule();
        String alias = expr.getNamespace();

        Path currentFile = ((Path) Flags.inputPath).toAbsolutePath();
        Path candidate = Paths.get(rawPath);
        Path jarPath = candidate.isAbsolute()
                ? candidate.normalize()
                : currentFile.getParent().resolve(candidate).normalize();

        String cacheKey = jarPath.toAbsolutePath() + "#" + alias;

        if (loadedNativeLibs.containsKey(cacheKey)) {
            return;
        }

        if (!Files.exists(jarPath)) {
            throw new NativeLibNotFoundError(jarPath.toString());
        }

        Lib lib;
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarUrl},
                    ImportResolver.class.getClassLoader());
            nativeClassLoaders.add(loader);
            ServiceLoader<Lib> serviceLoader = ServiceLoader.load(Lib.class, loader);
            lib = serviceLoader.findFirst().orElse(null);
            if (lib == null) {
                throw new NativeLibNoImplementationError(jarPath.toString());
            }
        } catch (NativeLibNoImplementationError | NativeLibNotFoundError e) {
            throw e;
        } catch (Exception e) {
            throw new NativeLibLoadError(jarPath.toString(), e);
        }

        loadedNativeLibs.put(cacheKey, lib);
        Namespace ns = new Namespace(alias);
        lib.loadLib(ns);
        environment.define(alias, ns);
    }

    public static void reset() {
        loadedModules.clear();
        loadedLibs.clear();
        loadedNativeLibs.clear();
        globalLibNames.clear();
        for (URLClassLoader loader : nativeClassLoaders) {
            try { loader.close(); } catch (IOException ignored) {}
        }
        nativeClassLoaders.clear();
    }
}
