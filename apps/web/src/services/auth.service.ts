import { apiClient, unwrapResponse } from "./api-client";
import type { ApiEnvelope, LoginData, LoginRequest, RegisterRequest, UserVO } from "./contracts";

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

export async function register(payload: RegisterRequest): Promise<UserVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>("/auth/register", payload);
  const data = asRecord(unwrapResponse(response));

  if (!data.id && data.userId) {
    return {
      id: asString(data.userId),
      username: payload.username,
      role: payload.role,
      status: "ACTIVE",
      email: payload.email,
      phone: payload.phone
    };
  }

  return data as UserVO;
}

export async function login(payload: LoginRequest): Promise<LoginData> {
  const response = await apiClient.post<ApiEnvelope<LoginData>>("/auth/login", payload);
  return unwrapResponse(response);
}

export async function logout(): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>("/auth/logout");
}

export async function refresh(refreshToken: string): Promise<Pick<LoginData, "accessToken" | "refreshToken">> {
  const response = await apiClient.post<ApiEnvelope<Pick<LoginData, "accessToken" | "refreshToken">>>("/auth/refresh", {
    refreshToken
  });
  return unwrapResponse(response);
}

export async function getMe(): Promise<UserVO> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/auth/me");
  const data = asRecord(unwrapResponse(response));
  return {
    id: asString(data.id),
    username: asString(data.username),
    role: asString(data.role) as UserVO["role"],
    status: asString(data.status) as UserVO["status"],
    email: asString(data.email),
    phone: asString(data.phone),
    createdAt: asString(data.createdAt),
    updatedAt: asString(data.updatedAt)
  };
}
