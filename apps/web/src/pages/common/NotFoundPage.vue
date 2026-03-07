<script setup lang="ts">
import { NResult, NButton } from "naive-ui";
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <div class="error-page-container">
    <n-result
      status="404"
      title="页面不存在"
      description="你访问的地址无效或已变更，请返回登录页重新进入系统。"
    >
      <template #footer>
        <n-button type="primary" @click="router.push('/login')">前往登录页</n-button>
      </template>
    </n-result>
  </div>
</template>

<style scoped>
.error-page-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
