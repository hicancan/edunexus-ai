import { defineStore } from "pinia";
import { toErrorMessage } from "../services/error-message";
import {
  generateAiQuestions,
  getAiQuestionAnalysis,
  listAiQuestionHistory,
  submitAiQuestions
} from "../services/student.service";
import type {
  AiQuestionAnalysisVO,
  AiQuestionGenerateRequest,
  AiQuestionResultVO,
  AiQuestionSessionVO,
  PagedResult,
  QuestionVO
} from "../services/contracts";

interface AiqState {
  generateLoading: boolean;
  submitLoading: boolean;
  historyLoading: boolean;
  analysisLoading: boolean;
  error: string;
  sessionId: string;
  generatedQuestions: QuestionVO[];
  submitResult: AiQuestionResultVO | null;
  analysisByRecord: Record<string, AiQuestionAnalysisVO>;
  history: AiQuestionSessionVO[];
  historyPage: number;
  historySize: number;
  historyTotalPages: number;
  historyTotalElements: number;
  historyLoaded: boolean;
}

function assignHistoryMeta(state: AiqState, paged: PagedResult<AiQuestionSessionVO>): void {
  state.historyPage = paged.page;
  state.historySize = paged.size;
  state.historyTotalPages = paged.totalPages;
  state.historyTotalElements = paged.totalElements;
}

export const useAiqStore = defineStore("aiq", {
  state: (): AiqState => ({
    generateLoading: false,
    submitLoading: false,
    historyLoading: false,
    analysisLoading: false,
    error: "",
    sessionId: "",
    generatedQuestions: [],
    submitResult: null,
    analysisByRecord: {},
    history: [],
    historyPage: 1,
    historySize: 10,
    historyTotalPages: 1,
    historyTotalElements: 0,
    historyLoaded: false
  }),
  actions: {
    async generate(payload: AiQuestionGenerateRequest): Promise<void> {
      this.generateLoading = true;
      this.error = "";
      try {
        const generated = await generateAiQuestions(payload);
        this.sessionId = generated.sessionId || "";
        this.generatedQuestions = generated.questions || [];
        this.submitResult = null;
      } catch (error) {
        this.error = toErrorMessage(error, "生成 AI 题目失败");
      } finally {
        this.generateLoading = false;
      }
    },

    async submitAnswers(answers: Array<{ questionId: string; userAnswer: string }>): Promise<AiQuestionResultVO | null> {
      if (!this.sessionId) {
        this.error = "请先生成题目后再提交答案";
        return null;
      }

      this.submitLoading = true;
      this.error = "";
      try {
        const result = await submitAiQuestions({
          sessionId: this.sessionId,
          answers
        });
        this.submitResult = result;
        if (result.recordId) {
          await this.loadAnalysis(result.recordId);
        }
        return result;
      } catch (error) {
        this.error = toErrorMessage(error, "提交 AI 答案失败");
        return null;
      } finally {
        this.submitLoading = false;
      }
    },

    async loadAnalysis(recordId: string): Promise<AiQuestionAnalysisVO | null> {
      this.analysisLoading = true;
      this.error = "";
      try {
        const analysis = await getAiQuestionAnalysis(recordId);
        this.analysisByRecord = {
          ...this.analysisByRecord,
          [recordId]: analysis
        };
        return analysis;
      } catch (error) {
        this.error = toErrorMessage(error, "加载 AI 题目解析失败");
        return null;
      } finally {
        this.analysisLoading = false;
      }
    },

    async loadHistory(params: { subject?: string; page?: number; size?: number } = {}): Promise<void> {
      this.historyLoading = true;
      this.error = "";
      try {
        const paged = await listAiQuestionHistory(params);
        this.history = paged.content;
        assignHistoryMeta(this, paged);
        this.historyLoaded = true;
      } catch (error) {
        this.error = toErrorMessage(error, "加载 AI 历史记录失败");
      } finally {
        this.historyLoading = false;
      }
    }
  }
});
