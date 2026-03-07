<script setup lang="ts">
import { computed, onMounted } from "vue";
import { NCard, NGrid, NGridItem, NStatistic, NSpace, NText, NAlert, NButton } from "naive-ui";
import { RefreshCw, Activity, Users, FileText, Database, MessageSquare } from "lucide-vue-next";
import { useAdminStore } from "../../features/admin/model/admin";

const adminStore = useAdminStore();

const METRIC_LABELS: Record<string, { label: string; icon: any; color: string }> = {
  totalUsers: { label: "总用户数", icon: Users, color: "#2080f0" },
  totalStudents: { label: "学生数", icon: Users, color: "#18a058" },
  totalTeachers: { label: "教师数", icon: Users, color: "#f2c97d" },
  totalAdmins: { label: "管理员数", icon: Users, color: "#d03050" },
  totalChatSessions: { label: "问答会话数", icon: MessageSquare, color: "#2080f0" },
  totalChatMessages: { label: "问答消息数", icon: MessageSquare, color: "#18a058" },
  totalExerciseRecords: { label: "练习记录数", icon: FileText, color: "#f2c97d" },
  totalQuestions: { label: "题库题目数", icon: Database, color: "#2080f0" },
  totalDocuments: { label: "文档数", icon: FileText, color: "#18a058" },
  totalLessonPlans: { label: "教案数", icon: FileText, color: "#d03050" },
  totalAiQuestionSessions: { label: "AI 出题会话数", icon: Activity, color: "#2080f0" },
  totalVectors: { label: "向量数", icon: Database, color: "#18a058" },
  totalKnowledgeChunks: { label: "知识片段数", icon: Database, color: "#7c3aed" }
};

const metricEntries = computed(() => {
  if (!adminStore.metrics) {
    return [];
  }
  return Object.entries(adminStore.metrics).map(([key, value]) => {
    const config = METRIC_LABELS[key] || { label: key, icon: Activity, color: "#2080f0" };
    return {
      key,
      value,
      ...config
    };
  });
});

async function loadMetrics(): Promise<void> {
  await adminStore.loadMetrics();
}

onMounted(loadMetrics);
</script>

<template>
  <div class="admin-dashboard">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">平台数据看板</n-text>
          <n-text depth="3">展示平台用户、内容和 AI 相关核心指标。</n-text>
        </div>
        <n-button
          type="primary"
          secondary
          :loading="adminStore.metricsLoading"
          @click="loadMetrics"
        >
          <template #icon><RefreshCw :size="16" /></template>
          刷新数据
        </n-button>
      </div>

      <n-alert v-if="adminStore.metricsError" type="error" :show-icon="true">{{
        adminStore.metricsError
      }}</n-alert>

      <div v-if="adminStore.metrics" class="dashboard-grid">
        <n-grid x-gap="16" y-gap="16" :cols="1" :m-cols="3" :l-cols="4">
          <n-grid-item v-for="item in metricEntries" :key="item.key">
            <n-card :bordered="false" class="metric-card" size="small">
              <n-statistic :label="item.label" :value="item.value">
                <template #prefix>
                  <component
                    :is="item.icon"
                    :size="20"
                    :style="{ color: item.color, marginRight: '6px' }"
                  />
                </template>
              </n-statistic>
            </n-card>
          </n-grid-item>
        </n-grid>
      </div>
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

.dashboard-grid {
  margin-top: 10px;
}

.metric-card {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  border-radius: 8px;
  background: #ffffff;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

:deep(.n-statistic-value) {
  font-family: var(--font-code);
  font-weight: 700;
  font-size: 1.6rem;
}
</style>
