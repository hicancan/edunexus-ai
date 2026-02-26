import type { components } from "../types/api";

type Schemas = components["schemas"];

export type Role = NonNullable<Schemas["UserVO"]["role"]>;
export type UserVO = Schemas["UserVO"];
export type LoginData = Schemas["LoginData"];
export type RegisterRequest = Schemas["RegisterRequest"];
export type LoginRequest = Schemas["LoginRequest"];
export type ChatSessionVO = Schemas["ChatSessionVO"];
export type ChatSessionDetailVO = Schemas["ChatSessionDetailVO"];
export type ChatReplyVO = Schemas["ChatReplyVO"];
export type QuestionVO = Schemas["QuestionVO"];
export type ExerciseResultVO = Schemas["ExerciseResultVO"];
export type ExerciseAnalysisVO = Schemas["ExerciseAnalysisVO"];
export type WrongBookEntryVO = Schemas["WrongBookEntryVO"];
export type ExerciseRecordVO = Schemas["ExerciseRecordVO"];
export type AiQuestionGenerateRequest = Schemas["AiQuestionGenerateRequest"];
export type AiQuestionGenerateResultVO = Schemas["AiQuestionGenerateResultVO"];
export type AiQuestionResultVO = Schemas["AiQuestionResultVO"];
export type AiQuestionAnalysisVO = Schemas["AiQuestionAnalysisVO"];
export type AiQuestionSessionVO = Schemas["AiQuestionSessionVO"];
export type DocumentVO = Schemas["DocumentVO"];
export type LessonPlanVO = Schemas["LessonPlanVO"];
export type ShareResultVO = Schemas["ShareResultVO"];
export type StudentAnalyticsVO = Schemas["StudentAnalyticsVO"];
export type TeacherSuggestionRequest = Schemas["TeacherSuggestionRequest"];
export type AuditLogVO = Schemas["AuditLogVO"];
export type DashboardMetricsVO = Schemas["DashboardMetricsVO"];
export type AdminResourceVO = Schemas["AdminResourceVO"];
export type AdminUserCreateRequest = Schemas["AdminUserCreateRequest"];

export interface ApiEnvelope<T = unknown> {
  code: number;
  message: string;
  data?: T;
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
    list?: unknown[];
    page?: number;
    size?: number;
    totalElements?: number;
    totalPages?: number;
  };

  const content = Array.isArray(raw.content)
    ? raw.content
    : Array.isArray(raw.list)
      ? raw.list
      : [];

  const page = Math.max(1, Number(raw.page ?? 1));
  const size = Math.max(1, Number(raw.size ?? (content.length || 20)));
  const totalElements = Number(raw.totalElements ?? content.length ?? 0);
  const totalPages = Math.max(1, Number(raw.totalPages ?? Math.ceil(totalElements / size)));

  return {
    content: content as T[],
    page,
    size,
    totalElements,
    totalPages
  };
}
