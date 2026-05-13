package com.mira.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

import com.mira.Flags;
import com.mira.error.runtime.RuntimeError.ModuleMissingDeclarationError;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.expression.Expression;
import com.mira.parser.nodes.expression.Expression.ImportExpression;
import com.mira.parser.nodes.statement.Statement.EnumDecl;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;

public class Compiler {

    public record CompileResult(byte[] mainClass, Map<String, byte[]> lambdaClasses, String className) {

    }

    private static final String ENV = ClassEmitter.ENV_NAME;
    private static final String ENV_D = ClassEmitter.ENV_DESC;
    private static final String RT = ClassEmitter.RT_NAME;
    private static final String OBJ_D = "Ljava/lang/Object;";
    private static final String IMPORT_RESOLVER = "com/mira/runtime/interpreter/ImportResolver";

    public CompileResult compile(List<Node> ast, String scriptName) {
        String className = toClassName(scriptName);
        ClassEmitter ce = new ClassEmitter(className);
        int[] lambdaCounter = {0};

        Set<String> knownFunctions = new HashSet<>();
        Set<String> pureFunctions = new HashSet<>();
        for (Node node : ast) {
            if (node instanceof FuncDecl fd && !fd.isAsync()) {
                knownFunctions.add(fd.getName());
                if (fd.isPure()) {
                    pureFunctions.add(fd.getName());
                    ce.declareCacheField(fd.getName());
                }
            }
        }

        Map<String, byte[]> extras = new HashMap<>();
        Map<String, String> compiledModules = compileModuleImports(ast, extras);

        emitStaticInit(ce, className, ast, pureFunctions);
        emitMain(ce, className, knownFunctions, lambdaCounter, ast, compiledModules);

        for (Node node : ast) {
            if (node instanceof FuncDecl fd) {
                emitTopLevelFunction(ce, className, knownFunctions, lambdaCounter, fd, pureFunctions);
            }
        }

        byte[] mainBytes = ce.finish();
        extras.putAll(ce.getExtraClasses());
        return new CompileResult(mainBytes, extras, className);
    }

    private Map<String, String> compileModuleImports(List<Node> ast, Map<String, byte[]> extras) {
        Map<String, String> result = new HashMap<>();
        for (Node node : ast) {
            if (!(node instanceof ImportExpression ie)) {
                continue;
            }
            if (ie.getKind() != ImportExpression.ImportKind.MODULE) {
                continue;
            }
            String alias = ie.getNamespace();
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String rawPath = ie.getModule().replace("\"", "");
            if (!rawPath.endsWith(".mira")) {
                rawPath += ".mira";
            }
            Path modulePath = Flags.inputPath.get().toAbsolutePath().getParent()
                    .resolve(rawPath).normalize();
            if (!Files.exists(modulePath)) {
                throw new RuntimeException("Module not found: " + modulePath);
            }
            try {
                String source = Files.readString(modulePath);
                List<Node> moduleAst = new Parser().parseTokens(
                        new Tokenizer().tokenize(source, false));

                String fileName = modulePath.getFileName().toString();
                if (moduleAst.isEmpty() || !(moduleAst.getFirst() instanceof ModuleDecl)) {
                    throw new ModuleMissingDeclarationError(fileName);
                }

                Path prev = Flags.inputPath.get();
                Flags.inputPath.set(modulePath);
                CompileResult r = new Compiler().compile(moduleAst,
                        modulePath.getFileName().toString());
                Flags.inputPath.set(prev);
                extras.put(r.className(), r.mainClass());
                extras.putAll(r.lambdaClasses());
                result.put(alias, r.className());
            } catch (IOException e) {
                throw new RuntimeException("Cannot read module: " + modulePath, e);
            }
        }
        return result;
    }

    private void emitStaticInit(ClassEmitter ce, String className, List<Node> ast, Set<String> pureFunctions) {
        MethodVisitor mv = ce.openStaticInit();
        mv.visitCode();

        mv.visitTypeInsn(NEW, ENV);
        mv.visitInsn(DUP);
        mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        mv.visitMethodInsn(INVOKESPECIAL, ENV, "<init>",
                "(L" + ENV + ";)V", false);
        mv.visitFieldInsn(PUTSTATIC, className, "GLOBALS", ENV_D);

        mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ENV_D);
        mv.visitMethodInsn(INVOKESTATIC, IMPORT_RESOLVER, "loadInternal",
                "(" + ENV_D + ")V", false);

        for (Node node : ast) {
            if (node instanceof Expression.ImportExpression ie) {
                if (ie.getKind() == Expression.ImportExpression.ImportKind.MODULE) {
                    continue;
                }
                String alias = ie.getNamespace();
                boolean hasAlias = alias != null && !alias.isBlank();
                mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ENV_D);
                mv.visitLdcInsn(ie.getKind().name());
                mv.visitLdcInsn(ie.getModule());
                if (hasAlias) {
                    mv.visitLdcInsn(alias);
                } else {
                    mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
                }
                mv.visitMethodInsn(INVOKESTATIC, IMPORT_RESOLVER, "loadForCompiled",
                        "(" + ENV_D + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
            }
        }

