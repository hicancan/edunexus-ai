package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthContext;
import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.common.ForbiddenException;
import com.edunexus.api.common.TraceFilter;
import com.edunexus.api.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;
import java.util.UUID;

public interface ControllerSupport {
    default AuthUser currentUser() {
        AuthUser user = AuthContext.get();
        if (user == null) {
            throw new UnauthorizedException("未认证");
        }
        return user;
    }

    default void requireRole(String role) {
        AuthUser user = currentUser();
        if (!role.equals(user.role())) {
            throw new ForbiddenException(ErrorCode.PERMISSION_DENIED, "无角色权限");
        }
    }

    default void requireAnyRole(String... roles) {
        AuthUser user = currentUser();
        Set<String> allow = Set.of(roles);
        if (!allow.contains(user.role())) {
            throw new ForbiddenException(ErrorCode.PERMISSION_DENIED, "无角色权限");
        }
    }

    default String trace(HttpServletRequest request) {
        Object o = request.getAttribute(TraceFilter.TRACE_ID);
        if (o != null && !o.toString().isBlank()) {
            return o.toString();
        }
        String fromHeader = request.getHeader("X-Request-Id");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        fromHeader = request.getHeader("X-Trace-Id");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        return UUID.randomUUID().toString();
    }
}
