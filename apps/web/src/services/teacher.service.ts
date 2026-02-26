import { apiClient, unwrapResponse } from "./api-client";
import {
  type ApiEnvelope,
  type DocumentVO,
  type LessonPlanVO,
  type PagedResult,
  type ShareResultVO,
  type StudentAnalyticsVO,
  type TeacherSuggestionRequest,
  normalizePagedResult
} from "./contracts";

type ExportFormat = "md" | "pdf";

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function asArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return fallback;
}

function normalizeDocument(raw: unknown): DocumentVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id),
    filename: asString(row.filename),
    fileType: asString(row.fileType),
    fileSize: asNumber(row.fileSize, 0),
    status: asString(row.status) as DocumentVO["status"],
    errorMessage: asString(row.errorMessage) || null,
    createdAt: asString(row.createdAt),
    updatedAt: asString(row.updatedAt)
  };
}

function normalizeLessonPlan(raw: unknown): LessonPlanVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id),
    topic: asString(row.topic),
    gradeLevel: asString(row.gradeLevel),
    durationMins: asNumber(row.durationMins, 0),
    contentMd: asString(row.contentMd || row.content),
    isShared: Boolean(row.isShared),
    shareToken: asString(row.shareToken) || null,
    sharedAt: asString(row.sharedAt) || null,
    createdAt: asString(row.createdAt),
    updatedAt: asString(row.updatedAt)
  };
}

export interface PlanGeneratePayload {
  topic: string;
  gradeLevel: string;
  durationMins: number;
}

export interface PlanListQuery {
  page?: number;
  size?: number;
}

export async function listKnowledgeDocuments(page = 1, size = 20): Promise<PagedResult<DocumentVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/teacher/knowledge/documents", {
    params: { page, size }
  });
  const raw = unwrapResponse(response);
  if (Array.isArray(raw)) {
    return {
      content: raw.map(normalizeDocument),
      page: 1,
      size: raw.length,
      totalElements: raw.length,
      totalPages: raw.length > 0 ? 1 : 0
    };
  }

  const paged = normalizePagedResult<unknown>(raw);
  return {
    ...paged,
    content: paged.content.map(normalizeDocument)
  };
}

export async function uploadKnowledgeDocument(file: File): Promise<DocumentVO> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiClient.post<ApiEnvelope<DocumentVO>>("/teacher/knowledge/documents", formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
  return normalizeDocument(unwrapResponse(response));
}

export async function deleteKnowledgeDocument(documentId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/knowledge/documents/${documentId}`);
}

export async function generatePlan(payload: PlanGeneratePayload): Promise<LessonPlanVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>("/teacher/plans/generate", payload);
  return normalizeLessonPlan(unwrapResponse(response));
}

export async function listPlans(params: PlanListQuery = {}): Promise<PagedResult<LessonPlanVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/teacher/plans", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeLessonPlan)
  };
}

export async function updatePlan(planId: string, contentMd: string): Promise<LessonPlanVO> {
  const response = await apiClient.put<ApiEnvelope<unknown>>(`/teacher/plans/${planId}`, { contentMd });
  return normalizeLessonPlan({ id: planId, ...asRecord(unwrapResponse(response)), contentMd });
}

export async function deletePlan(planId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/plans/${planId}`);
}

export async function sharePlan(planId: string): Promise<ShareResultVO> {
  const response = await apiClient.post<ApiEnvelope<ShareResultVO>>(`/teacher/plans/${planId}/share`);
  return unwrapResponse(response);
}

export async function exportPlan(planId: string, format: ExportFormat) {
  return apiClient.get(`/teacher/plans/${planId}/export`, {
    params: { format },
    responseType: "blob"
  });
}

export async function getStudentAnalytics(studentId: string): Promise<StudentAnalyticsVO> {
  const response = await apiClient.get<ApiEnvelope<StudentAnalyticsVO>>(`/teacher/students/${studentId}/analytics`);
  return unwrapResponse(response);
}

export async function createTeacherSuggestion(payload: TeacherSuggestionRequest): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>("/teacher/suggestions", payload);
}
