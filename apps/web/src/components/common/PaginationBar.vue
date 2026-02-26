<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    page: number;
    size: number;
    totalPages: number;
    totalElements: number;
    disabled?: boolean;
    pageSizeOptions?: number[];
  }>(),
  {
    disabled: false,
    pageSizeOptions: () => [10, 20, 50]
  }
);

const emit = defineEmits<{
  "update:page": [value: number];
  "update:size": [value: number];
}>();

const safeTotalPages = computed(() => Math.max(1, props.totalPages || 1));
const canPrev = computed(() => !props.disabled && props.page > 1);
const canNext = computed(() => !props.disabled && props.page < safeTotalPages.value);

function prevPage(): void {
  if (!canPrev.value) {
    return;
  }
  emit("update:page", props.page - 1);
}

function nextPage(): void {
  if (!canNext.value) {
    return;
  }
  emit("update:page", props.page + 1);
}

function onPageSizeChange(event: Event): void {
  const target = event.target as HTMLSelectElement;
  const nextSize = Number(target.value);
  if (Number.isFinite(nextSize) && nextSize > 0) {
    emit("update:size", nextSize);
  }
}
</script>

<template>
  <div class="pager-wrap">
    <p class="pager-meta">第 {{ page }} / {{ safeTotalPages }} 页 · 共 {{ totalElements }} 条</p>
    <div class="pager-actions">
      <label class="pager-size">
        每页
        <select :value="size" :disabled="disabled" @change="onPageSizeChange">
          <option v-for="option in pageSizeOptions" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
      <button class="btn secondary small" type="button" :disabled="!canPrev" @click="prevPage">上一页</button>
      <button class="btn secondary small" type="button" :disabled="!canNext" @click="nextPage">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.pager-wrap {
  margin-top: 12px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.pager-meta {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.84rem;
}

.pager-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager-size {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 0.84rem;
}

.pager-size select {
  width: auto;
  min-height: 32px;
}

@media (max-width: 767px) {
  .pager-wrap {
    align-items: flex-start;
    flex-direction: column;
  }

  .pager-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
