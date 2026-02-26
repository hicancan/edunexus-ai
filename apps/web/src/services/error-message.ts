import type { AxiosError } from "axios";

const errorMessageMap: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: "用户名或密码错误",
  AUTH_TOKEN_EXPIRED: "登录已过期，请重新登录",
  AUTH_TOKEN_INVALID: "登录凭证无效，请重新登录",
  PERMISSION_DENIED: "你没有执行该操作的权限",
  PERMISSION_OWNERSHIP: "你无法访问不属于你的资源",
  VALIDATION_FIELD: "输入字段校验失败，请检查表单",
  VALIDATION_PARAM: "查询参数不合法，请修正后重试",
  RESOURCE_NOT_FOUND: "请求的资源不存在或已被移除",
  RESOURCE_CONFLICT: "资源已存在，请勿重复提交",
  AI_MODEL_UNAVAILABLE: "AI 模型暂不可用，请稍后重试",
  AI_OUTPUT_INVALID: "AI 返回格式异常，请重试",
  AI_RATE_LIMITED: "当前请求较多，请稍后重试",
  SYSTEM_INTERNAL: "系统繁忙，请稍后再试",
  SYSTEM_DEPENDENCY: "依赖服务不可用，请稍后再试"
};

type ApiErrorBody = {
  code?: string | number;
  message?: string;
  traceId?: string;
};

export function toErrorMessage(error: unknown, fallback = "操作失败，请稍后重试"): string {
  const axiosError = error as AxiosError<ApiErrorBody>;
  const body = axiosError?.response?.data;

  if (body?.code) {
    const code = String(body.code);
    if (errorMessageMap[code]) {
      return errorMessageMap[code];
    }
  }

  if (body?.message) {
    return body.message;
  }

  if (axiosError?.message) {
    return axiosError.message;
  }

  return fallback;
}
