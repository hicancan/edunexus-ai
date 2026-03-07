import { ref } from "vue";
import { toErrorMessage } from "../services/error-message";

export function useAsyncAction<T = void>(defaultErrorMessage = "操作失败") {
  const loading = ref(false);
  const error = ref("");

  async function execute(action: () => Promise<T>): Promise<T | null> {
    loading.value = true;
    error.value = "";
    try {
      return await action();
    } catch (err) {
      error.value = toErrorMessage(err, defaultErrorMessage);
      return null;
    } finally {
      loading.value = false;
    }
  }

  function clearError(): void {
    error.value = "";
  }

  return { loading, error, execute, clearError };
}
