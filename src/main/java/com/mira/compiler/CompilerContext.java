package com.mira.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import com.mira.parser.nodes.Node;

public class CompilerContext {

    public final String className;

    public final MethodVisitor mv;
    public final LocalSlotTable slots;

    public final Set<String> knownFunctions;

    public final int[] lambdaCounter;

    public final Deque<Label> breakStack = new ArrayDeque<>();
    public final Deque<Label> continueStack = new ArrayDeque<>();

    public final Deque<ExecFrame> execStack = new ArrayDeque<>();

    public record ExecFrame(Label end, int resultSlot) {
    }

    public final boolean isTopLevel;

    public int objectEnvSlot = -1;
    public int blockDepth = 0;

    public int[] instrBytes = {0};
    public boolean isPartialExtract = false;

    public List<Node> currentUnitBody = List.of();

    // Compile-time constants embedded into profilerLine(...) calls (see
    // MethodEmitter).
    // Nested contexts (lambdas, split continuations) inherit these from their
    // parent.
    public String moduleName = "<script>";
    public String functionName = "<script>";

    public CompilerContext(String className, MethodVisitor mv, LocalSlotTable slots, Set<String> knownFunctions,
            int[] lambdaCounter, boolean isTopLevel) {
        this.className = className;
        this.mv = mv;
        this.slots = slots;
        this.knownFunctions = knownFunctions;
        this.lambdaCounter = lambdaCounter;
        this.isTopLevel = isTopLevel;
    }

    public CompilerContext(String className, MethodVisitor mv, LocalSlotTable slots, Set<String> knownFunctions,
            int[] lambdaCounter, boolean isTopLevel, int[] instrBytes) {
        this(className, mv, slots, knownFunctions, lambdaCounter, isTopLevel);
        this.instrBytes = instrBytes;
    }
}
