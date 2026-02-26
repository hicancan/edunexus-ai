import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import type { ApiEnvelope, RefreshData } from "./contracts";

const fallbackBaseURL = `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;
const legacyRefreshTokenKey = "refresh_token";

interface RequestMetrics {
  __startedAt?: number;
  __retry401?: boolean;
}

export class ApiClientError extends Error {
  readonly code?: string | number;
  readonly status?: number;
  readonly traceId?: string;

  constructor(message: string, options?: { code?: string | number; status?: number; traceId?: string }) {
    super(message);
    this.name = "ApiClientError";
    this.code = options?.code;
    this.status = options?.status;
    this.traceId = options?.traceId;
  }
}

export const storageKeys = {
  accessToken: "token",
  refreshToken: "refreshToken",
  user: "user"
} as const;

const baseURL = import.meta.env.VITE_API_BASE_URL || fallbackBaseURL;

const refreshClient = axios.create({
  baseURL,
  timeout: 20000
});

export const apiClient = axios.create({
  baseURL,
  timeout: 20000
});

let refreshPromise: Promise<RefreshData | null> | null = null;

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function setHeader(config: InternalAxiosRequestConfig, key: string, value: string): void {
  if (typeof config.headers?.set === "function") {
    config.headers.set(key, value);
    return;
  }
  (config.headers as Record<string, string>)[key] = value;
}

function hasHeader(config: InternalAxiosRequestConfig, key: string): boolean {
  if (typeof config.headers?.has === "function") {
    return config.headers.has(key);
  }
  const headers = config.headers as Record<string, unknown>;
  return headers[key] !== undefined;
}

function isAuthEndpoint(url?: string): boolean {
  return Boolean(url && /^\/auth\/(login|register|refresh)$/.test(url));
}

function readAccessToken(): string {
  return localStorage.getItem(storageKeys.accessToken) || "";
}

function readRefreshToken(): string {
  return localStorage.getItem(storageKeys.refreshToken) || localStorage.getItem(legacyRefreshTokenKey) || "";
}

function persistTokens(data: RefreshData): void {
  localStorage.setItem(storageKeys.accessToken, data.accessToken);
  localStorage.setItem(storageKeys.refreshToken, data.refreshToken);
  localStorage.removeItem(legacyRefreshTokenKey);
}

export function clearSessionStorage(): void {
  localStorage.removeItem(storageKeys.accessToken);
  localStorage.removeItem(storageKeys.refreshToken);
  localStorage.removeItem(legacyRefreshTokenKey);
  localStorage.removeItem(storageKeys.user);
}

async function refreshAccessToken(): Promise<RefreshData | null> {
  const refreshToken = readRefreshToken();
  if (!refreshToken) {
    return null;
  }

  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiEnvelope<RefreshData>>("/auth/refresh", { refreshToken })
      .then((response) => unwrapResponse(response))
      .then((tokens) => {
        persistTokens(tokens);
        return tokens;
      })
      .catch(() => {
        clearSessionStorage();
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

apiClient.interceptors.request.use((config) => {
  const requestConfig = config as InternalAxiosRequestConfig & RequestMetrics;
  requestConfig.__startedAt = Date.now();

  const token = readAccessToken();
  if (token) {
    setHeader(requestConfig, "Authorization", `Bearer ${token}`);
  }

  if (!hasHeader(requestConfig, "X-Request-Id")) {
    setHeader(requestConfig, "X-Request-Id", createRequestId());
  }

  const method = (requestConfig.method || "get").toUpperCase();
  if (["POST", "PUT", "PATCH", "DELETE"].includes(method) && !hasHeader(requestConfig, "Idempotency-Key")) {
    setHeader(requestConfig, "Idempotency-Key", createRequestId());
  }

  return requestConfig;
});

apiClient.interceptors.response.use(
  (response) => {
    const requestConfig = response.config as AxiosRequestConfig & RequestMetrics;
    const startedAt = requestConfig.__startedAt ?? Date.now();
    const traceId = (response.data as ApiEnvelope | undefined)?.traceId || response.headers["x-trace-id"] || "";

    console.info(
      "[web-api]",
      JSON.stringify({
        route: response.config.url,
        action: (response.config.method || "get").toUpperCase(),
        latency: Date.now() - startedAt,
        traceId
      })
    );

    return response;
  },
  async (error: AxiosError<ApiEnvelope>) => {
    const requestConfig = (error.config || {}) as InternalAxiosRequestConfig & RequestMetrics;
    const status = error.response?.status;

    if (status === 401 && !requestConfig.__retry401 && !isAuthEndpoint(requestConfig.url)) {
      requestConfig.__retry401 = true;
      const refreshed = await refreshAccessToken();
      if (refreshed?.accessToken) {
        setHeader(requestConfig, "Authorization", `Bearer ${refreshed.accessToken}`);
        return apiClient.request(requestConfig);
      }
    }

    if (status === 401) {
      clearSessionStorage();
    }

    return Promise.reject(error);
  }
);

export function unwrapResponse<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  const payload = response.data;
  if (!payload || typeof payload !== "object") {
    throw new ApiClientError("接口响应格式不合法", { status: response.status });
  }

  if (typeof payload.code === "number" && payload.code >= 400) {
    throw new ApiClientError(payload.message || "接口调用失败", {
      code: payload.code,
      status: response.status,
      traceId: payload.traceId
    });
  }

  return payload.data as T;
}
