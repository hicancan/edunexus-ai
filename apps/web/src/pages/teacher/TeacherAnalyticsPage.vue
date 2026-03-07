<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref, computed } from "vue";
import { NForm, NFormItem, NSelect, NButton, NAlert } from "naive-ui";
import { Search } from "lucide-vue-next";
import { useMessage } from "naive-ui";
import type { StudentAttributionVO } from "../../services/contracts";
import { useAnalyticsStore } from "../../features/teacher-workspace/model/analytics";
import { useClassroomStore } from "../../features/teacher-workspace/model/classroom";

const AnalyticsCharts = defineAsyncComponent(
  () => import("../../components/teacher/AnalyticsCharts.vue")
);

const classroomStore = useClassroomStore();
const analyticsStore = useAnalyticsStore();
const message = useMessage();
const studentId = ref("");

const studentOptions = computed(() =>
  classroomStore.students
    .filter((s) => Boolean(s.id))
    .map((s) => ({
      label: `${s.username || "未命名学生"}${s.className ? ` (${s.className})` : ""}`,
      value: s.id as string
    }))
);

const currentAttribution = computed((): StudentAttributionVO | null =>
  studentId.value ? (analyticsStore.attributionByStudent[studentId.value] ?? null) : null
);

const selectedStudent = computed(
  () => classroomStore.students.find((s) => s.id === studentId.value) || null
);

async function loadAnalytics(): Promise<void> {
  if (!studentId.value) {
    message.warning("请先选择学生");
    return;
  }
  analyticsStore.lastStudentId = studentId.value;
  await Promise.all([
    analyticsStore.loadAnalytics(studentId.value),
    analyticsStore.loadAttribution(studentId.value)
  ]);
}

onMounted(() => {
  void classroomStore.loadStudents().then(async () => {
    if (classroomStore.students.length === 0) return;
    const cachedId = analyticsStore.lastStudentId;
    const exists = classroomStore.students.some((s) => s.id === cachedId);
    studentId.value = exists ? cachedId : classroomStore.students[0].id || "";
    if (studentId.value) await loadAnalytics();
  });
});
</script>

<template>
  <div class="analytics-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">学情分析</h1>
          <p class="workspace-subtitle">查看单个学生的练习、错题和知识点薄弱情况。</p>
        </div>
      </div>

      <div class="panel glass-card search-panel">
        <n-form inline label-placement="left" :show-feedback="false" class="ethereal-form">
          <n-form-item label="学生">
            <n-select
              v-model:value="studentId"
              :options="studentOptions"
              placeholder="请选择学生"
              style="width: 360px"
              filterable
              :loading="classroomStore.studentsLoading"
            />
          </n-form-item>
          <n-form-item>
            <n-button
              type="primary"
              class="animate-pop glass-pill-btn"
              :loading="analyticsStore.analyticsLoading || analyticsStore.attributionLoading"
              @click="loadAnalytics"
            >
              <template #icon><Search :size="16" /></template>
              加载分析
            </n-button>
          </n-form-item>
        </n-form>
      </div>

      <n-alert
        v-if="classroomStore.studentsError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ classroomStore.studentsError }}</n-alert
      >
      <n-alert
        v-if="analyticsStore.analyticsError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ analyticsStore.analyticsError }}</n-alert
      >
      <n-alert
        v-if="analyticsStore.attributionError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ analyticsStore.attributionError }}</n-alert
      >

      <Suspense>
        <AnalyticsCharts
          v-if="analyticsStore.analytics"
          :selected-student="selectedStudent"
          :current-attribution="currentAttribution"
        />
        <template #fallback>
          <div v-if="analyticsStore.analytics" class="panel glass-card chart-loading-state">
            图表加载中...
          </div>
        </template>
      </Suspense>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 16px 24px;
}

.chart-loading-state {
  padding: 32px 24px;
  text-align: center;
  color: var(--color-text-muted);
}
</style>
