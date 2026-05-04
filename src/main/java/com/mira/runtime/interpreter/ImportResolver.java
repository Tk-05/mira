package com.mira.runtime.interpreter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;

public class ImportResolver {

    private record CachedModule(List<Node> ast, FileTime lastModified) {

    }

    private static final Internal internal = new Internal();
    private static final Map<String, CachedModule> astCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> moduleLoadFutures = new ConcurrentHashMap<>();
    private static final Set<String> loadedLibs = new HashSet<>();
    private static final Map<String, String> globalLibNames = new HashMap<>();
    private static final Map<String, Lib> loadedNativeLibs = new HashMap<>();
    private static final List<URLClassLoader> nativeClassLoaders = new ArrayList<>();
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

    public static void loadInternal(Environment environment) {
        internal.loadLib(environment);
    }

    public static void loadForCompiled(Environment env, String kindStr, String module, String alias) {
        ImportExpression.ImportKind kind = ImportExpression.ImportKind.valueOf(kindStr);
        Expression moduleExpr = new Expression() {
            @Override
            public <T> T accept(com.mira.runtime.visitors.ExprVisitor<T> v) {
                return (T) null;
            }

            @Override
            public String toString() {
                return module;
            }
        };
        ImportExpression expr = new ImportExpression(moduleExpr, alias, kind);
        switch (kind) {
            case STDLIB ->
                resolveStdlibImport(expr, env);
            case NATIVE ->
                resolveNativeImport(expr, env);
            case MODULE ->
                resolveModuleImport(new Interpreter(), expr, env);
        }
    }

    public static void reset() {
        moduleLoadFutures.clear();
        loadedLibs.clear();
        loadedNativeLibs.clear();
        globalLibNames.clear();
        for (URLClassLoader loader : nativeClassLoaders) {
            try {
                loader.close();
            } catch (IOException ignored) {
            }
        }
        nativeClassLoaders.clear();
    }

    public static void resolveImports(List<ImportExpression> imports, Environment environment, Interpreter interpreter, boolean entryPoint) {
        long start = System.currentTimeMillis();

        if (entryPoint) {
            internal.loadLib(environment);
        }

        Path parentInputPath = Flags.inputPath.get();
        List<ImportExpression> aliasedModules = imports.stream()
                .filter(e -> e.getKind() == ImportExpression.ImportKind.MODULE
                && e.getNamespace() != null && !e.getNamespace().isBlank())
                .toList();

        if (!aliasedModules.isEmpty()) {
            List<CompletableFuture<Void>> futures = aliasedModules.stream()
                    .map(e -> CompletableFuture.runAsync(() -> {
                Flags.inputPath.set(parentInputPath);
                resolveModuleImport(new Interpreter(), e, environment);
            }))
                    .toList();
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof com.mira.error.MiraError me) {
                    throw me;
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            }
        }

