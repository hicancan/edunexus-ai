import { apiClient, unwrapResponse } from "./api-client";
import {
  type AdminResourceVO,
  type AdminUserCreateRequest,
  type ApiEnvelope,
  type AuditLogVO,
  type DashboardMetricsVO,
  type PagedResult,
  type UserVO,
  normalizePagedResult
} from "./contracts";

type ResourceType = "LESSON_PLAN" | "QUESTION" | "DOCUMENT";

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function normalizeResource(raw: unknown): AdminResourceVO {
  const row = asRecord(raw);
  return {
    resourceId: asString(row.resourceId || row.id),
    resourceType: asString(row.resourceType || row.type, "QUESTION") as AdminResourceVO["resourceType"],
    title: asString(row.title || row.name),
    creatorId: asString(row.creatorId),
    creatorUsername: asString(row.creatorUsername),
    createdAt: asString(row.createdAt || row.updatedAt)
  };
}

export interface UserListQuery {
  page?: number;
  size?: number;
  role?: "STUDENT" | "TEACHER" | "ADMIN";
  status?: "ACTIVE" | "DISABLED";
}

export interface ResourceListQuery {
  page?: number;
  size?: number;
  resourceType: ResourceType;
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

export async function patchUser(userId: string, payload: { role?: string; status?: "ACTIVE" | "DISABLED" }): Promise<UserVO> {
  const response = await apiClient.patch<ApiEnvelope<UserVO>>(`/admin/users/${userId}`, payload);
  return unwrapResponse(response);
}

export async function listResources(params: ResourceListQuery): Promise<PagedResult<AdminResourceVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/admin/resources", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeResource)
  };
}

export async function downloadResource(resourceId: string) {
  return apiClient.get(`/admin/resources/${resourceId}/download`, { responseType: "blob" });
}

export async function listAudits(params: AuditListQuery = {}): Promise<PagedResult<AuditLogVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/admin/audits", { params });
  return normalizePagedResult<AuditLogVO>(unwrapResponse(response));
}

export async function getDashboardMetrics(): Promise<DashboardMetricsVO> {
  const response = await apiClient.get<ApiEnvelope<DashboardMetricsVO>>("/admin/dashboard/metrics");
  return unwrapResponse(response);
}
