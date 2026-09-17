package com.mira.lib;

import java.util.List;
import java.util.Map;

/**
 * Optional addition to {@link Lib}: declares which of the free functions it
 * already defines via {@link Lib#loadLib} should also be callable as instance
 * methods on a given {@link NativeType} (e.g. {@code "foo".upper()} instead of
 * {@code upper("foo")}). The receiver is always the method's implicit first
 * argument - the named functions keep working exactly as before as plain free
 * functions, this is purely an additional call syntax.
 * <p>
 * This is what lets a native type "own" its functions: the list here is the
 * single place that answers "what can a String do", instead of that being
 * scattered across anonymous {@code environment.define(...)} calls.
 */
public interface NativeMethodProvider {

    Map<NativeType, List<String>> nativeMethods();
}
