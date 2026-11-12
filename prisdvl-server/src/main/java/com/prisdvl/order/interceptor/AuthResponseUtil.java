package com.prisdvl.order.interceptor;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 拦截器统一 401 响应，避免前端拿到空响应体还要猜状态。
 */
public final class AuthResponseUtil {

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":0,\"msg\":\"未登录或登录已过期\"}";

    private AuthResponseUtil() {
    }

    public static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
