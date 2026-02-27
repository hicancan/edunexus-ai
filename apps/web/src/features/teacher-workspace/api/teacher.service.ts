import { apiClient, unwrapResponse } from "../../../services/api-client";
import {
  type ApiEnvelope,
  type DocumentStatus,
  type DocumentVO,
  type LessonPlanVO,
  type PagedResult,
  type PlanGenerateRequest,
  type PlanUpdateRequest,
  type ShareResultVO,
  type StudentAnalyticsVO,
  type TeacherSuggestionRequest,
  type TeacherSuggestionVO,
  normalizePagedResult
} from "../../../services/contracts";

export type ExportFormat = "md" | "pdf";

export interface PlanListQuery {
  page?: number;
  size?: number;
}

export async function listKnowledgeDocuments(status?: DocumentStatus): Promise<DocumentVO[]> {
  const response = await apiClient.get<ApiEnvelope<DocumentVO[]>>("/teacher/knowledge/documents", {
    params: status ? { status } : undefined
  });
  return unwrapResponse(response) || [];
}

export async function uploadKnowledgeDocument(file: File): Promise<DocumentVO> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiClient.post<ApiEnvelope<DocumentVO>>("/teacher/knowledge/documents", formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
  return unwrapResponse(response);
}

export async function deleteKnowledgeDocument(documentId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/knowledge/documents/${documentId}`);
}

export async function generatePlan(payload: PlanGenerateRequest): Promise<LessonPlanVO> {
  const response = await apiClient.post<ApiEnvelope<LessonPlanVO>>("/teacher/plans/generate", payload);
  return unwrapResponse(response);
}

export async function listPlans(params: PlanListQuery = {}): Promise<PagedResult<LessonPlanVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/teacher/plans", { params });
  return normalizePagedResult<LessonPlanVO>(unwrapResponse(response));
}

export async function updatePlan(planId: string, payload: PlanUpdateRequest): Promise<LessonPlanVO> {
  const response = await apiClient.put<ApiEnvelope<LessonPlanVO>>(`/teacher/plans/${planId}`, payload);
  return unwrapResponse(response);
}

export async function deletePlan(planId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/plans/${planId}`);
}

export async function sharePlan(planId: string): Promise<ShareResultVO> {
  const response = await apiClient.post<ApiEnvelope<ShareResultVO>>(`/teacher/plans/${planId}/share`);
  return unwrapResponse(response);
}

export async function exportPlan(planId: string, format: ExportFormat): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/teacher/plans/${planId}/export`, {
    params: { format },
    responseType: "blob"
  });
  return response.data;
}

export async function getStudentAnalytics(studentId: string): Promise<StudentAnalyticsVO> {
  const response = await apiClient.get<ApiEnvelope<StudentAnalyticsVO>>(`/teacher/students/${studentId}/analytics`);
  return unwrapResponse(response);
}

export async function createTeacherSuggestion(payload: TeacherSuggestionRequest): Promise<TeacherSuggestionVO> {
  const response = await apiClient.post<ApiEnvelope<TeacherSuggestionVO>>("/teacher/suggestions", payload);
  return unwrapResponse(response);
}
