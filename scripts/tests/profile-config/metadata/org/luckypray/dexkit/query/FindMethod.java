package org.luckypray.dexkit.query;

import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Exact public finder metadata seam; production reflection chooses the method signatures. */
public final class FindMethod {
    public MethodMatcher target;
    public String[] packages;
    public FindMethod matcher(MethodMatcher value) { target = value; return this; }
    public FindMethod searchPackages(String... values) { packages = values.clone(); return this; }
}
