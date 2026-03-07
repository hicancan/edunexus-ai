import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import {
  deletePlan,
  exportPlan,
  generatePlan,
  listPlans,
  sharePlan,
  updatePlan
} from "../api/teacher.service";
import type {
  LessonPlanVO,
  PagedResult,
  PlanGenerateRequest,
  ShareResultVO
} from "../../../services/contracts";

interface PlanState {
  plans: LessonPlanVO[];
  plansPage: number;
  plansSize: number;
  plansTotalPages: number;
  plansTotalElements: number;
  plansLoading: boolean;
  plansLoaded: boolean;
  plansError: string;

  operationLoading: boolean;
  operationError: string;
  shareResult: ShareResultVO | null;
}

function assignPlanMeta(state: PlanState, paged: PagedResult<LessonPlanVO>): void {
  state.plansPage = paged.page;
  state.plansSize = paged.size;
  state.plansTotalPages = paged.totalPages;
  state.plansTotalElements = paged.totalElements;
}

export const usePlanStore = defineStore("teacher-plans", {
  state: (): PlanState => ({
    plans: [],
    plansPage: 1,
    plansSize: 10,
    plansTotalPages: 1,
    plansTotalElements: 0,
    plansLoading: false,
    plansLoaded: false,
    plansError: "",

    operationLoading: false,
    operationError: "",
    shareResult: null
  }),
  actions: {
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
        return await generatePlan(payload);
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
    }
  }
});
