import { apiClient, unwrapResponse } from "../../../services/api-client";
import type { ApiEnvelope, LoginData, LoginRequest, RefreshData, RegisterRequest, UserVO } from "../../../services/contracts";

export async function register(payload: RegisterRequest): Promise<UserVO> {
  const response = await apiClient.post<ApiEnvelope<UserVO>>("/auth/register", payload);
  return unwrapResponse(response);
}

export async function login(payload: LoginRequest): Promise<LoginData> {
  const response = await apiClient.post<ApiEnvelope<LoginData>>("/auth/login", payload);
  return unwrapResponse(response);
}

export async function logout(): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>("/auth/logout");
}

export async function refresh(refreshToken: string): Promise<RefreshData> {
  const response = await apiClient.post<ApiEnvelope<RefreshData>>("/auth/refresh", {
    refreshToken
  });
  return unwrapResponse(response);
}

export async function getMe(): Promise<UserVO> {
  const response = await apiClient.get<ApiEnvelope<UserVO>>("/auth/me");
  return unwrapResponse(response);
}
