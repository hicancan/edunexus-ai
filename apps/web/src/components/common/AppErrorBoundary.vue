<script setup lang="ts">
import { onErrorCaptured, ref } from "vue";
import { NResult, NButton } from "naive-ui";

const hasError = ref(false);
const errorMessage = ref("页面渲染异常，请刷新后重试。");

onErrorCaptured((error) => {
  hasError.value = true;
  if (error instanceof Error && error.message) {
    errorMessage.value = error.message;
  }
  return false;
});

function reloadPage(): void {
  window.location.reload();
}
</script>

<template>
  <slot v-if="!hasError" />
  <div v-else class="error-boundary-container">
    <NResult
      status="500"
      title="页面发生致命错误"
      :description="errorMessage"
    >
      <template #footer>
        <NButton type="primary" @click="reloadPage">刷新页面</NButton>
      </template>
    </NResult>
  </div>
</template>

<style scoped>
.error-boundary-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-soft);
}
</style>
