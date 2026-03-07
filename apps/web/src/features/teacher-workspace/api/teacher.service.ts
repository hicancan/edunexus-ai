import { apiClient, unwrapResponse } from "../../../services/api-client";
import {
  type ApiEnvelope,
  type BulkSuggestionRequest,
  type BulkSuggestionResultVO,
  type DocumentStatus,
  type DocumentVO,
  type InterventionRecommendationVO,
  type LessonPlanVO,
  type PagedResult,
  type PlanGenerateRequest,
  type PlanUpdateRequest,
  type ShareResultVO,
  type StudentAttributionVO,
  type StudentAnalyticsVO,
  type TeacherClassroomVO,
  type TeacherStudentVO,
  type TeacherSuggestionRequest,
  type TeacherSuggestionVO,
  normalizePagedResult
} from "../../../services/contracts";

export type ExportFormat = "md" | "pdf";

export interface PlanListQuery {
  page?: number;
  size?: number;
}

function resolveTimeoutMs(envValue: unknown, fallback: number): number {
  const parsed = Number(envValue);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

const KNOWLEDGE_UPLOAD_TIMEOUT_MS = resolveTimeoutMs(
  import.meta.env.VITE_KNOWLEDGE_UPLOAD_TIMEOUT_MS,
  180000
);
const LESSON_PLAN_TIMEOUT_MS = resolveTimeoutMs(
  import.meta.env.VITE_LESSON_PLAN_TIMEOUT_MS,
  120000
);

export async function listKnowledgeDocuments(status?: DocumentStatus): Promise<DocumentVO[]> {
  const response = await apiClient.get<ApiEnvelope<DocumentVO[]>>("/teacher/knowledge/documents", {
    params: status ? { status } : undefined
  });
  return unwrapResponse(response) || [];
}

export async function listTeacherClassrooms(): Promise<TeacherClassroomVO[]> {
  const response = await apiClient.get<ApiEnvelope<TeacherClassroomVO[]>>("/teacher/classrooms");
  return unwrapResponse(response) || [];
}

export async function listTeacherStudents(): Promise<TeacherStudentVO[]> {
  const response = await apiClient.get<ApiEnvelope<TeacherStudentVO[]>>("/teacher/students");
  return unwrapResponse(response) || [];
}

export async function listInterventionRecommendations(): Promise<InterventionRecommendationVO[]> {
  const response = await apiClient.get<ApiEnvelope<InterventionRecommendationVO[]>>(
    "/teacher/interventions/recommendations"
  );
  return unwrapResponse(response) || [];
}

export async function dispatchBulkSuggestion(
  payload: BulkSuggestionRequest
): Promise<BulkSuggestionResultVO> {
  const response = await apiClient.post<ApiEnvelope<BulkSuggestionResultVO>>(
    "/teacher/suggestions/bulk",
    payload
  );
  return unwrapResponse(response);
}

export async function uploadKnowledgeDocument(file: File, classId: string): Promise<DocumentVO> {
  const formData = new FormData();
  formData.append("classId", classId);
  formData.append("file", file, file.name);
  const response = await apiClient.post<ApiEnvelope<DocumentVO>>(
    "/teacher/knowledge/documents",
    formData,
    {
      timeout: KNOWLEDGE_UPLOAD_TIMEOUT_MS
    }
  );
  return unwrapResponse(response);
}

export async function deleteKnowledgeDocument(documentId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/knowledge/documents/${documentId}`);
}

export async function generatePlan(payload: PlanGenerateRequest): Promise<LessonPlanVO> {
  const response = await apiClient.post<ApiEnvelope<LessonPlanVO>>(
    "/teacher/plans/generate",
    payload,
    {
      timeout: LESSON_PLAN_TIMEOUT_MS
    }
  );
  return unwrapResponse(response);
}

export async function listPlans(params: PlanListQuery = {}): Promise<PagedResult<LessonPlanVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/teacher/plans", { params });
  return normalizePagedResult<LessonPlanVO>(unwrapResponse(response));
}

export async function updatePlan(
  planId: string,
  payload: PlanUpdateRequest
): Promise<LessonPlanVO> {
  const response = await apiClient.put<ApiEnvelope<LessonPlanVO>>(
    `/teacher/plans/${planId}`,
    payload
  );
  return unwrapResponse(response);
}

export async function deletePlan(planId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/teacher/plans/${planId}`);
}

export async function sharePlan(planId: string): Promise<ShareResultVO> {
  const response = await apiClient.post<ApiEnvelope<ShareResultVO>>(
    `/teacher/plans/${planId}/share`
  );
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
  const response = await apiClient.get<ApiEnvelope<StudentAnalyticsVO>>(
    `/teacher/students/${studentId}/analytics`
  );
  return unwrapResponse(response);
}

export async function getStudentAttribution(studentId: string): Promise<StudentAttributionVO> {
  const response = await apiClient.get<ApiEnvelope<StudentAttributionVO>>(
    `/teacher/students/${studentId}/attribution`
  );
  return unwrapResponse(response);
}

export async function createTeacherSuggestion(
  payload: TeacherSuggestionRequest
): Promise<TeacherSuggestionVO> {
  const response = await apiClient.post<ApiEnvelope<TeacherSuggestionVO>>(
    "/teacher/suggestions",
    payload
  );
  return unwrapResponse(response);
}
