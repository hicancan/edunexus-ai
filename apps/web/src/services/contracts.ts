import type { components } from "../types/api";

type Schemas = components["schemas"];

export type Role = NonNullable<Schemas["UserVO"]["role"]>;
export type UserStatus = NonNullable<Schemas["UserVO"]["status"]>;
export type Difficulty = NonNullable<Schemas["QuestionVO"]["difficulty"]>;
export type WrongStatus = NonNullable<Schemas["WrongBookEntryVO"]["status"]>;
export type DocumentStatus = NonNullable<Schemas["DocumentVO"]["status"]>;
export type ResourceType = NonNullable<Schemas["AdminResourceVO"]["resourceType"]>;
export type UserVO = Schemas["UserVO"];
export type LoginData = Schemas["LoginData"];
export type RefreshData = Schemas["RefreshData"];
export type RegisterRequest = Schemas["RegisterRequest"];
export type LoginRequest = Schemas["LoginRequest"];
export type ChatSessionVO = Schemas["ChatSessionVO"];
export type ChatSessionDetailVO = Schemas["ChatSessionDetailVO"];
export type ChatMessageVO = Schemas["ChatMessageVO"];
export type ChatReplyVO = Schemas["ChatReplyVO"];
export type QuestionVO = Schemas["QuestionVO"];
export type ExerciseSubmitRequest = Schemas["ExerciseSubmitRequest"];
export type ExerciseResultVO = Schemas["ExerciseResultVO"];
export type ExerciseAnalysisVO = Schemas["ExerciseAnalysisVO"];
export type WrongBookEntryVO = Schemas["WrongBookEntryVO"];
export type ExerciseRecordVO = Schemas["ExerciseRecordVO"];
export type AiQuestionGenerateRequest = Schemas["AiQuestionGenerateRequest"];
export type AiQuestionSubmitRequest = Schemas["AiQuestionSubmitRequest"];
export type AiQuestionGenerateResultVO = Schemas["AiQuestionGenerateResultVO"];
export type AiQuestionResultVO = Schemas["AiQuestionResultVO"];
export type AiQuestionAnalysisVO = Schemas["AiQuestionAnalysisVO"];
export type AiQuestionSessionVO = Schemas["AiQuestionSessionVO"];
export type DocumentVO = Schemas["DocumentVO"];
export type PlanGenerateRequest = Schemas["PlanGenerateRequest"];
export type PlanUpdateRequest = Schemas["PlanUpdateRequest"];
export type LessonPlanVO = Schemas["LessonPlanVO"];
export type ShareResultVO = Schemas["ShareResultVO"];
export type StudentAnalyticsVO = Schemas["StudentAnalyticsVO"];
export type TeacherSuggestionRequest = Schemas["TeacherSuggestionRequest"];
export type TeacherSuggestionVO = Schemas["TeacherSuggestionVO"];
export type AuditLogVO = Schemas["AuditLogVO"];
export type DashboardMetricsVO = Schemas["DashboardMetricsVO"];
export type AdminResourceVO = Schemas["AdminResourceVO"];
export type AdminUserCreateRequest = Schemas["AdminUserCreateRequest"];
export type AdminUserPatchRequest = Schemas["AdminUserPatchRequest"];

export interface ApiEnvelope<T = unknown> {
  code: number | string;
  message: string;
  data: T;
  traceId?: string;
  timestamp: string;
}

export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function normalizePagedResult<T>(rawData: unknown): PagedResult<T> {
  const raw = (rawData ?? {}) as {
    content?: unknown[];
    page?: number;
    size?: number;
    totalElements?: number;
    totalPages?: number;
  };

  const content = Array.isArray(raw.content) ? raw.content : [];

  const rawPage = Number(raw.page ?? 1);
  const rawSize = Number(raw.size ?? 20);
  const rawTotal = Number(raw.totalElements ?? content.length);
  const page = Number.isFinite(rawPage) && rawPage > 0 ? rawPage : 1;
  const size = Number.isFinite(rawSize) && rawSize > 0 ? rawSize : 20;
  const totalElements = Number.isFinite(rawTotal) && rawTotal >= 0 ? rawTotal : content.length;
  const rawTotalPages = Number(raw.totalPages ?? Math.ceil(totalElements / size));
  const totalPages = Number.isFinite(rawTotalPages) && rawTotalPages > 0 ? rawTotalPages : 1;

  return {
    content: content as T[],
    page,
    size,
    totalElements,
    totalPages
  };
}
