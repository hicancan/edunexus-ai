<script setup lang="ts">
import { computed, onMounted } from "vue";
import {
  NCard,
  NGrid,
  NGridItem,
  NStatistic,
  NSpace,
  NText,
  NAlert,
  NButton
} from "naive-ui";
import { RefreshCw, Activity, Users, FileText, Database, MessageSquare } from "lucide-vue-next";
import { useAdminStore } from "../../features/admin/model/admin";

const adminStore = useAdminStore();

const METRIC_LABELS: Record<string, { label: string; icon: any; color: string }> = {
  totalUsers: { label: "总用户数", icon: Users, color: "#2080f0" },
  totalStudents: { label: "挂载学生", icon: Users, color: "#18a058" },
  totalTeachers: { label: "教职工", icon: Users, color: "#f2c97d" },
  totalAdmins: { label: "神级管理员", icon: Users, color: "#d03050" },
  totalChatSessions: { label: "全域问答会话", icon: MessageSquare, color: "#2080f0" },
  totalChatMessages: { label: "知识图谱消息", icon: MessageSquare, color: "#18a058" },
  totalExerciseRecords: { label: "历练卷宗", icon: FileText, color: "#f2c97d" },
  totalQuestions: { label: "底层题库元", icon: Database, color: "#2080f0" },
  totalDocuments: { label: "治理文档集", icon: FileText, color: "#18a058" },
  totalLessonPlans: { label: "AI生成教案", icon: FileText, color: "#d03050" },
  totalAiQuestionSessions: { label: "AI组卷批次", icon: Activity, color: "#2080f0" },
  totalVectors: { label: "核心向量数据点", icon: Database, color: "#18a058" }
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
          <n-text tag="h2" class="page-title">数智化塔台 (Command Center)</n-text>
          <n-text depth="3">展示高并发用户规模、会话矩阵、向量规模等底层水位线指标。</n-text>
        </div>
        <n-button type="primary" secondary :loading="adminStore.metricsLoading" @click="loadMetrics">
           <template #icon><RefreshCw :size="16" /></template>
           同步核心大盘
        </n-button>
      </div>

      <n-alert v-if="adminStore.metricsError" type="error" :show-icon="true">{{ adminStore.metricsError }}</n-alert>

      <div v-if="adminStore.metrics" class="dashboard-grid">
         <n-grid x-gap="16" y-gap="16" :cols="1" :m-cols="3" :l-cols="4">
            <n-grid-item v-for="item in metricEntries" :key="item.key">
               <n-card :bordered="false" class="metric-card" size="small">
                  <n-statistic :label="item.label" :value="item.value">
                     <template #prefix>
                        <component :is="item.icon" :size="20" :style="{ color: item.color, marginRight: '6px' }" />
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
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
  border-radius: 8px;
  background: #ffffff;
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.06);
}

:deep(.n-statistic-value) {
  font-family: var(--font-code);
  font-weight: 700;
  font-size: 1.6rem;
}
</style>
