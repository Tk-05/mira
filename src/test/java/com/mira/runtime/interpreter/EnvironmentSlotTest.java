package com.mira.runtime.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;

/**
 * Phase 0 of the resolver/slot-based-scope architecture: proves the slot-mode
 * {@link Environment} storage works correctly in isolation, before anything in
 * the interpreter depends on it.
 */
public class EnvironmentSlotTest {

    @Test
    void getAtAssignAtDefineAtRoundtrip() {
        Environment env = new Environment(null, new String[]{"a", "b"});
        env.defineAt(0, 1L, false);
        env.defineAt(1, "hi", false);

        assertEquals(1L, env.getAt(0, 0));
        assertEquals("hi", env.getAt(0, 1));

        env.assignAt(0, 0, 2L);
        assertEquals(2L, env.getAt(0, 0));
    }

    @Test
    void getAtWalksParentChainByDistance() {
        Environment outer = new Environment(null, new String[]{"x"});
        outer.defineAt(0, 10L, false);
        Environment inner = new Environment(outer, new String[]{"y"});
        inner.defineAt(0, 20L, false);

        assertEquals(20L, inner.getAt(0, 0));
        assertEquals(10L, inner.getAt(1, 0));
    }

    @Test
    void assignAtConstSlotThrows() {
        Environment env = new Environment(null, new String[]{"c"});
        env.defineAt(0, 1L, true);

        assertThrows(ReferenceIsImmutableError.class, () -> env.assignAt(0, 0, 2L));
        assertEquals(1L, env.getAt(0, 0));
    }

    @Test
    void assignAtWalksParentChainByDistance() {
        Environment outer = new Environment(null, new String[]{"x"});
        outer.defineAt(0, 1L, false);
        Environment inner = new Environment(outer, new String[]{"y"});
        inner.defineAt(0, 2L, false);

        inner.assignAt(1, 0, 99L);
        assertEquals(99L, outer.getAt(0, 0));
    }

    @Test
    void snapshotProducesIndependentSlotArray() {
        Environment parent = new Environment(null, new String[]{"p"});
        parent.defineAt(0, 1L, false);
        Environment scope = new Environment(parent, new String[]{"i"});
        scope.defineAt(0, 0L, false);

        Environment snap = scope.snapshot(parent);
        scope.assignAt(0, 0, 999L);
        assertEquals(0L, snap.getAt(0, 0), "mutating the original after snapshot must not affect the copy");

        snap.assignAt(0, 0, -1L);
        assertEquals(999L, scope.getAt(0, 0), "mutating the copy must not affect the original");
    }

    @Test
    void snapshotStopsAtGlobalEnvWithoutCopying() {
        Environment global = new Environment(null, new String[]{"g"});
        global.defineAt(0, 1L, false);
        Environment scope = new Environment(global, new String[]{"i"});
        scope.defineAt(0, 5L, false);

        Environment snap = scope.snapshot(global);
        // The parent link of the snapshot must be the *same* global instance, not
        // a copy - global state is shared by reference, only local scopes are
        // structurally copied.
        assertEquals(1L, snap.getAt(1, 0));
        global.assignAt(0, 0, 42L);
        assertEquals(42L, snap.getAt(1, 0), "global mutations must be visible through the snapshot's parent link");
    }

    @Test
    void copyShallowCopiesSlotArrayIndependently() {
        Environment env = new Environment(null, new String[]{"a"});
        env.defineAt(0, 1L, false);

        Environment copy = env.copyShallow();
        copy.assignAt(0, 0, 2L);

        assertEquals(1L, env.getAt(0, 0));
        assertEquals(2L, copy.getAt(0, 0));
    }

    @Test
    void nameBasedGetWorksAgainstSlotInstance() {
        Environment env = new Environment(null, new String[]{"foo", "bar"});
        env.defineAt(0, 1L, false);
        env.defineAt(1, 2L, false);

        assertEquals(1L, env.get("foo"));
        assertEquals(2L, env.get("bar"));
    }

