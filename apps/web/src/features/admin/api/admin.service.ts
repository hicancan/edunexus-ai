import { apiClient, unwrapResponse } from "../../../services/api-client";
import {
  type AdminResourceVO,
  type AdminUserCreateRequest,
  type AdminUserPatchRequest,
  type ApiEnvelope,
  type AuditLogVO,
  type DashboardMetricsVO,
  type PagedResult,
  type ResourceType,
  type Role,
  type UserStatus,
  type UserVO,
  normalizePagedResult
} from "../../../services/contracts";

export interface UserListQuery {
  page?: number;
  size?: number;
  role?: Role;
  status?: UserStatus;
}

export interface ResourceListQuery {
  page?: number;
  size?: number;
  resourceType?: ResourceType;
}

export interface AuditListQuery {
  page?: number;
  size?: number;
}

export async function listUsers(params: UserListQuery = {}): Promise<PagedResult<UserVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/admin/users", { params });
  return normalizePagedResult<UserVO>(unwrapResponse(response));
}

export async function createUser(payload: AdminUserCreateRequest): Promise<UserVO> {
  const response = await apiClient.post<ApiEnvelope<UserVO>>("/admin/users", payload);
  return unwrapResponse(response);
}

export async function patchUser(userId: string, payload: AdminUserPatchRequest): Promise<UserVO> {
  const response = await apiClient.patch<ApiEnvelope<UserVO>>(`/admin/users/${userId}`, payload);
  return unwrapResponse(response);
}

export async function listResources(params: ResourceListQuery = {}): Promise<PagedResult<AdminResourceVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/admin/resources", { params });
  return normalizePagedResult<AdminResourceVO>(unwrapResponse(response));
}

export async function downloadResource(resourceId: string): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/admin/resources/${resourceId}/download`, {
    responseType: "blob"
  });
  return response.data;
}

export async function listAudits(params: AuditListQuery = {}): Promise<PagedResult<AuditLogVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/admin/audits", { params });
  return normalizePagedResult<AuditLogVO>(unwrapResponse(response));
}

export async function getDashboardMetrics(): Promise<DashboardMetricsVO> {
  const response = await apiClient.get<ApiEnvelope<DashboardMetricsVO>>("/admin/dashboard/metrics");
  return unwrapResponse(response);
}
