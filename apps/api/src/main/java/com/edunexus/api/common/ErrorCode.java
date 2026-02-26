package com.edunexus.api.common;

/**
 * 结构化错误码枚举 — 对齐 doc/10-开发约束 §4.3 + 前端 error-message.ts 映射表。
 */
public enum ErrorCode {
    // Auth
    AUTH_INVALID_CREDENTIALS(401, "用户名或密码错误"),
    AUTH_TOKEN_EXPIRED(401, "登录已过期"),
    AUTH_TOKEN_INVALID(401, "登录凭证无效"),
    AUTH_ACCOUNT_DISABLED(403, "账号已禁用"),

    // Permission
    PERMISSION_DENIED(403, "无角色权限"),
    PERMISSION_OWNERSHIP(403, "非资源归属者"),

    // Validation
    VALIDATION_FIELD(400, "输入字段校验失败"),
    VALIDATION_PARAM(400, "参数类型错误"),

    // Resource
    RESOURCE_NOT_FOUND(404, "资源不存在"),
    RESOURCE_CONFLICT(409, "资源冲突"),

    // AI & Dependency
    AI_MODEL_UNAVAILABLE(503, "AI 模型暂不可用"),
    AI_OUTPUT_INVALID(502, "AI 返回格式异常"),
    SYSTEM_DEPENDENCY(503, "依赖服务不可用"),

    // System
    SYSTEM_INTERNAL(500, "系统繁忙");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() { return httpStatus; }
    public String defaultMessage() { return defaultMessage; }
}
