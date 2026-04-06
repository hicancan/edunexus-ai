import {
  API_BASE_URL,
  ApiClientError,
  apiClient,
  getAccessTokenForRequest,
  unwrapResponse
} from "../../../services/api-client";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import {
  type AiQuestionAnalysisVO,
  type AiQuestionGenerateRequest,
  type AiQuestionGenerateResultVO,
  type AiQuestionResultVO,
  type AiQuestionSessionVO,
  type ApiEnvelope,
  type CitationVO,
  type ChatReplyVO,
  type ChatSessionDetailVO,
  type ChatSessionVO,
  type Difficulty,
  type ExerciseAnalysisVO,
  type ExerciseRecordVO,
  type ExerciseResultVO,
  type ExerciseSubmitRequest,
  type KnowledgeTopologyVO,
  type PagedResult,
  type QuestionVO,
  type SocraticProbeRequest,
  type SocraticProbeVO,
  type StudentAnalyticsVO,
  type WeakPointVO,
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

export interface ChatStreamEvent {
  type: "delta" | "done" | "error";
  delta?: string;
  citations?: CitationVO[];
  message?: string;
}

function resolveTimeoutMs(envValue: unknown, fallback: number): number {
  const parsed = Number(envValue);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

const AI_QUESTION_GENERATE_TIMEOUT_MS = resolveTimeoutMs(
  import.meta.env.VITE_AI_QUESTION_TIMEOUT_MS,
  180000
);

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function parseSsePayload(raw: string): { done: boolean; event?: ChatStreamEvent } {
  const payload = raw.trim();
  if (!payload) {
    return { done: false };
  }
  if (payload === "[DONE]") {
    return { done: true, event: { type: "done" } };
  }

  try {
    const parsed = JSON.parse(payload) as {
      delta?: string;
      citations?: CitationVO[];
      error?: string;
      done?: boolean;
    };

    if (parsed.error) {
      return { done: true, event: { type: "error", message: parsed.error } };
    }
    if (parsed.done) {
      return { done: true, event: { type: "done" } };
    }
    if (typeof parsed.delta === "string") {
      return {
        done: false,
        event: {
          type: "delta",
          delta: parsed.delta,
          citations: Array.isArray(parsed.citations) ? parsed.citations : []
        }
      };
    }
  } catch {
    return { done: false };
  }
  return { done: false };
}

export async function listChatSessions(
  params: ChatListQuery = {}
): Promise<PagedResult<ChatSessionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/chat/sessions", { params });
  return normalizePagedResult<ChatSessionVO>(unwrapResponse(response));
}

export async function createChatSession(): Promise<ChatSessionVO> {
  const response = await apiClient.post<ApiEnvelope<ChatSessionVO>>("/student/chat/session");
  return unwrapResponse(response);
}

export async function getChatSessionDetail(sessionId: string): Promise<ChatSessionDetailVO> {
  const response = await apiClient.get<ApiEnvelope<ChatSessionDetailVO>>(
    `/student/chat/session/${sessionId}`
  );
  return unwrapResponse(response);
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/chat/session/${sessionId}`);
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatReplyVO> {
  const response = await apiClient.post<ApiEnvelope<ChatReplyVO>>(
    `/student/chat/session/${sessionId}/message`,
    {
      message
    }
  );
  return unwrapResponse(response);
}

export async function sendChatMessageStream(
  sessionId: string,
  message: string,
  onEvent: (event: ChatStreamEvent) => void
): Promise<void> {
  const token = getAccessTokenForRequest();
  const requestId = createRequestId();
  const idempotencyKey = createRequestId();

  class FetchEventSourceError extends Error {
    constructor(
      public messageText: string,
      public status?: number,
      public code?: string,
      public traceId?: string
    ) {
      super(messageText);
    }
  }

  try {
    await fetchEventSource(`${API_BASE_URL}/student/chat/session/${sessionId}/message`, {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
        "X-Request-Id": requestId,
        "Idempotency-Key": idempotencyKey,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ message }),
      async onopen(response) {
        if (response.ok && response.headers.get("content-type")?.includes("text/event-stream")) {
          return;
        }
        let messageText = "发送消息失败";
        try {
          const body = (await response.json()) as { message?: string; errorCode?: string; traceId?: string };
          if (body?.message) messageText = body.message;
          throw new FetchEventSourceError(messageText, response.status, body?.errorCode, body?.traceId);
        } catch (e) {
          if (e instanceof FetchEventSourceError) throw e;
          throw new FetchEventSourceError(messageText, response.status);
        }
      },
      onmessage(msg) {
        if (!msg.data) return;
        const parsed = parseSsePayload(msg.data);
        if (parsed.event) {
          onEvent(parsed.event);
        }
      },
      onerror(err) {
        if (err instanceof FetchEventSourceError) {
          throw err;
        }
        throw err;
      }
    });

    onEvent({ type: "done" });
  } catch (error) {
    if (error instanceof FetchEventSourceError) {
      throw new ApiClientError(error.messageText, {
        status: error.status,
        code: error.code,
        traceId: error.traceId
      });
    }
    throw new ApiClientError(error instanceof Error ? error.message : "流连接中断");
  }
}

export async function listExerciseQuestions(
  params: ExerciseQuestionQuery
): Promise<PagedResult<QuestionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/questions", {
    params
  });
  return normalizePagedResult<QuestionVO>(unwrapResponse(response));
}

export async function submitExercise(
  answers: ExerciseSubmitRequest["answers"]
): Promise<ExerciseResultVO> {
  const response = await apiClient.post<ApiEnvelope<ExerciseResultVO>>("/student/exercise/submit", {
    answers
  });
  return unwrapResponse(response);
}

export async function getExerciseAnalysis(recordId: string): Promise<ExerciseAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<ExerciseAnalysisVO>>(
    `/student/exercise/${recordId}/analysis`
  );
  return unwrapResponse(response);
}

export async function listWrongQuestions(
  params: WrongBookQuery
): Promise<PagedResult<WrongBookEntryVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/wrong-questions", {
    params
  });
  return normalizePagedResult<WrongBookEntryVO>(unwrapResponse(response));
}

export async function removeWrongQuestion(questionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/exercise/wrong-questions/${questionId}`);
}

