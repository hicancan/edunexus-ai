package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthContext;
import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.TraceFilter;
import jakarta.servlet.http.HttpServletRequest;

public interface ControllerSupport {
    default AuthUser currentUser() {
        AuthUser user = AuthContext.get();
        if (user == null) throw new SecurityException("未认证");
        return user;
    }

    default void requireRole(String role) {
        AuthUser user = currentUser();
        if (!role.equals(user.role())) throw new SecurityException("无权限");
    }

    default String trace(HttpServletRequest request) {
        Object o = request.getAttribute(TraceFilter.TRACE_ID);
        return o == null ? "" : o.toString();
    }
}