    @Test
    void nameBasedGetFallsBackToParentAndThenThrows() {
        Environment outer = new Environment(null, new String[]{"x"});
        outer.defineAt(0, 10L, false);
        Environment inner = new Environment(outer, new String[]{"y"});
        inner.defineAt(0, 20L, false);

        assertEquals(10L, inner.get("x"));
        assertThrows(UndefinedReferenceError.class, () -> inner.get("nope"));
    }

    @Test
    void nameBasedGetOrNull() {
        Environment env = new Environment(null, new String[]{"a"});
        env.defineAt(0, 1L, false);

        assertEquals(1L, env.getOrNull("a"));
        assertNull(env.getOrNull("missing"));
    }

    @Test
    void nameBasedAssignRespectsConst() {
        Environment env = new Environment(null, new String[]{"a"});
        env.defineAt(0, 1L, true);

        assertThrows(ReferenceIsImmutableError.class, () -> env.assign("a", 2L));
    }

    @Test
    void nameBasedExistsAndIsConst() {
        Environment env = new Environment(null, new String[]{"a", "b"});
        env.defineAt(0, 1L, true);
        env.defineAt(1, 2L, false);

        assertTrue(env.exists("a"));
        assertTrue(env.exists("b"));
        assertFalse(env.exists("c"));
        assertTrue(env.isConst("a"));
        assertFalse(env.isConst("b"));
    }

    @Test
    void nameBasedExistsInChain() {
        Environment outer = new Environment(null, new String[]{"x"});
        outer.defineAt(0, 1L, false);
        Environment inner = new Environment(outer, new String[]{"y"});
        inner.defineAt(0, 2L, false);

        assertTrue(inner.existsInChain("x"));
        assertTrue(inner.existsInChain("y"));
        assertFalse(inner.existsInChain("z"));
    }

    @Test
    void keySetAndGetDefinedNames() {
        Environment env = new Environment(null, new String[]{"a", "b"});
        env.defineAt(0, 1L, false);
        env.defineAt(1, 2L, false);

        assertEquals(Set.of("a", "b"), env.keySet());
        assertEquals(Set.of("a", "b"), env.getDefinedNames());
    }

    @Test
    void getLocalValuesReflectsSlotContents() {
        Environment env = new Environment(null, new String[]{"a", "b"});
        env.defineAt(0, 1L, false);
        env.defineAt(1, "x", false);

        Map<String, Object> local = env.getLocalValues();
        assertEquals(1L, local.get("a"));
        assertEquals("x", local.get("b"));
        assertEquals(2, local.size());
    }

    @Test
    void getSizeReflectsSlotCount() {
        Environment env = new Environment(null, new String[]{"a", "b", "c"});
        assertEquals(3, env.getSize());
    }

    @Test
    void forceDefineOverwritesAndClearsConst() {
        Environment env = new Environment(null, new String[]{"a"});
        env.defineAt(0, 1L, true);

        env.forceDefine("a", 2L);
        assertEquals(2L, env.get("a"));
        assertFalse(env.isConst("a"));
    }

    @Test
    void unknownNameOnSlotInstanceThrowsUndefinedReference() {
        Environment env = new Environment(null, new String[]{"a"});
        assertThrows(UndefinedReferenceError.class, () -> env.define("nope", 1L));
        assertThrows(UndefinedReferenceError.class, () -> env.defineConst("nope", 1L));
        assertThrows(UndefinedReferenceError.class, () -> env.forceDefine("nope", 1L));
    }

    @Test
    void hashMapModeIsCompletelyUnaffected() {
        Environment env = new Environment();
        env.define("x", 1L);
        env.defineConst("y", 2L);

        assertEquals(1L, env.get("x"));
        assertEquals(2L, env.get("y"));
        assertTrue(env.isConst("y"));
        assertThrows(ReferenceIsImmutableError.class, () -> env.assign("y", 3L));

        Environment copy = env.copyShallow();
        env.assign("x", 99L);
        assertEquals(1L, copy.get("x"), "copyShallow must still deep-copy the HashMap-mode values map");
    }
}
