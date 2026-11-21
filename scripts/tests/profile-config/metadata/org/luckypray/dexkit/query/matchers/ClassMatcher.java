package org.luckypray.dexkit.query.matchers;

/** Exact public String[] API from the bundled ClassMatcher smali; records metadata only. */
public final class ClassMatcher {
    public String[] equalStrings;
    public ClassMatcher usingEqStrings(String... values) { equalStrings = values.clone(); return this; }
}
