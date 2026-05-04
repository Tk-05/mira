package com.mira.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CompilerContext {

    public final String className;

    public final MethodVisitor mv;
    public final LocalSlotTable slots;

    public final Set<String> knownFunctions;
    public final Set<String> localFunctions = new HashSet<>();

    public final int[] lambdaCounter;

    public final Deque<Label> breakStack = new ArrayDeque<>();
    public final Deque<Label> continueStack = new ArrayDeque<>();

    public final boolean isTopLevel;

    public int objectEnvSlot = -1;
    public int blockDepth = 0;

    public CompilerContext(String className, MethodVisitor mv, LocalSlotTable slots,
            Set<String> knownFunctions, int[] lambdaCounter, boolean isTopLevel) {
        this.className = className;
        this.mv = mv;
        this.slots = slots;
        this.knownFunctions = knownFunctions;
        this.lambdaCounter = lambdaCounter;
        this.isTopLevel = isTopLevel;
    }
}
