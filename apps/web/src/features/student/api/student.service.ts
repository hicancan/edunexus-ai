import { apiClient, unwrapResponse } from "../../../services/api-client";
import {
  type AiQuestionAnalysisVO,
  type AiQuestionGenerateRequest,
  type AiQuestionGenerateResultVO,
  type AiQuestionResultVO,
  type AiQuestionSessionVO,
  type ApiEnvelope,
  type ChatReplyVO,
  type ChatSessionDetailVO,
  type ChatSessionVO,
  type Difficulty,
  type ExerciseAnalysisVO,
  type ExerciseRecordVO,
  type ExerciseResultVO,
  type ExerciseSubmitRequest,
  type PagedResult,
  type QuestionVO,
  type WrongBookEntryVO,
  type WrongStatus,
  normalizePagedResult
} from "../../../services/contracts";

export interface ChatListQuery {
  page?: number;
  size?: number;
}

export interface ExerciseQuestionQuery {
  subject?: string;
  difficulty?: Difficulty;
  page?: number;
  size?: number;
}

export interface WrongBookQuery {
  subject?: string;
  status?: WrongStatus;
  page?: number;
  size?: number;
}

export interface ExerciseRecordQuery {
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

export interface AiHistoryQuery {
  subject?: string;
  page?: number;
  size?: number;
}

export async function listChatSessions(params: ChatListQuery = {}): Promise<PagedResult<ChatSessionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/chat/sessions", { params });
  return normalizePagedResult<ChatSessionVO>(unwrapResponse(response));
}

export async function createChatSession(): Promise<ChatSessionVO> {
  const response = await apiClient.post<ApiEnvelope<ChatSessionVO>>("/student/chat/session");
  return unwrapResponse(response);
}

export async function getChatSessionDetail(sessionId: string): Promise<ChatSessionDetailVO> {
  const response = await apiClient.get<ApiEnvelope<ChatSessionDetailVO>>(`/student/chat/session/${sessionId}`);
  return unwrapResponse(response);
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/chat/session/${sessionId}`);
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatReplyVO> {
  const response = await apiClient.post<ApiEnvelope<ChatReplyVO>>(`/student/chat/session/${sessionId}/message`, {
    message
  });
  return unwrapResponse(response);
}

export async function listExerciseQuestions(params: ExerciseQuestionQuery): Promise<PagedResult<QuestionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/questions", { params });
  return normalizePagedResult<QuestionVO>(unwrapResponse(response));
}

export async function submitExercise(answers: ExerciseSubmitRequest["answers"]): Promise<ExerciseResultVO> {
  const response = await apiClient.post<ApiEnvelope<ExerciseResultVO>>("/student/exercise/submit", {
    answers
  });
  return unwrapResponse(response);
}

export async function getExerciseAnalysis(recordId: string): Promise<ExerciseAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<ExerciseAnalysisVO>>(`/student/exercise/${recordId}/analysis`);
  return unwrapResponse(response);
}

export async function listWrongQuestions(params: WrongBookQuery): Promise<PagedResult<WrongBookEntryVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/wrong-questions", { params });
  return normalizePagedResult<WrongBookEntryVO>(unwrapResponse(response));
}

export async function removeWrongQuestion(questionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/exercise/wrong-questions/${questionId}`);
}

export async function listExerciseRecords(params: ExerciseRecordQuery): Promise<PagedResult<ExerciseRecordVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/records", { params });
  return normalizePagedResult<ExerciseRecordVO>(unwrapResponse(response));
}

export async function generateAiQuestions(payload: AiQuestionGenerateRequest): Promise<AiQuestionGenerateResultVO> {
  const response = await apiClient.post<ApiEnvelope<AiQuestionGenerateResultVO>>(
    "/student/ai-questions/generate",
    payload
  );
  return unwrapResponse(response);
}

export async function submitAiQuestions(payload: {
  sessionId: string;
  answers: Array<{ questionId: string; userAnswer: string }>;
}): Promise<AiQuestionResultVO> {
  const response = await apiClient.post<ApiEnvelope<AiQuestionResultVO>>("/student/ai-questions/submit", payload);
  return unwrapResponse(response);
}

export async function getAiQuestionAnalysis(recordId: string): Promise<AiQuestionAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<AiQuestionAnalysisVO>>(`/student/ai-questions/${recordId}/analysis`);
  return unwrapResponse(response);
}

export async function listAiQuestionHistory(params: AiHistoryQuery): Promise<PagedResult<AiQuestionSessionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/ai-questions", { params });
  return normalizePagedResult<AiQuestionSessionVO>(unwrapResponse(response));
}
