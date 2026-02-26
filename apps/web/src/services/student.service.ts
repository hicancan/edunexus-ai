import { apiClient, unwrapResponse } from "./api-client";
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
  type ExerciseAnalysisVO,
  type ExerciseRecordVO,
  type ExerciseResultVO,
  type PagedResult,
  type QuestionVO,
  type UserVO,
  type WrongBookEntryVO,
  normalizePagedResult
} from "./contracts";

type Difficulty = "EASY" | "MEDIUM" | "HARD";
type WrongStatus = "ACTIVE" | "MASTERED";

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

function normalizeQuestion(raw: unknown): QuestionVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id || row.questionId),
    subject: asString(row.subject),
    questionType: asString(row.questionType || row.type, "SINGLE_CHOICE") as QuestionVO["questionType"],
    difficulty: asString(row.difficulty, "MEDIUM") as QuestionVO["difficulty"],
    content: asString(row.content),
    options: asRecord(row.options) as QuestionVO["options"],
    correctAnswer: asString(row.correctAnswer),
    analysis: asString(row.analysis) || null,
    knowledgePoints: asArray<string>(row.knowledgePoints),
    score: asNumber(row.score, 0),
    source: asString(row.source, "MANUAL") as QuestionVO["source"],
    createdAt: asString(row.createdAt)
  };
}

function normalizeChatSession(raw: unknown): ChatSessionVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id || row.sessionId),
    studentId: asString(row.studentId),
    title: asString(row.title, "新建对话"),
    createdAt: asString(row.createdAt),
    updatedAt: asString(row.updatedAt)
  };
}

function normalizeChatMessage(raw: unknown): NonNullable<ChatSessionDetailVO["messages"]>[number] {
  const row = asRecord(raw);
  const citationsRaw = asArray<Record<string, unknown>>(row.citations);
  return {
    id: asString(row.id || row.messageId),
    role: asString(row.role, "ASSISTANT") as "USER" | "ASSISTANT",
    content: asString(row.content),
    citations: citationsRaw.map((citation) => ({
      documentId: asString(citation.documentId),
      filename: asString(citation.filename || citation.title),
      chunkIndex: asNumber(citation.chunkIndex, 0),
      content: asString(citation.content),
      score: asNumber(citation.score, 0)
    })),
    tokenUsage: asNumber(row.tokenUsage, 0),
    createdAt: asString(row.createdAt || row.timestamp)
  };
}

function normalizeExerciseResult(raw: unknown): ExerciseResultVO {
  const row = asRecord(raw);
  const itemRows = asArray<unknown>(row.items ?? row.results);
  return {
    recordId: asString(row.recordId),
    totalQuestions: asNumber(row.totalQuestions ?? row.totalCount, 0),
    correctCount: asNumber(row.correctCount, 0),
    totalScore: asNumber(row.totalScore, 0),
    items: itemRows.map((item) => {
      const record = asRecord(item);
      return {
        questionId: asString(record.questionId),
        userAnswer: asString(record.userAnswer),
        correctAnswer: asString(record.correctAnswer),
        isCorrect: Boolean(record.isCorrect),
        score: asNumber(record.score, 0)
      };
    })
  };
}

function normalizeExerciseAnalysis(raw: unknown): ExerciseAnalysisVO {
  const row = asRecord(raw);
  const itemRows = asArray<unknown>(row.items ?? row.questions);
  return {
    recordId: asString(row.recordId),
    items: itemRows.map((item) => {
      const record = asRecord(item);
      return {
        questionId: asString(record.questionId),
        content: asString(record.content),
        userAnswer: asString(record.userAnswer),
        correctAnswer: asString(record.correctAnswer),
        isCorrect: Boolean(record.isCorrect),
        analysis: asString(record.analysis) || null,
        knowledgePoints: asArray<string>(record.knowledgePoints),
        teacherSuggestion: asString(record.teacherSuggestion) || null
      };
    })
  };
}

function normalizeWrongBookEntry(raw: unknown): WrongBookEntryVO {
  const row = asRecord(raw);
  const question = asRecord(row.question);
  const resolvedQuestion = question.id || row.content
    ? normalizeQuestion({
      ...question,
      id: question.id || row.questionId,
      content: question.content || row.content,
      subject: question.subject || row.subject,
      knowledgePoints: question.knowledgePoints || row.knowledgePoints
    })
    : undefined;

  return {
    id: asString(row.id),
    questionId: asString(row.questionId),
    question: resolvedQuestion,
    wrongCount: asNumber(row.wrongCount, 0),
    lastWrongTime: asString(row.lastWrongTime),
    status: asString(row.status, "ACTIVE") as WrongBookEntryVO["status"]
  };
}

function normalizeExerciseRecord(raw: unknown): ExerciseRecordVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id || row.recordId),
    subject: asString(row.subject),
    totalQuestions: asNumber(row.totalQuestions, 0),
    correctCount: asNumber(row.correctCount, 0),
    totalScore: asNumber(row.totalScore, 0),
    timeSpent: asNumber(row.timeSpent, 0),
    createdAt: asString(row.createdAt || row.submitTime)
  };
}

