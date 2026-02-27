<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NButton,
  NSpace,
  NText,
  NAlert,
  NTag,
  NSpin
} from "naive-ui";
import { RefreshCw, User as UserIcon, AlertTriangle } from "lucide-vue-next";
import { getMe } from "../../features/auth/api/auth.service";
import { toErrorMessage } from "../../services/error-message";
import type { UserVO } from "../../services/contracts";
import { useAuthStore } from "../../features/auth/model/auth";

const auth = useAuthStore();
const profile = ref<UserVO | null>(null);
const loading = ref(false);
const error = ref("");

const mockWeakPoints = ref([
  { name: "JavaScript 闭包与词法环境", errorRate: 68 },
  { name: "Vue3 组件生命周期与副作用", errorRate: 55 },
  { name: "CSS 弹性盒与网格布局原理", errorRate: 42 }
]);

async function loadProfile(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    profile.value = await getMe();
    if (profile.value) {
      auth.setUser(profile.value);
    }
  } catch (requestError) {
    error.value = toErrorMessage(requestError, "加载个人信息失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadProfile);

function getRoleType(role: string): any {
  if (role === "ADMIN") return "error";
  if (role === "TEACHER") return "warning";
  if (role === "STUDENT") return "info";
  return "default";
}

function getStatusType(status: string): "success" | "error" {
  if (status === "ACTIVE") return "success";
  return "error";
}
</script>

<template>
  <div class="profile-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">个人主页</n-text>
          <n-text depth="3">展示当前登录用户的账号详情与身份状态。</n-text>
        </div>
      </div>

      <n-alert v-if="error" type="error" :show-icon="true">{{ error }}</n-alert>

      <n-card :bordered="true" class="profile-card">
        <template #header>
           <n-space align="center" :size="8">
             <UserIcon :size="20" class="card-icon" />
             <n-text strong>基础信息</n-text>
           </n-space>
        </template>
        <template #header-extra>
          <n-button secondary type="primary" :loading="loading" @click="loadProfile">
             <template #icon><RefreshCw :size="14" /></template>
             获取最新资料
          </n-button>
        </template>

        <n-spin :show="loading && !profile">
          <div v-if="!profile && !loading" style="padding: 40px; text-align: center;">
             <n-text depth="3">暂无个人信息数据</n-text>
          </div>
          <n-descriptions
            v-else-if="profile"
            label-placement="left"
            bordered
            :column="2"
            size="large"
          >
            <n-descriptions-item label="用户名">
              <n-text strong>{{ profile.username || "--" }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="账号状态">
              <n-tag :type="getStatusType(profile.status || '')" size="small" :bordered="false">
                {{ profile.status || "--" }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="系统角色">
              <n-tag :type="getRoleType(profile.role || '')" :bordered="false">
                {{ profile.role || "--" }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="用户 ID">
              <n-text code>{{ profile.id || "--" }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="绑定邮箱">
              {{ profile.email || "未绑定" }}
            </n-descriptions-item>
            <n-descriptions-item label="绑定手机号码">
              {{ profile.phone || "未绑定" }}
            </n-descriptions-item>
          </n-descriptions>
        </n-spin>
      </n-card>

      <n-card :bordered="true" class="warning-card animate-pop">
        <template #header>
           <n-space align="center" :size="8">
             <AlertTriangle :size="20" class="warning-icon text-danger" />
             <n-text strong style="color: var(--color-danger); font-size: 1.1rem;">AI 诊断：核心薄弱知识域 (Top 3)</n-text>
           </n-space>
        </template>
        
        <p class="warning-desc">根据您近期 50 道推演题的报错聚类，Nexus Agent 捕获以下知识坍缩点：</p>
        
        <div class="weak-points-list">
          <div class="weak-point-item" v-for="(item, index) in mockWeakPoints" :key="index">
            <div class="wp-header">
              <n-text strong class="wp-name">🎯 {{ item.name }}</n-text>
              <span class="wp-badge">失误率 {{ item.errorRate }}%</span>
            </div>
            <div class="wp-bar-bg">
              <div class="wp-bar-fill" :style="{ width: item.errorRate + '%' }"></div>
            </div>
          </div>
        </div>
      </n-card>
    </n-space>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-card {
  margin-top: 8px;
  border-radius: 8px;
}

.card-icon {
  color: var(--color-primary);
}

@media (max-width: 768px) {
  :deep(.n-descriptions) {
    --n-title-text-color: var(--n-title-text-color); /* To prevent any strange inheritance */
  }
}

.warning-card {
  border-color: rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.02);
  border-radius: 12px;
}

.warning-icon {
  margin-top: 2px;
}

.text-danger {
  color: var(--color-danger);
}

.warning-desc {
  margin: 0 0 16px 0;
  color: var(--color-text-muted);
  font-size: 0.95rem;
}

.weak-points-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.weak-point-item {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(239, 68, 68, 0.15);
  padding: 16px;
  border-radius: 8px;
}

.wp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.wp-name {
  font-size: 1.05rem;
  color: var(--color-text-main);
}

.wp-badge {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 700;
}

.wp-bar-bg {
  width: 100%;
  height: 8px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  overflow: hidden;
}

.wp-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #f87171, #ef4444);
  border-radius: 4px;
  transition: width 1s ease-out;
}
</style>