        for (String fnName : pureFunctions) {
            mv.visitTypeInsn(NEW, "java/util/concurrent/ConcurrentHashMap");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/concurrent/ConcurrentHashMap", "<init>", "()V", false);
            mv.visitFieldInsn(PUTSTATIC, className, "CACHE$" + fnName, "Ljava/util/concurrent/ConcurrentHashMap;");
        }

        for (Node node : ast) {
            if (node instanceof FuncDecl fd) {
                String mName = "mira$" + fd.getName();
                String syncClass = className + "$Lambda$fn$" + fd.getName() + (fd.isAsync() ? "$sync" : "");
                ce.emitLambdaClass(syncClass, className, mName, fd.getArity());

                String visibleClass = syncClass;
                if (fd.isAsync()) {
                    String asyncClass = className + "$Lambda$fn$" + fd.getName();
                    ce.emitAsyncLambdaClass(asyncClass, syncClass, fd.getArity());
                    visibleClass = asyncClass;
                }

                mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ENV_D);
                mv.visitLdcInsn(fd.getName());
                mv.visitTypeInsn(NEW, visibleClass);
                mv.visitInsn(DUP);
                emitIntConst(mv, fd.getArity());
                mv.visitMethodInsn(INVOKESPECIAL, visibleClass, "<init>", "(I)V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "define",
                        "(Ljava/lang/String;" + OBJ_D + ")V", false);
            }
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitMain(ClassEmitter ce, String className,
            Set<String> knownFunctions, int[] lambdaCounter, List<Node> ast,
            Map<String, String> compiledModules) {
        MethodVisitor mv = ce.openMain();
        mv.visitCode();

        LocalSlotTable slots = new LocalSlotTable(1);
        CompilerContext ctx = new CompilerContext(className, mv, slots,
                knownFunctions, lambdaCounter, true);
        MethodEmitter emitter = new MethodEmitter(ctx, ce);

        mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ClassEmitter.ENV_DESC);
        mv.visitLdcInsn("args");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, RT, "wrapArgs", "([Ljava/lang/String;)Ljava/lang/Object;", false);
        mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, ClassEmitter.ENV_NAME, "define",
                "(Ljava/lang/String;Ljava/lang/Object;)V", false);

        for (Node node : ast) {
            if (node instanceof EnumDecl) {
                emitter.emitNode(node);
            }
        }

        for (Map.Entry<String, String> entry : compiledModules.entrySet()) {
            String alias = entry.getKey();
            String dotName = entry.getValue().replace('/', '.');
            mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ClassEmitter.ENV_DESC);
            mv.visitLdcInsn(alias);
            mv.visitLdcInsn(dotName);
            mv.visitMethodInsn(INVOKESTATIC, RT, "loadCompiledModule",
                    "(" + ClassEmitter.ENV_DESC + "Ljava/lang/String;Ljava/lang/String;)V", false);
        }

        for (Node node : ast) {
            if (node instanceof FuncDecl || node instanceof ModuleDecl
                    || node instanceof Expression.ImportExpression || node instanceof EnumDecl) {
                continue;
            }
            emitter.emitNode(node);
        }

        if (Flags.mainFunction && knownFunctions.contains("main")) {
            int mainArity = ast.stream()
                    .filter(n -> n instanceof FuncDecl fd && "main".equals(fd.getName()))
                    .mapToInt(n -> ((FuncDecl) n).getParameters().size())
                    .findFirst().orElse(-1);

            String errClass = "com/mira/error/runtime/RuntimeError$ArgMismatchError";
            if (mainArity == 0) {
                Label noArgs = new Label();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARRAYLENGTH);
                mv.visitJumpInsn(org.objectweb.asm.Opcodes.IFEQ, noArgs);
                mv.visitTypeInsn(NEW, errClass);
                mv.visitInsn(DUP);
                mv.visitLdcInsn("main");
                mv.visitInsn(ICONST_0);
                mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_1);
                mv.visitMethodInsn(INVOKESPECIAL, errClass, "<init>", "(Ljava/lang/String;II)V", false);
                mv.visitInsn(org.objectweb.asm.Opcodes.ATHROW);
                mv.visitLabel(noArgs);
            } else if (mainArity > 0) {
                Label hasArgs = new Label();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARRAYLENGTH);
                mv.visitJumpInsn(org.objectweb.asm.Opcodes.IFGT, hasArgs);
                mv.visitTypeInsn(NEW, errClass);
                mv.visitInsn(DUP);
                mv.visitLdcInsn("main");
                emitIntConst(mv, mainArity);
                mv.visitInsn(ICONST_0);
                mv.visitMethodInsn(INVOKESPECIAL, errClass, "<init>", "(Ljava/lang/String;II)V", false);
                mv.visitInsn(org.objectweb.asm.Opcodes.ATHROW);
                mv.visitLabel(hasArgs);
            }

            mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_1);
            mv.visitTypeInsn(org.objectweb.asm.Opcodes.ANEWARRAY, "java/lang/Object");
            mv.visitInsn(org.objectweb.asm.Opcodes.DUP);
            mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESTATIC, RT, "wrapArgs", "([Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitInsn(org.objectweb.asm.Opcodes.AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, className, "mira$main", ClassEmitter.FN_DESC, false);
            mv.visitInsn(org.objectweb.asm.Opcodes.POP);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitTopLevelFunction(ClassEmitter ce, String className,
            Set<String> knownFunctions, int[] lambdaCounter, FuncDecl fd, Set<String> pureFunctions) {
        boolean isPure = pureFunctions.contains(fd.getName()) && !fd.isAsync();
        String implName = isPure ? "mira$" + fd.getName() + "$impl" : "mira$" + fd.getName();

        MethodVisitor mv = ce.openFunction(implName);
        mv.visitCode();

        LocalSlotTable slots = new LocalSlotTable(1);
        CompilerContext ctx = new CompilerContext(className, mv, slots,
                knownFunctions, lambdaCounter, false);
        MethodEmitter emitter = new MethodEmitter(ctx, ce);

        List<Parameter> params = fd.getParameters();
        for (int i = 0; i < params.size(); i++) {
            Parameter param = params.get(i);
            int slot = slots.allocate(param.name());
            if (param.hasDefault()) {
                Label useDefault = new Label(), useDefaultAfterPop = new Label(), done = new Label();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(org.objectweb.asm.Opcodes.ARRAYLENGTH);
                emitIntConst(mv, i + 1);
                mv.visitJumpInsn(org.objectweb.asm.Opcodes.IF_ICMPLT, useDefault);
                mv.visitVarInsn(ALOAD, 0);
                emitIntConst(mv, i);
                mv.visitInsn(org.objectweb.asm.Opcodes.AALOAD);
                mv.visitInsn(org.objectweb.asm.Opcodes.DUP);
                mv.visitMethodInsn(INVOKESTATIC, RT, "isNullValue", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(org.objectweb.asm.Opcodes.IFNE, useDefaultAfterPop);
                mv.visitJumpInsn(GOTO, done);
                mv.visitLabel(useDefaultAfterPop);
                mv.visitInsn(org.objectweb.asm.Opcodes.POP);
                mv.visitLabel(useDefault);
                param.defaultValue().accept(emitter);
                mv.visitLabel(done);
            } else {
                mv.visitVarInsn(ALOAD, 0);
                emitIntConst(mv, i);
                mv.visitInsn(org.objectweb.asm.Opcodes.AALOAD);
            }
            mv.visitVarInsn(ASTORE, slot);
        }
        if (fd.getVariadicParam() != null) {
            int slot = slots.allocate(fd.getVariadicParam());
            mv.visitVarInsn(ALOAD, 0);
            emitIntConst(mv, params.size());
            mv.visitMethodInsn(INVOKESTATIC, RT, "variadicTail",
                    "([" + OBJ_D + "I)" + OBJ_D, false);
            mv.visitVarInsn(ASTORE, slot);
        }

        emitter.emitBody(fd.getBody());

        mv.visitMethodInsn(INVOKESTATIC, RT, "nullVal", "()" + OBJ_D, false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        if (isPure) {
            emitPureFunctionWrapper(ce, className, fd.getName());
        }
    }

    private static final String CACHE_DESC = "Ljava/util/concurrent/ConcurrentHashMap;";

    private void emitPureFunctionWrapper(ClassEmitter ce, String className, String funcName) {
        MethodVisitor mv = ce.openFunction("mira$" + funcName);
        mv.visitCode();

        Label missLabel = new Label();

        mv.visitFieldInsn(GETSTATIC, className, "CACHE$" + funcName, CACHE_DESC);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, RT, "cacheGet",
                "(" + CACHE_DESC + "[Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 1);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(GETSTATIC, RT, "CACHE_MISS", OBJ_D);
        mv.visitJumpInsn(IF_ACMPEQ, missLabel);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARETURN);

        mv.visitLabel(missLabel);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, className, "mira$" + funcName + "$impl",
                "([Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 2);

        mv.visitFieldInsn(GETSTATIC, className, "CACHE$" + funcName, CACHE_DESC);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKESTATIC, RT, "cachePut",
                "(" + CACHE_DESC + "[Ljava/lang/Object;Ljava/lang/Object;)V", false);

        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static String toClassName(String scriptName) {
        String base = scriptName;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        base = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        return "com/mira/compiled/" + base;
    }

    private static void emitIntConst(MethodVisitor mv, int n) {
        if (n >= 0 && n <= 5) {
            mv.visitInsn(ICONST_0 + n);
        } else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) {
            mv.visitIntInsn(org.objectweb.asm.Opcodes.BIPUSH, n);
        } else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) {
            mv.visitIntInsn(org.objectweb.asm.Opcodes.SIPUSH, n);
        } else {
            mv.visitLdcInsn(n);
        }
    }
}