function normalizeAiQuestionSession(raw: unknown): AiQuestionSessionVO {
  const row = asRecord(raw);
  return {
    id: asString(row.id || row.sessionId),
    subject: asString(row.subject),
    difficulty: asString(row.difficulty, "MEDIUM") as AiQuestionSessionVO["difficulty"],
    questionCount: asNumber(row.questionCount, 0),
    completed: Boolean(row.completed),
    correctRate: row.correctRate == null ? null : asNumber(row.correctRate, 0),
    score: row.score == null ? null : asNumber(row.score, 0),
    generatedAt: asString(row.generatedAt)
  };
}

export interface ChatListQuery {
  page?: number;
  size?: number;
}

export interface ExerciseQuestionQuery {
  subject?: string;
  difficulty?: Difficulty | "";
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
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeChatSession)
  };
}

export async function createChatSession(): Promise<ChatSessionVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>("/student/chat/session");
  return normalizeChatSession(unwrapResponse(response));
}

export async function getChatSessionDetail(sessionId: string): Promise<ChatSessionDetailVO> {
  const response = await apiClient.get<ApiEnvelope<unknown>>(`/student/chat/session/${sessionId}`);
  const data = asRecord(unwrapResponse(response));
  return {
    id: asString(data.id || data.sessionId, sessionId),
    title: asString(data.title, "新建对话"),
    createdAt: asString(data.createdAt),
    messages: asArray<unknown>(data.messages).map(normalizeChatMessage)
  };
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/chat/session/${sessionId}`);
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatReplyVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>(`/student/chat/session/${sessionId}/message`, {
    message
  });
  const data = asRecord(unwrapResponse(response));

  if (data.assistantMessage) {
    return {
      userMessage: normalizeChatMessage(data.userMessage),
      assistantMessage: normalizeChatMessage(data.assistantMessage)
    };
  }

  return {
    userMessage: {
      role: "USER",
      content: asString(data.userMessage || message)
    },
    assistantMessage: {
      role: "ASSISTANT",
      content: asString(data.aiResponse),
      citations: asArray<unknown>(data.sources).map((item) => {
        const citation = asRecord(item);
        return {
          documentId: asString(citation.documentId),
          filename: asString(citation.filename || citation.title),
          chunkIndex: asNumber(citation.chunkIndex, 0),
          content: asString(citation.content),
          score: asNumber(citation.score, 0)
        };
      }),
      createdAt: asString(data.timestamp)
    }
  };
}

export async function listExerciseQuestions(params: ExerciseQuestionQuery): Promise<PagedResult<QuestionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/questions", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeQuestion)
  };
}

export async function submitExercise(
  answers: Array<{ questionId: string; userAnswer: string }>
): Promise<ExerciseResultVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>("/student/exercise/submit", {
    answers
  });
  return normalizeExerciseResult(unwrapResponse(response));
}

export async function getExerciseAnalysis(recordId: string): Promise<ExerciseAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<unknown>>(`/student/exercise/${recordId}/analysis`);
  return normalizeExerciseAnalysis(unwrapResponse(response));
}

export async function listWrongQuestions(params: WrongBookQuery): Promise<PagedResult<WrongBookEntryVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/wrong-questions", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeWrongBookEntry)
  };
}

export async function removeWrongQuestion(questionId: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/student/exercise/wrong-questions/${questionId}`);
}

export async function listExerciseRecords(params: ExerciseRecordQuery): Promise<PagedResult<ExerciseRecordVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/exercise/records", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeExerciseRecord)
  };
}

export async function generateAiQuestions(payload: AiQuestionGenerateRequest): Promise<AiQuestionGenerateResultVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>(
    "/student/ai-questions/generate",
    payload
  );
  const data = asRecord(unwrapResponse(response));
  return {
    sessionId: asString(data.sessionId),
    questions: asArray<unknown>(data.questions).map(normalizeQuestion)
  };
}

export async function submitAiQuestions(
  sessionId: string,
  answers: Array<{ questionId: string; userAnswer: string }>
): Promise<AiQuestionResultVO> {
  const response = await apiClient.post<ApiEnvelope<unknown>>("/student/ai-questions/submit", {
    sessionId,
    answers
  });
  const data = asRecord(unwrapResponse(response));
  const result = normalizeExerciseResult(data);
  return {
    ...result,
    sessionId: asString(data.sessionId)
  };
}

export async function getAiQuestionAnalysis(recordId: string): Promise<AiQuestionAnalysisVO> {
  const response = await apiClient.get<ApiEnvelope<unknown>>(`/student/ai-questions/${recordId}/analysis`);
  const normalized = normalizeExerciseAnalysis(unwrapResponse(response));
  return {
    recordId: normalized.recordId,
    items: normalized.items
  };
}

export async function listAiQuestionHistory(params: AiHistoryQuery): Promise<PagedResult<AiQuestionSessionVO>> {
  const response = await apiClient.get<ApiEnvelope<unknown>>("/student/ai-questions", { params });
  const paged = normalizePagedResult<unknown>(unwrapResponse(response));
  return {
    ...paged,
    content: paged.content.map(normalizeAiQuestionSession)
  };
}

export async function getCurrentUserProfile(): Promise<UserVO> {
  const response = await apiClient.get<ApiEnvelope<UserVO>>("/auth/me");
  return unwrapResponse(response);
}
