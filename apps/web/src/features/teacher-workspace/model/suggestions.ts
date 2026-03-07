import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import { createTeacherSuggestion, dispatchBulkSuggestion } from "../api/teacher.service";
import type {
  BulkSuggestionRequest,
  BulkSuggestionResultVO,
  TeacherSuggestionRequest,
  TeacherSuggestionVO
} from "../../../services/contracts";

interface SuggestionState {
  suggestionLoading: boolean;
  suggestionError: string;
  latestSuggestion: TeacherSuggestionVO | null;
  latestBulkSuggestion: BulkSuggestionResultVO | null;
  dispatchedPoints: string[];
}

export const useSuggestionStore = defineStore("teacher-suggestions", {
  state: (): SuggestionState => ({
    suggestionLoading: false,
    suggestionError: "",
    latestSuggestion: null,
    latestBulkSuggestion: null,
    dispatchedPoints: []
  }),
  actions: {
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
    },

    async dispatchSuggestion(
      payload: BulkSuggestionRequest
    ): Promise<BulkSuggestionResultVO | null> {
      this.suggestionLoading = true;
      this.suggestionError = "";
      try {
        const result = await dispatchBulkSuggestion(payload);
        this.latestBulkSuggestion = result;
        if (payload.knowledgePoint && !this.dispatchedPoints.includes(payload.knowledgePoint)) {
          this.dispatchedPoints.push(payload.knowledgePoint);
        }
        return result;
      } catch (error) {
        this.suggestionError = toErrorMessage(error, "批量发送失败");
        return null;
      } finally {
        this.suggestionLoading = false;
      }
    }
  },
  persist: {
    key: "teacher-suggestions",
    paths: ["dispatchedPoints"]
  }
});
