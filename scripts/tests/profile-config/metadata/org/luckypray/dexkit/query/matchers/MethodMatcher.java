package org.luckypray.dexkit.query.matchers;

/** Exact public matcher overloads invoked by real HostTargetResolver, not resolver logic. */
public final class MethodMatcher {
    public ClassMatcher owner;
    public String declaredName;
    public String[] strings;
    public String[] parameters;
    public String result;
    public int count = -1, access = -1;
    public MethodMatcher declaredClass(ClassMatcher value) { owner = value; return this; }
    public MethodMatcher declaredClass(String value) { declaredName = value; return this; }
    public MethodMatcher usingStrings(String... values) { strings = values.clone(); return this; }
    public MethodMatcher name(String value) { methodName = value; return this; }
    public String methodName;
    public MethodMatcher paramTypes(String... values) { parameters = values.clone(); return this; }
    public MethodMatcher returnType(String value) { result = value; return this; }
    public MethodMatcher paramCount(int value) { count = value; return this; }
    public MethodMatcher modifiers(int value) { access = value; return this; }
}
