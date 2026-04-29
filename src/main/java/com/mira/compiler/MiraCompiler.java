package com.mira.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

import com.mira.Flags;
import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.ModuleDecl;

public class MiraCompiler {

    public record CompileResult(byte[] mainClass, Map<String, byte[]> lambdaClasses, String className) {}

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
        for (Node node : ast) {
            if (node instanceof FuncDecl fd) {
                knownFunctions.add(fd.getName());
            }
        }

        emitStaticInit(ce, className, knownFunctions, lambdaCounter, ast);

        emitMain(ce, className, knownFunctions, lambdaCounter, ast);

        for (Node node : ast) {
            if (node instanceof FuncDecl fd) {
                emitTopLevelFunction(ce, className, knownFunctions, lambdaCounter, fd);
            }
        }

        byte[] mainBytes = ce.finish();
        Map<String, byte[]> extras = new HashMap<>(ce.getExtraClasses());
        return new CompileResult(mainBytes, extras, className);
    }

    // ------------------------------------------------------------------ clinit

    private void emitStaticInit(ClassEmitter ce, String className,
            Set<String> knownFunctions, int[] lambdaCounter, List<Node> ast) {
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
            if (node instanceof FuncDecl fd) {
                String mName = "mira$" + fd.getName();
                String lClass = className + "$Lambda$fn$" + fd.getName();
                ce.emitLambdaClass(lClass, className, mName, fd.getArity());

                mv.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, className, "GLOBALS", ENV_D);
                mv.visitLdcInsn(fd.getName());
                mv.visitTypeInsn(NEW, lClass);
                mv.visitInsn(DUP);
                emitIntConst(mv, fd.getArity());
                mv.visitMethodInsn(INVOKESPECIAL, lClass, "<init>", "(I)V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, ENV, "define",
                        "(Ljava/lang/String;" + OBJ_D + ")V", false);
            }
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitMain(ClassEmitter ce, String className,
            Set<String> knownFunctions, int[] lambdaCounter, List<Node> ast) {
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
            if (node instanceof FuncDecl || node instanceof ModuleDecl) continue;
            emitter.emitNode(node);
        }

        if (Flags.mainFunction && knownFunctions.contains("main")) {
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
            Set<String> knownFunctions, int[] lambdaCounter, FuncDecl fd) {
        String mName = "mira$" + fd.getName();
        MethodVisitor mv = ce.openFunction(mName);
        mv.visitCode();

        LocalSlotTable slots = new LocalSlotTable(1);
        CompilerContext ctx = new CompilerContext(className, mv, slots,
                knownFunctions, lambdaCounter, false);
        MethodEmitter emitter = new MethodEmitter(ctx, ce);

        List<Parameter> params = fd.getParameters();
        for (int i = 0; i < params.size(); i++) {
            int slot = slots.allocate(params.get(i).name());
            mv.visitVarInsn(ALOAD, 0);
            emitIntConst(mv, i);
            mv.visitInsn(org.objectweb.asm.Opcodes.AALOAD);
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
    }

    static String toClassName(String scriptName) {
        String base = scriptName;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
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