export async function listExerciseRecords(
  params: ExerciseRecordQuery
): Promise<PagedResult<ExerciseRecordVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/records", {
    params
  });
  return normalizePagedResult<ExerciseRecordVO>(unwrapResponse(response));
}

export async function generateAiQuestions(
  payload: AiQuestionGenerateRequest
): Promise<AiQuestionGenerateResultVO> {
  const response = await apiClient.post<ApiEnvelope<AiQuestionGenerateResultVO>>(
    "/student/ai-questions/generate",
    payload,
    {
      timeout: AI_QUESTION_GENERATE_TIMEOUT_MS
    }
  );
  return unwrapResponse(response);
}

export async function submitAiQuestions(payload: {
  sessionId: string;
  answers: Array<{ questionId: string; userAnswer: string }>;
}): Promise<AiQuestionResultVO> {
  const response = await apiClient.post<ApiEnvelope<AiQuestionResultVO>>(
    "/student/ai-questions/submit",
    payload
  );
  return unwrapResponse(response);
}

export async function getAiQuestionAnalysis(recordId: string): Promise<AiQuestionAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<AiQuestionAnalysisVO>>(
    `/student/ai-questions/${recordId}/analysis`
  );
  return unwrapResponse(response);
}

export async function listAiQuestionHistory(
  params: AiHistoryQuery
): Promise<PagedResult<AiQuestionSessionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/ai-questions", { params });
  return normalizePagedResult<AiQuestionSessionVO>(unwrapResponse(response));
}

export async function listProfileWeakPoints(): Promise<WeakPointVO[]> {
  const response = await apiClient.get<ApiEnvelope<WeakPointVO[]>>("/student/profile/weak-points");
  return unwrapResponse(response) || [];
}

export async function getProfileAnalytics(): Promise<StudentAnalyticsVO> {
  const response = await apiClient.get<ApiEnvelope<StudentAnalyticsVO>>(
    "/student/profile/analytics"
  );
  return unwrapResponse(response);
}

export async function getKnowledgeTopology(): Promise<KnowledgeTopologyVO> {
  const response = await apiClient.get<ApiEnvelope<KnowledgeTopologyVO>>("/student/profile/knowledge-topology");
  return unwrapResponse(response);
}

export async function socraticProbe(
  questionId: string,
  payload: SocraticProbeRequest
): Promise<SocraticProbeVO> {
  const response = await apiClient.post<ApiEnvelope<SocraticProbeVO>>(
    `/student/exercise/wrong-questions/${questionId}/socratic-probe`,
    payload
  );
  return unwrapResponse(response);
}
