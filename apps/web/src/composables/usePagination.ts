import { ref, computed } from "vue";

export interface PaginationOptions {
  initialPage?: number;
  initialSize?: number;
}

export function usePagination(options: PaginationOptions = {}) {
  const page = ref(options.initialPage ?? 1);
  const size = ref(options.initialSize ?? 20);
  const totalElements = ref(0);
  const loading = ref(false);

  const totalPages = computed(() =>
    totalElements.value > 0 ? Math.ceil(totalElements.value / size.value) : 1
  );

  function setPage(newPage: number): void {
    page.value = newPage;
  }

  function setTotal(total: number): void {
    totalElements.value = total;
  }

  function reset(): void {
    page.value = options.initialPage ?? 1;
    totalElements.value = 0;
  }

  return { page, size, totalPages, totalElements, loading, setPage, setTotal, reset };
}
