import { defineStore } from "pinia";
import { toErrorMessage } from "../services/error-message";
import {
  getExerciseAnalysis,
  listExerciseQuestions,
  listExerciseRecords,
  listWrongQuestions,
  removeWrongQuestion,
  submitExercise
} from "../services/student.service";
import type {
  ExerciseAnalysisVO,
  ExerciseRecordVO,
  ExerciseResultVO,
  ExerciseSubmitRequest,
  PagedResult,
  QuestionVO,
  WrongBookEntryVO
} from "../services/contracts";

interface ExerciseState {
  questions: QuestionVO[];
  questionPage: number;
  questionSize: number;
  questionTotalPages: number;
  questionTotalElements: number;
  questionsLoaded: boolean;
  questionsLoading: boolean;
  questionsError: string;

  submitLoading: boolean;
  submitError: string;
  latestResult: ExerciseResultVO | null;

  analysisLoading: boolean;
  analysisError: string;
  analysisByRecord: Record<string, ExerciseAnalysisVO>;

  wrongEntries: WrongBookEntryVO[];
  wrongPage: number;
  wrongSize: number;
  wrongTotalPages: number;
  wrongTotalElements: number;
  wrongLoaded: boolean;
  wrongLoading: boolean;
  wrongError: string;

  records: ExerciseRecordVO[];
  recordsPage: number;
  recordsSize: number;
  recordsTotalPages: number;
  recordsTotalElements: number;
  recordsLoaded: boolean;
  recordsLoading: boolean;
  recordsError: string;
}

function assignPagedMeta<T>(
  state: ExerciseState,
  prefix: "question" | "wrong" | "records",
  paged: PagedResult<T>
): void {
  if (prefix === "question") {
    state.questionPage = paged.page;
    state.questionSize = paged.size;
    state.questionTotalPages = paged.totalPages;
    state.questionTotalElements = paged.totalElements;
    return;
  }

  if (prefix === "wrong") {
    state.wrongPage = paged.page;
    state.wrongSize = paged.size;
    state.wrongTotalPages = paged.totalPages;
    state.wrongTotalElements = paged.totalElements;
    return;
  }

  state.recordsPage = paged.page;
  state.recordsSize = paged.size;
  state.recordsTotalPages = paged.totalPages;
  state.recordsTotalElements = paged.totalElements;
}

export const useExerciseStore = defineStore("exercise", {
  state: (): ExerciseState => ({
    questions: [],
    questionPage: 1,
    questionSize: 10,
    questionTotalPages: 1,
    questionTotalElements: 0,
    questionsLoaded: false,
    questionsLoading: false,
    questionsError: "",

    submitLoading: false,
    submitError: "",
    latestResult: null,

    analysisLoading: false,
    analysisError: "",
    analysisByRecord: {},

    wrongEntries: [],
    wrongPage: 1,
    wrongSize: 20,
    wrongTotalPages: 1,
    wrongTotalElements: 0,
    wrongLoaded: false,
    wrongLoading: false,
    wrongError: "",

    records: [],
    recordsPage: 1,
    recordsSize: 20,
    recordsTotalPages: 1,
    recordsTotalElements: 0,
    recordsLoaded: false,
    recordsLoading: false,
    recordsError: ""
  }),
  actions: {
    async loadQuestions(query: {
      subject?: string;
      difficulty?: "EASY" | "MEDIUM" | "HARD";
      page?: number;
      size?: number;
    }): Promise<void> {
      this.questionsLoading = true;
      this.questionsError = "";
      try {
        const paged = await listExerciseQuestions(query);
        this.questions = paged.content;
        assignPagedMeta(this, "question", paged);
        this.questionsLoaded = true;
      } catch (error) {
        this.questionsError = toErrorMessage(error, "加载题目失败");
      } finally {
        this.questionsLoading = false;
      }
    },

    async submitAnswers(answers: ExerciseSubmitRequest["answers"]): Promise<ExerciseResultVO | null> {
      this.submitLoading = true;
      this.submitError = "";
      try {
        const result = await submitExercise(answers);
        this.latestResult = result;
        if (result.recordId) {
          await this.loadAnalysis(result.recordId);
        }
        return result;
      } catch (error) {
        this.submitError = toErrorMessage(error, "提交练习失败");
        return null;
      } finally {
        this.submitLoading = false;
      }
    },

    async loadAnalysis(recordId: string): Promise<ExerciseAnalysisVO | null> {
      this.analysisLoading = true;
      this.analysisError = "";
      try {
        const analysis = await getExerciseAnalysis(recordId);
        this.analysisByRecord = {
          ...this.analysisByRecord,
          [recordId]: analysis
        };
        return analysis;
      } catch (error) {
        this.analysisError = toErrorMessage(error, "加载解析失败");
        return null;
      } finally {
        this.analysisLoading = false;
      }
    },

    async loadWrongEntries(query: {
      subject?: string;
      status?: "ACTIVE" | "MASTERED";
      page?: number;
      size?: number;
    }): Promise<void> {
      this.wrongLoading = true;
      this.wrongError = "";
      try {
        const paged = await listWrongQuestions(query);
        this.wrongEntries = paged.content;
        assignPagedMeta(this, "wrong", paged);
        this.wrongLoaded = true;
      } catch (error) {
        this.wrongError = toErrorMessage(error, "加载错题本失败");
      } finally {
        this.wrongLoading = false;
      }
    },

    async markWrongMastered(questionId: string, query: {
      subject?: string;
      status?: "ACTIVE" | "MASTERED";
      page?: number;
      size?: number;
    }): Promise<void> {
      await removeWrongQuestion(questionId);
      await this.loadWrongEntries(query);
    },

    async loadRecords(query: {
      startDate?: string;
      endDate?: string;
      page?: number;
      size?: number;
    }): Promise<void> {
      this.recordsLoading = true;
      this.recordsError = "";
      try {
        const paged = await listExerciseRecords(query);
        this.records = paged.content;
        assignPagedMeta(this, "records", paged);
        this.recordsLoaded = true;
      } catch (error) {
        this.recordsError = toErrorMessage(error, "加载做题记录失败");
      } finally {
        this.recordsLoading = false;
      }
    }
  }
});
