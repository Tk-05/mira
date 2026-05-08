package com.mira.compiler;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V21;

public class ClassEmitter {

    static final String ENV_DESC = "Lcom/mira/runtime/interpreter/Environment;";
    static final String ENV_NAME = "com/mira/runtime/interpreter/Environment";
    static final String RT_NAME = "com/mira/compiler/Runtime";
    static final String NULL_NAME = "com/mira/runtime/interpreter/NullValue";
    static final String THROW_NAME = "com/mira/runtime/functions/ThrowSignal";

    static final String FN_DESC = "([Ljava/lang/Object;)Ljava/lang/Object;";

    private final ClassWriter cw;
    private final String internalName;
    private final Map<String, byte[]> extraClasses = new HashMap<>();

    public ClassEmitter(String internalName) {
        this.internalName = internalName;
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL, internalName, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "GLOBALS", ENV_DESC, null, null).visitEnd();
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "NAMESPACES", ENV_DESC, null, null).visitEnd();
    }

    public String getInternalName() {
        return internalName;
    }

    public void declareCacheField(String funcName) {
        cw.visitField(ACC_PRIVATE | ACC_STATIC, "CACHE$" + funcName,
                "Ljava/util/concurrent/ConcurrentHashMap;", null, null).visitEnd();
    }

    public MethodVisitor openStaticInit() {
        return cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    }

    public MethodVisitor openMain() {
        return cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main",
                "([Ljava/lang/String;)V", null, null);
    }

    public MethodVisitor openFunction(String mangledName) {
        return cw.visitMethod(ACC_STATIC, mangledName, FN_DESC, null, null);
    }

    public void emitLambdaClass(String lambdaClassName, String outerClassName,
            String methodName, int arity) {
        ClassWriter lcw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        lcw.visit(V21, ACC_PUBLIC | ACC_FINAL, lambdaClassName, null,
                "java/lang/Object", new String[]{"com/mira/runtime/functions/Callable"});

        lcw.visitField(ACC_PRIVATE | ACC_FINAL, "arity", "I", null, null).visitEnd();

        MethodVisitor init = lcw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ILOAD, 1);
        init.visitFieldInsn(PUTFIELD, lambdaClassName, "arity", "I");
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor ga = lcw.visitMethod(ACC_PUBLIC, "getArity", "()I", null, null);
        ga.visitCode();
        ga.visitVarInsn(ALOAD, 0);
        ga.visitFieldInsn(GETFIELD, lambdaClassName, "arity", "I");
        ga.visitInsn(IRETURN);
        ga.visitMaxs(0, 0);
        ga.visitEnd();

        MethodVisitor call = lcw.visitMethod(ACC_PUBLIC, "call",
                "(Lcom/mira/runtime/interpreter/Interpreter;Ljava/util/List;)Ljava/lang/Object;",
                null, null);
        call.visitCode();
        call.visitVarInsn(ALOAD, 2);
        call.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "toArray", "()[Ljava/lang/Object;", true);
        call.visitMethodInsn(INVOKESTATIC, outerClassName, methodName, FN_DESC, false);
        call.visitInsn(ARETURN);
        call.visitMaxs(0, 0);
        call.visitEnd();

        lcw.visitEnd();
        extraClasses.put(lambdaClassName, lcw.toByteArray());
    }

    public void emitAsyncLambdaClass(String asyncClassName, String syncClassName, int arity) {
        ClassWriter acw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        acw.visit(V21, ACC_PUBLIC | ACC_FINAL, asyncClassName, null,
                "java/lang/Object", new String[]{"com/mira/runtime/functions/Callable"});

        acw.visitField(ACC_PRIVATE | ACC_FINAL, "arity", "I", null, null).visitEnd();

        MethodVisitor init = acw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ILOAD, 1);
        init.visitFieldInsn(PUTFIELD, asyncClassName, "arity", "I");
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor ga = acw.visitMethod(ACC_PUBLIC, "getArity", "()I", null, null);
        ga.visitCode();
        ga.visitVarInsn(ALOAD, 0);
        ga.visitFieldInsn(GETFIELD, asyncClassName, "arity", "I");
        ga.visitInsn(IRETURN);
        ga.visitMaxs(0, 0);
        ga.visitEnd();

        MethodVisitor call = acw.visitMethod(ACC_PUBLIC, "call",
                "(Lcom/mira/runtime/interpreter/Interpreter;Ljava/util/List;)Ljava/lang/Object;",
                null, null);
        call.visitCode();
        call.visitTypeInsn(NEW, syncClassName);
        call.visitInsn(DUP);
        call.visitVarInsn(ALOAD, 0);
        call.visitFieldInsn(GETFIELD, asyncClassName, "arity", "I");
        call.visitMethodInsn(INVOKESPECIAL, syncClassName, "<init>", "(I)V", false);
        call.visitVarInsn(ALOAD, 2);
        call.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "toArray", "()[Ljava/lang/Object;", true);
        call.visitMethodInsn(INVOKESTATIC, RT_NAME, "asyncWrap",
                "(Lcom/mira/runtime/functions/Callable;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        call.visitInsn(ARETURN);
        call.visitMaxs(0, 0);
        call.visitEnd();

        acw.visitEnd();
        extraClasses.put(asyncClassName, acw.toByteArray());
    }

    public byte[] finish() {
        cw.visitEnd();
        return cw.toByteArray();
    }

    public Map<String, byte[]> getExtraClasses() {
        return extraClasses;
    }
}
