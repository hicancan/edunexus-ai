import axios, { type AxiosResponse } from "axios";
import type { ApiEnvelope } from "./contracts";

const fallbackBaseURL = `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;
const legacyRefreshTokenKey = "refresh_token";

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export const storageKeys = {
  accessToken: "token",
  refreshToken: "refreshToken",
  user: "user"
} as const;

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || fallbackBaseURL,
  timeout: 20000
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(storageKeys.accessToken);
  const headers = config.headers;

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  headers["X-Request-Id"] = headers["X-Request-Id"] || createRequestId();

  const method = (config.method || "get").toUpperCase();
  if (["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
    headers["Idempotency-Key"] = headers["Idempotency-Key"] || createRequestId();
  }

  (config as { __startedAt?: number }).__startedAt = Date.now();

  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    const startedAt = (response.config as { __startedAt?: number }).__startedAt ?? Date.now();
    const traceId = (response.data as ApiEnvelope | undefined)?.traceId;

    console.info(
      "[web-api]",
      JSON.stringify({
        route: response.config.url,
        action: (response.config.method || "get").toUpperCase(),
        latency: Date.now() - startedAt,
        traceId: traceId || response.headers["x-trace-id"] || ""
      })
    );

    return response;
  },
  (error) => {
    const status = error?.response?.status;
    if (status === 401) {
      localStorage.removeItem(storageKeys.accessToken);
      localStorage.removeItem(storageKeys.refreshToken);
      localStorage.removeItem(legacyRefreshTokenKey);
      localStorage.removeItem(storageKeys.user);
    }
    return Promise.reject(error);
  }
);

export function unwrapResponse<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  const payload = response.data;

  if (!payload || typeof payload.code !== "number") {
    throw new Error("接口响应格式不合法");
  }

  if (payload.code >= 400) {
    throw new Error(payload.message || "接口调用失败");
  }

  return payload.data as T;
}
