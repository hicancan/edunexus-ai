<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useTeacherStore } from "../../stores/teacher";

const teacherStore = useTeacherStore();
const studentId = ref(localStorage.getItem("teacher.analytics.studentId") || "");

async function loadAnalytics(): Promise<void> {
  if (!studentId.value) {
    teacherStore.analyticsError = "请输入学生 ID";
    return;
  }
  localStorage.setItem("teacher.analytics.studentId", studentId.value);
  await teacherStore.loadAnalytics(studentId.value);
}

onMounted(loadAnalytics);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">学情分析</h2>
        <p class="panel-note">查询学生做题量、平均分、高频错点等学情指标。</p>
      </div>
      <button class="btn" type="button" :disabled="teacherStore.analyticsLoading" @click="loadAnalytics">
        {{ teacherStore.analyticsLoading ? "查询中..." : "查询" }}
      </button>
    </header>

    <div class="field-block" style="max-width: 420px;">
      <label for="analytics-student-id">学生 ID</label>
      <input id="analytics-student-id" v-model="studentId" placeholder="请输入学生 UUID" />
    </div>

    <p v-if="teacherStore.analyticsError" class="status-box error" role="alert">{{ teacherStore.analyticsError }}</p>

    <div v-if="teacherStore.analyticsLoading && !teacherStore.analytics" class="status-box info">正在加载学情指标...</div>
    <div v-else-if="!teacherStore.analytics" class="status-box empty">暂无学情数据。</div>
    <div v-else class="analytics-grid">
      <article class="metric-card">
        <p class="metric-name">学生</p>
        <p class="metric-value">{{ teacherStore.analytics.username }}</p>
      </article>
      <article class="metric-card">
        <p class="metric-name">总做题次数</p>
        <p class="metric-value">{{ teacherStore.analytics.totalExercises }}</p>
      </article>
      <article class="metric-card">
        <p class="metric-name">总题目数</p>
        <p class="metric-value">{{ teacherStore.analytics.totalQuestions }}</p>
      </article>
      <article class="metric-card">
        <p class="metric-name">正确数</p>
        <p class="metric-value">{{ teacherStore.analytics.correctCount }}</p>
      </article>
      <article class="metric-card">
        <p class="metric-name">平均分</p>
        <p class="metric-value">{{ teacherStore.analytics.averageScore }}</p>
      </article>
      <article class="metric-card">
        <p class="metric-name">错题本数量</p>
        <p class="metric-value">{{ teacherStore.analytics.wrongBookCount }}</p>
      </article>
    </div>

    <section v-if="teacherStore.analytics?.topWeakPoints?.length" class="weak-points">
      <h3>高频薄弱知识点</h3>
      <div class="list-stack">
        <article
          v-for="(point, index) in teacherStore.analytics?.topWeakPoints || []"
          :key="`${point.knowledgePoint}-${index}`"
          class="list-item"
        >
          <div class="list-item-main">
            <p class="list-item-title">{{ point.knowledgePoint }}</p>
            <p class="list-item-meta">错误次数：{{ point.wrongCount }}</p>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.analytics-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #f7fbff;
  padding: 12px;
}

.metric-name {
  margin: 0;
  color: #4f708f;
  font-size: 0.83rem;
}

.metric-value {
  margin: 6px 0 0;
  font-size: 1.35rem;
  font-weight: 800;
  color: #21486d;
  overflow-wrap: anywhere;
}

.weak-points {
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  padding-top: 14px;
}

.weak-points h3 {
  margin: 0 0 8px;
}

@media (max-width: 1279px) {
  .analytics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
