<script setup lang="ts">
import { onErrorCaptured, ref } from "vue";

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
  <div v-else class="app-container">
    <section class="panel">
      <h2 class="panel-title">页面发生错误</h2>
      <p class="panel-note">{{ errorMessage }}</p>
      <button class="btn" type="button" @click="reloadPage">刷新页面</button>
    </section>
  </div>
</template>
