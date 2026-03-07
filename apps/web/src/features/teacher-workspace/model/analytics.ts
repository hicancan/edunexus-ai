import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import {
  getStudentAnalytics,
  getStudentAttribution,
  listInterventionRecommendations
} from "../api/teacher.service";
import type {
  InterventionRecommendationVO,
  StudentAnalyticsVO,
  StudentAttributionVO
} from "../../../services/contracts";

interface AnalyticsState {
  analytics: StudentAnalyticsVO | null;
  analyticsLoading: boolean;
  analyticsError: string;

  attributionByStudent: Record<string, StudentAttributionVO>;
  attributionLoading: boolean;
  attributionError: string;

  interventions: InterventionRecommendationVO[];
  interventionsLoading: boolean;
  interventionsError: string;

  lastStudentId: string;
}

export const useAnalyticsStore = defineStore("teacher-analytics", {
  state: (): AnalyticsState => ({
    analytics: null,
    analyticsLoading: false,
    analyticsError: "",

    attributionByStudent: {},
    attributionLoading: false,
    attributionError: "",

    interventions: [],
    interventionsLoading: false,
    interventionsError: "",

    lastStudentId: ""
  }),
  actions: {
    async loadAnalytics(studentId: string): Promise<void> {
      this.analyticsLoading = true;
      this.analyticsError = "";
      this.lastStudentId = studentId;
      try {
        this.analytics = await getStudentAnalytics(studentId);
      } catch (error) {
        this.analyticsError = toErrorMessage(error, "加载学情分析失败");
      } finally {
        this.analyticsLoading = false;
      }
    },

    async loadAttribution(studentId: string): Promise<StudentAttributionVO | null> {
      this.attributionLoading = true;
      this.attributionError = "";
      try {
        const attribution = await getStudentAttribution(studentId);
        this.attributionByStudent = {
          ...this.attributionByStudent,
          [studentId]: attribution
        };
        return attribution;
      } catch (error) {
        this.attributionError = toErrorMessage(error, "加载错因归因失败");
        return null;
      } finally {
        this.attributionLoading = false;
      }
    },

    async loadInterventions(): Promise<void> {
      this.interventionsLoading = true;
      this.interventionsError = "";
      try {
        this.interventions = await listInterventionRecommendations();
      } catch (error) {
        this.interventionsError = toErrorMessage(error, "加载干预建议失败");
      } finally {
        this.interventionsLoading = false;
      }
    }
  },
  persist: {
    key: "teacher-analytics",
    paths: ["lastStudentId"]
  }
});
