package com.mira.compiler;

import org.objectweb.asm.ClassWriter;

// Sibling classes from the same compile pass (e.g. two lambda branches of a ternary) aren't
// loadable yet when ASM tries to resolve their common supertype for a stack-map frame merge.
// Every generated class extends Object directly, so Object is always the correct fallback.
final class MiraClassWriter extends ClassWriter {

    MiraClassWriter(int flags) {
        super(flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (TypeNotPresentException e) {
            return "java/lang/Object";
        }
    }
}
