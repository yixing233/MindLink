package io.github.libxposed.api;

import java.lang.reflect.Executable;
import java.util.List;

/**
 * 编译期桩，不打包。只声明 MindLink 直接链接、且已在 WeKit（libxposed 102）中确认的成员；
 * 其余框架 API（hook 构建器、HookHandle、Invoker）一律经反射按名称调用，避免签名猜错。
 */
public interface XposedInterface {
    interface Chain {
        Executable getExecutable();
        Object getThisObject();
        List<Object> getArgs();
        Object proceed() throws Throwable;
        Object proceed(Object[] args) throws Throwable;
    }

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }
}