        for (ImportExpression expr : imports) {
            if (expr.getKind() == ImportExpression.ImportKind.MODULE
                    && expr.getNamespace() != null && !expr.getNamespace().isBlank()) {
                continue;
            }
            try {
                switch (expr.getKind()) {
                    case MODULE ->
                        resolveModuleImport(interpreter, expr, environment);
                    case NATIVE ->
                        resolveNativeImport(expr, environment);
                    case STDLIB ->
                        resolveStdlibImport(expr, environment);
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
        String rawPath = importExpression.getModule().replace("\"", "");
        if (!rawPath.endsWith(".mira")) {
            rawPath += ".mira";
        }

        Path currentFile = Flags.inputPath.get().toAbsolutePath();
        Path candidate = Paths.get(rawPath);
        Path modulePath = candidate.isAbsolute()
                ? candidate.normalize()
                : currentFile.getParent().resolve(candidate).normalize();
        String moduleKey = modulePath.toAbsolutePath().toString();

        CompletableFuture<Void> loadFuture = new CompletableFuture<>();
        CompletableFuture<Void> existing = moduleLoadFutures.putIfAbsent(moduleKey, loadFuture);
        if (existing != null) {
            try {
                existing.join();
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            }
            return;
        }

        try {
            if (!Files.exists(modulePath)) {
                throw new RuntimeException("Module file not found: " + modulePath);
            }

            FileTime currentModTime = Files.getLastModifiedTime(modulePath);
            CachedModule cached = astCache.get(moduleKey);
            List<Node> asts;
            if (cached != null && cached.lastModified().equals(currentModTime)) {
                asts = cached.ast();
            } else {
                String source = Files.readString(modulePath);
                List<Token> tokens = new Tokenizer().tokenize(source, false);
                asts = new Parser().parseTokens(tokens);
                astCache.put(moduleKey, new CachedModule(asts, currentModTime));
            }

            Path previousFile = Flags.inputPath.get();
            Flags.inputPath.set(modulePath);

            validateModuleDeclaration(asts, importExpression);

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
                synchronized (environment) {
                    environment.define(alias, targetEnv);
                }
            }

            Flags.inputPath.set(previousFile);
            loadFuture.complete(null);

        } catch (IOException | RuntimeException e) {
            loadFuture.completeExceptionally(e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Module '" + importExpression.getModule() + "' could not be loaded", e);
        }
    }

    private static String validateModuleDeclaration(List<Node> asts, ImportExpression expr) {
        if (!(asts.getFirst() instanceof ModuleDecl moduleDecl)) {
            throw new com.mira.error.runtime.RuntimeError.ModuleMissingDeclarationError(expr.getModule());
        }
        return moduleDecl.getModuleName();
    }

    private static void resolveStdlibImport(ImportExpression expr, Environment environment) {
        String libName = expr.getModule();
        String alias = expr.getNamespace();
        boolean hasAlias = alias != null && !alias.isBlank();

        String cacheKey = hasAlias ? libName + "#" + alias : libName;
        boolean alreadyLoaded = loadedLibs.contains(cacheKey);
        if (alreadyLoaded && !hasAlias) {
            return;
        }

        Lib lib = libs.get(libName);
        if (lib == null) {
            throw new RuntimeException("Import '" + libName + "' could not be resolved");
        }

        if (!alreadyLoaded) {
            loadedLibs.add(cacheKey);
        }

        if (hasAlias) {
            Namespace ns = new Namespace(alias);
            lib.loadLib(ns);
            if (expr.isSelective()) {
                Namespace filtered = new Namespace(alias);
                for (String fn : expr.getSelectedFunctions()) {
                    if (!ns.exists(fn)) {
                        throw new RuntimeException("Function '" + fn + "' not found in lib '" + libName + "'");
                    }
                    filtered.define(fn, ns.get(fn));
                }
                environment.define(alias, filtered);
            } else {
                environment.define(alias, ns);
            }
        } else {
            Environment temp = new Environment();
            lib.loadLib(temp);
            Set<String> toLoad = expr.isSelective()
                    ? new LinkedHashSet<>(expr.getSelectedFunctions())
                    : temp.keySet();
            for (String name : toLoad) {
                if (!temp.exists(name)) {
                    throw new RuntimeException("Function '" + name + "' not found in lib '" + libName + "'");
                }
            }
            Set<String> conflicts = new HashSet<>(toLoad);
            conflicts.retainAll(globalLibNames.keySet());
            if (!conflicts.isEmpty()) {
                String conflictingLib = globalLibNames.get(conflicts.iterator().next());
                throw new LibImportConflictError(conflictingLib, libName, conflicts);
            }
            for (String name : toLoad) {
                environment.define(name, temp.get(name));
                globalLibNames.put(name, libName);
            }
        }
    }

    private static void resolveNativeImport(ImportExpression expr, Environment environment) {
        String rawPath = expr.getModule();
        String alias = expr.getNamespace();

        Path currentFile = Flags.inputPath.get().toAbsolutePath();
        Path candidate = Paths.get(rawPath);
        Path jarPath = candidate.isAbsolute()
                ? candidate.normalize()
                : currentFile.getParent().resolve(candidate).normalize();

        String cacheKey = jarPath.toAbsolutePath() + "#" + alias;

        if (loadedNativeLibs.containsKey(cacheKey)) {
            Namespace ns = new Namespace(alias);
            loadedNativeLibs.get(cacheKey).loadLib(ns);
            environment.define(alias, ns);
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

}
