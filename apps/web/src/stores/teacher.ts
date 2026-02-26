import { defineStore } from "pinia";
import { toErrorMessage } from "../services/error-message";
import {
  createTeacherSuggestion,
  deleteKnowledgeDocument,
  deletePlan,
  exportPlan,
  generatePlan,
  getStudentAnalytics,
  listKnowledgeDocuments,
  listPlans,
  sharePlan,
  updatePlan,
  uploadKnowledgeDocument
} from "../services/teacher.service";
import type {
  DocumentStatus,
  DocumentVO,
  LessonPlanVO,
  PlanGenerateRequest,
  PagedResult,
  ShareResultVO,
  StudentAnalyticsVO,
  TeacherSuggestionRequest,
  TeacherSuggestionVO
} from "../services/contracts";

interface TeacherState {
  documents: DocumentVO[];
  documentsLoading: boolean;
  documentsLoaded: boolean;
  documentsError: string;

  plans: LessonPlanVO[];
  plansPage: number;
  plansSize: number;
  plansTotalPages: number;
  plansTotalElements: number;
  plansLoading: boolean;
  plansLoaded: boolean;
  plansError: string;

  analytics: StudentAnalyticsVO | null;
  analyticsLoading: boolean;
  analyticsError: string;

  suggestionLoading: boolean;
  suggestionError: string;
  latestSuggestion: TeacherSuggestionVO | null;

  operationLoading: boolean;
  operationError: string;
  shareResult: ShareResultVO | null;
}

function assignPlanMeta(state: TeacherState, paged: PagedResult<LessonPlanVO>): void {
  state.plansPage = paged.page;
  state.plansSize = paged.size;
  state.plansTotalPages = paged.totalPages;
  state.plansTotalElements = paged.totalElements;
}

export const useTeacherStore = defineStore("teacher", {
  state: (): TeacherState => ({
    documents: [],
    documentsLoading: false,
    documentsLoaded: false,
    documentsError: "",

    plans: [],
    plansPage: 1,
    plansSize: 10,
    plansTotalPages: 1,
    plansTotalElements: 0,
    plansLoading: false,
    plansLoaded: false,
    plansError: "",

    analytics: null,
    analyticsLoading: false,
    analyticsError: "",

    suggestionLoading: false,
    suggestionError: "",
    latestSuggestion: null,

    operationLoading: false,
    operationError: "",
    shareResult: null
  }),
  actions: {
    async loadDocuments(status?: DocumentStatus): Promise<void> {
      this.documentsLoading = true;
      this.documentsError = "";
      try {
        this.documents = await listKnowledgeDocuments(status);
        this.documentsLoaded = true;
      } catch (error) {
        this.documentsError = toErrorMessage(error, "加载知识文档失败");
      } finally {
        this.documentsLoading = false;
      }
    },

    async uploadDocument(file: File): Promise<void> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        await uploadKnowledgeDocument(file);
      } catch (error) {
        this.operationError = toErrorMessage(error, "上传文档失败");
      } finally {
        this.operationLoading = false;
      }
    },

    async removeDocument(documentId: string): Promise<void> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        await deleteKnowledgeDocument(documentId);
      } catch (error) {
        this.operationError = toErrorMessage(error, "删除文档失败");
      } finally {
        this.operationLoading = false;
      }
    },

    async loadPlans(params: { page?: number; size?: number } = {}): Promise<void> {
      this.plansLoading = true;
      this.plansError = "";
      try {
        const paged = await listPlans(params);
        this.plans = paged.content;
        assignPlanMeta(this, paged);
        this.plansLoaded = true;
      } catch (error) {
        this.plansError = toErrorMessage(error, "加载教案失败");
      } finally {
        this.plansLoading = false;
      }
    },

    async createPlan(payload: PlanGenerateRequest): Promise<LessonPlanVO | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        const plan = await generatePlan(payload);
        return plan;
      } catch (error) {
        this.operationError = toErrorMessage(error, "生成教案失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async savePlan(planId: string, contentMd: string): Promise<LessonPlanVO | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        return await updatePlan(planId, { contentMd });
      } catch (error) {
        this.operationError = toErrorMessage(error, "保存教案失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async removePlan(planId: string): Promise<void> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        await deletePlan(planId);
      } catch (error) {
        this.operationError = toErrorMessage(error, "删除教案失败");
      } finally {
        this.operationLoading = false;
      }
    },

    async shareLessonPlan(planId: string): Promise<ShareResultVO | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        this.shareResult = await sharePlan(planId);
        return this.shareResult;
      } catch (error) {
        this.operationError = toErrorMessage(error, "生成分享链接失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async exportLessonPlan(planId: string, format: "md" | "pdf"): Promise<Blob | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        return await exportPlan(planId, format);
      } catch (error) {
        this.operationError = toErrorMessage(error, "导出教案失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async loadAnalytics(studentId: string): Promise<void> {
      this.analyticsLoading = true;
      this.analyticsError = "";
      try {
        this.analytics = await getStudentAnalytics(studentId);
      } catch (error) {
        this.analyticsError = toErrorMessage(error, "加载学情分析失败");
      } finally {
        this.analyticsLoading = false;
      }
    },

    async submitSuggestion(payload: TeacherSuggestionRequest): Promise<TeacherSuggestionVO | null> {
      this.suggestionLoading = true;
      this.suggestionError = "";
      try {
        const suggestion = await createTeacherSuggestion(payload);
        this.latestSuggestion = suggestion;
        return suggestion;
      } catch (error) {
        this.suggestionError = toErrorMessage(error, "提交教师建议失败");
        return null;
      } finally {
        this.suggestionLoading = false;
      }
    }
  }
});
