<script setup lang="ts">
import { NResult, NButton } from "naive-ui";
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <div class="error-page-container">
    <n-result
      status="403"
      title="无权限访问"
      description="当前账号没有权限访问该页面，请切换账号或联系管理员。"
    >
      <template #footer>
        <n-button type="primary" @click="router.push('/login')">返回登录页</n-button>
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
