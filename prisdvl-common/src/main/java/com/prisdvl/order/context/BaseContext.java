package com.prisdvl.order.context;

/**
 * 当前登录主体上下文。
 *
 * <p>JWT 拦截器在 preHandle 中写入，请求结束后必须在 afterCompletion 中 remove，
 * 否则 Tomcat 线程复用会把上一个请求的身份带到下一个请求。</p>
 */
public class BaseContext {

    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    private BaseContext() {
    }

    public static void setCurrentId(Long id) {
        THREAD_LOCAL.set(id);
    }

    public static Long getCurrentId() {
        return THREAD_LOCAL.get();
    }

    public static void removeCurrentId() {
        THREAD_LOCAL.remove();
    }

}
