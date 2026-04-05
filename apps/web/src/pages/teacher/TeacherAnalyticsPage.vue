<script setup lang="ts">
import { defineAsyncComponent, computed, onMounted, ref } from "vue";
import { NForm, NFormItem, NSelect, NButton, NAlert, NSpin } from "naive-ui";
import { Search, RefreshCw, Users, Activity, Radar, ShieldAlert } from "lucide-vue-next";
import type { StudentAnalyticsVO, StudentAttributionVO } from "../../services/contracts";
import { useAnalyticsStore } from "../../features/teacher-workspace/model/analytics";
import { useClassroomStore } from "../../features/teacher-workspace/model/classroom";

const AnalyticsCharts = defineAsyncComponent(
  () => import("../../components/teacher/AnalyticsCharts.vue")
);

const classroomStore = useClassroomStore();
const analyticsStore = useAnalyticsStore();
const studentId = ref("");

const studentOptions = computed(() =>
  classroomStore.students
    .filter((student) => Boolean(student.id))
    .map((student) => ({
      label: `${student.username || "未命名学生"}${student.className ? ` (${student.className})` : ""}`,
      value: student.id as string
    }))
);

const currentAttribution = computed((): StudentAttributionVO | null =>
  studentId.value ? (analyticsStore.attributionByStudent[studentId.value] ?? null) : null
);

const selectedStudent = computed(
  () => classroomStore.students.find((student) => student.id === studentId.value) || null
);

const classroomSnapshots = computed(() => analyticsStore.classroomAnalytics);

function accuracyOf(snapshot: StudentAnalyticsVO): number {
  const totalQuestions = Number(snapshot.totalQuestions || 0);
  const correctCount = Number(snapshot.correctCount || 0);
  if (totalQuestions <= 0) return 0;
  return Number(((correctCount / totalQuestions) * 100).toFixed(1));
}

function riskLevelOf(snapshot: StudentAnalyticsVO): "高风险" | "需关注" | "稳定" {
  const accuracy = accuracyOf(snapshot);
  const wrongBookCount = Number(snapshot.wrongBookCount || 0);
  if (accuracy < 60 || wrongBookCount >= 5) return "高风险";
  if (accuracy < 80 || wrongBookCount >= 2) return "需关注";
  return "稳定";
}

function riskClassOf(riskLevel: string): string {
  if (riskLevel === "高风险") return "danger";
  if (riskLevel === "需关注") return "warning";
  return "success";
}

const classroomSummary = computed(() => {
  const snapshots = classroomSnapshots.value;
  const totalStudents = snapshots.length;
  const highRiskCount = snapshots.filter((snapshot) => riskLevelOf(snapshot) === "高风险").length;
  const attentionCount = snapshots.filter((snapshot) => riskLevelOf(snapshot) !== "稳定").length;
  const unresolvedWrongBook = snapshots.reduce(
    (total, snapshot) => total + Number(snapshot.wrongBookCount || 0),
    0
  );
  const averageAccuracy =
    totalStudents === 0
      ? 0
      : Number(
          (
            snapshots.reduce((total, snapshot) => total + accuracyOf(snapshot), 0) / totalStudents
          ).toFixed(1)
        );
  const totalExercises = snapshots.reduce(
    (total, snapshot) => total + Number(snapshot.totalExercises || 0),
    0
  );

  return {
    totalStudents,
    highRiskCount,
    attentionCount,
    unresolvedWrongBook,
    averageAccuracy,
    averageExercises: totalStudents === 0 ? 0 : Number((totalExercises / totalStudents).toFixed(1))
  };
});

const knowledgeHotspots = computed(() => {
  const aggregate = new Map<
    string,
    { knowledgePoint: string; wrongCount: number; studentCount: number }
  >();

  classroomSnapshots.value.forEach((snapshot) => {
    const seenInStudent = new Set<string>();
    (snapshot.topWeakPoints || []).forEach((point) => {
      const knowledgePoint = point.knowledgePoint || "未命名知识点";
      const existing = aggregate.get(knowledgePoint) || {
        knowledgePoint,
        wrongCount: 0,
        studentCount: 0
      };
      existing.wrongCount += Number(point.wrongCount || 0);
      if (!seenInStudent.has(knowledgePoint)) {
        existing.studentCount += 1;
        seenInStudent.add(knowledgePoint);
      }
      aggregate.set(knowledgePoint, existing);
    });
  });

  return Array.from(aggregate.values())
    .sort(
      (left, right) => right.wrongCount - left.wrongCount || right.studentCount - left.studentCount
    )
    .slice(0, 5);
});

const classroomTemporalHotspots = computed(() => {
  const aggregate = new Map<
    string,
    { knowledgePoint: string; eventCount: number; studentCount: number }
  >();

  classroomSnapshots.value.forEach((snapshot) => {
    const seenInStudent = new Set<string>();
    (snapshot.realtimeState?.hotspotKnowledgePoints || []).forEach((point) => {
      const knowledgePoint = point.knowledgePoint || "未命名知识点";
      const existing = aggregate.get(knowledgePoint) || {
        knowledgePoint,
        eventCount: 0,
        studentCount: 0
      };
      existing.eventCount += Number(point.eventCount || 0);
      if (!seenInStudent.has(knowledgePoint)) {
        existing.studentCount += 1;
        seenInStudent.add(knowledgePoint);
      }
      aggregate.set(knowledgePoint, existing);
    });
  });

  return Array.from(aggregate.values())
    .sort(
      (left, right) => right.eventCount - left.eventCount || right.studentCount - left.studentCount
    )
    .slice(0, 5);
});

const studentPulse = computed(() =>
  classroomSnapshots.value
    .map((snapshot) => ({
      studentId: snapshot.studentId || "",
      username: snapshot.username || "未命名学生",
      accuracy: accuracyOf(snapshot),
      wrongBookCount: Number(snapshot.wrongBookCount || 0),
      totalExercises: Number(snapshot.totalExercises || 0),
      primaryWeakPoint: snapshot.topWeakPoints?.[0]?.knowledgePoint || "暂无明显薄弱点",
      riskLevel: riskLevelOf(snapshot),
      supportStage: snapshot.supportStage?.label || "待分析",
      interactionProfile: snapshot.interactionProfile || "等待行为数据",
      realtimeSignal: snapshot.realtimeState?.signals?.[0] || "近 10 分钟暂无新的课堂态信号"
    }))
    .sort(
      (left, right) => right.wrongBookCount - left.wrongBookCount || left.accuracy - right.accuracy
    )
);

async function loadSelectedAnalytics(): Promise<void> {
  if (!studentId.value) {
    return;
  }
  analyticsStore.lastStudentId = studentId.value;
  await Promise.all([
    analyticsStore.loadAnalytics(studentId.value),
    analyticsStore.loadAttribution(studentId.value)
  ]);
}

async function loadClassroomOverview(): Promise<void> {
  const studentIds = classroomStore.students
    .map((student) => student.id)
    .filter(Boolean) as string[];
  await analyticsStore.loadClassroomAnalytics(studentIds);
}

async function initializePage(): Promise<void> {
  await classroomStore.loadStudents();
  await loadClassroomOverview();

  if (classroomStore.students.length === 0) {
    return;
  }

  const cachedId = analyticsStore.lastStudentId;
  const exists = classroomStore.students.some((student) => student.id === cachedId);
  studentId.value = exists ? cachedId : classroomStore.students[0].id || "";
  await loadSelectedAnalytics();
}

async function refreshPage(): Promise<void> {
  await classroomStore.loadStudents();
  await Promise.all([loadSelectedAnalytics(), loadClassroomOverview()]);
}

onMounted(() => {
  void initializePage();
});
</script>

<template>
  <div class="analytics-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">学情分析</h1>
          <p class="workspace-subtitle">先看班级即时诊断，再下钻到单个学生的错因与支架建议。</p>
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
              @click="loadSelectedAnalytics"
            >
              <template #icon><Search :size="16" /></template>
              加载个体分析
            </n-button>
          </n-form-item>
          <n-form-item>
            <n-button
              secondary
              class="glass-pill"
              :loading="analyticsStore.classroomLoading || classroomStore.studentsLoading"
              @click="refreshPage"
            >
              <template #icon><RefreshCw :size="16" /></template>
              刷新班级总览
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
        v-if="analyticsStore.classroomError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ analyticsStore.classroomError }}</n-alert
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

      <n-spin :show="analyticsStore.classroomLoading">
        <div class="summary-grid">
          <div class="summary-card panel glass-card">
            <div class="summary-icon primary"><Users :size="22" /></div>
            <div>
              <p class="summary-label">班级总人数</p>
              <h3 class="summary-value">{{ classroomSummary.totalStudents }}</h3>
              <p class="summary-hint">当前已纳入教师工作台的学生数量</p>
            </div>
          </div>
          <div class="summary-card panel glass-card">
            <div class="summary-icon danger"><ShieldAlert :size="22" /></div>
            <div>
              <p class="summary-label">高风险学生</p>
              <h3 class="summary-value">{{ classroomSummary.highRiskCount }}</h3>
              <p class="summary-hint">正确率偏低或错题积压较多的学生</p>
            </div>
          </div>
          <div class="summary-card panel glass-card">
            <div class="summary-icon info"><Activity :size="22" /></div>
            <div>
              <p class="summary-label">班级平均正确率</p>
              <h3 class="summary-value">{{ classroomSummary.averageAccuracy }}%</h3>
              <p class="summary-hint">说明课堂即时诊断后的整体掌握程度</p>
            </div>
          </div>
          <div class="summary-card panel glass-card">
            <div class="summary-icon warning"><Radar :size="22" /></div>
            <div>
              <p class="summary-label">活跃错题压力</p>
              <h3 class="summary-value">{{ classroomSummary.unresolvedWrongBook }}</h3>
              <p class="summary-hint">
                {{ classroomSummary.attentionCount }} 名学生处于“需关注 / 高风险”状态
              </p>
            </div>
          </div>
        </div>

        <div class="overview-grid">
          <section class="panel glass-card hotspot-panel">
            <div class="panel-head">
              <div>
                <h2 class="panel-title">班级知识热点</h2>
                <p class="panel-note">聚合所有学生的薄弱点，定位课堂应优先复讲的知识环节。</p>
              </div>
              <span class="panel-badge">班级级视角</span>
            </div>

            <div v-if="knowledgeHotspots.length === 0" class="empty-block">
              暂无明显知识热点，当前班级表现相对稳定。
            </div>
            <div v-else class="hotspot-list">
              <div
                v-for="item in knowledgeHotspots"
                :key="item.knowledgePoint"
                class="hotspot-item"
              >
                <div>
                  <h3 class="hotspot-name">{{ item.knowledgePoint }}</h3>
                  <p class="hotspot-meta">
                    影响 {{ item.studentCount }} 人 · 累计错次 {{ item.wrongCount }}
                  </p>
                </div>
                <div class="hotspot-bar">
                  <span
                    class="hotspot-fill"
                    :style="{ width: `${Math.min(100, item.wrongCount * 12)}%` }"
                  ></span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel glass-card hotspot-panel">
            <div class="panel-head">
              <div>
                <h2 class="panel-title">近 10 分钟课堂态热点</h2>
                <p class="panel-note">
                  直接聚合 Redis 近时态画像，判断当前课堂窗口期最先该处理什么。
                </p>
              </div>
              <span class="panel-badge">Redis 近时态</span>
            </div>

            <div v-if="classroomTemporalHotspots.length === 0" class="empty-block">
              近 10 分钟暂无新的课堂态热点，当前可继续参考历史薄弱点。
            </div>
            <div v-else class="hotspot-list">
              <div
                v-for="item in classroomTemporalHotspots"
                :key="item.knowledgePoint"
                class="hotspot-item"
              >
                <div>
                  <h3 class="hotspot-name">{{ item.knowledgePoint }}</h3>
                  <p class="hotspot-meta">
                    即时事件 {{ item.eventCount }} 次 · 影响 {{ item.studentCount }} 人
                  </p>
                </div>
                <div class="hotspot-bar realtime">
                  <span
                    class="hotspot-fill realtime"
                    :style="{ width: `${Math.min(100, item.eventCount * 18)}%` }"
                  ></span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel glass-card roster-panel">
            <div class="panel-head">
              <div>
                <h2 class="panel-title">班级风险雷达</h2>
                <p class="panel-note">优先安排一对一跟进或课堂点拨的学生列表。</p>
              </div>
              <span class="panel-badge neutral">Teacher-in-the-Loop</span>
            </div>

            <div v-if="studentPulse.length === 0" class="empty-block">暂无班级学情数据。</div>
            <div v-else class="pulse-list">
              <button
                v-for="student in studentPulse"
                :key="student.studentId"
                type="button"
                class="pulse-card"
                @click="
                  studentId = student.studentId;
                  loadSelectedAnalytics();
                "
              >
                <div class="pulse-top">
                  <strong>{{ student.username }}</strong>
                  <span class="risk-tag" :class="riskClassOf(student.riskLevel)">
                    {{ student.riskLevel }}
                  </span>
                </div>
                <p class="pulse-main">
                  正确率 {{ student.accuracy }}% · 错题 {{ student.wrongBookCount }} 道
                </p>
                <p class="pulse-sub">{{ student.primaryWeakPoint }}</p>
                <p class="pulse-meta">
                  {{ student.supportStage }} · {{ student.interactionProfile }}
                </p>
                <p class="pulse-meta subtle">{{ student.realtimeSignal }}</p>
              </button>
            </div>
          </section>
        </div>
      </n-spin>

      <section class="selected-panel">
        <div class="section-title-row">
          <div>
            <h2 class="section-title">学生个体归因</h2>
            <p class="section-note">从班级视角下钻到单个学生，查看错因、薄弱点和支架建议。</p>
          </div>
        </div>

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
      </section>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 16px 24px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
}

.summary-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  min-height: 148px;
}

.summary-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: var(--shadow-float);
}

.summary-icon.primary {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
}

.summary-icon.danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}

.summary-icon.info {
  background: linear-gradient(135deg, #14b8a6, #0f766e);
}

.summary-icon.warning {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.summary-label {
  margin: 2px 0 6px;
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.summary-value {
  margin: 0;
  font-size: 2rem;
  font-family: var(--font-code);
}

.summary-hint {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.overview-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: var(--space-4);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.panel-title {
  margin: 0;
  font-size: 1.1rem;
}

.panel-note {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.panel-badge {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.14);
}

.panel-badge.neutral {
  color: #9a3412;
  background: rgba(245, 158, 11, 0.14);
}

.hotspot-list,
.pulse-list {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.hotspot-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 14px;
  align-items: center;
  padding: 16px 18px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.45);
}

.hotspot-name {
  margin: 0;
  font-size: 1rem;
}

.hotspot-meta {
  margin: 6px 0 0;
  color: var(--color-text-muted);
}

.hotspot-bar {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(59, 130, 246, 0.12);
}

.hotspot-bar.realtime {
  background: rgba(20, 184, 166, 0.14);
}

.hotspot-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}

.hotspot-fill.realtime {
  background: linear-gradient(90deg, #2dd4bf, #0f766e);
}

.pulse-card {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.5);
  text-align: left;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.pulse-card:hover {
  transform: translateY(-2px);
  border-color: rgba(59, 130, 246, 0.25);
  box-shadow: var(--shadow-float);
}

.pulse-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pulse-main {
  margin: 10px 0 6px;
  font-weight: 600;
}

.pulse-sub {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.pulse-meta {
  margin: 8px 0 0;
  color: #475569;
  font-size: 0.82rem;
}

.pulse-meta.subtle {
  line-height: 1.5;
}

.risk-tag {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
}

.risk-tag.danger {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.16);
}

.risk-tag.warning {
  color: #b45309;
  background: rgba(245, 158, 11, 0.16);
}

.risk-tag.success {
  color: #047857;
  background: rgba(16, 185, 129, 0.16);
}

.empty-block {
  margin-top: 18px;
  padding: 28px;
  text-align: center;
  color: var(--color-text-muted);
  border: 1px dashed var(--color-border-glass);
  border-radius: 16px;
}

.selected-panel {
  display: grid;
  gap: 12px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: end;
}

.section-title {
  margin: 0;
  font-size: 1.2rem;
}

.section-note {
  margin: 6px 0 0;
  color: var(--color-text-muted);
}

.chart-loading-state {
  padding: 32px 24px;
  text-align: center;
  color: var(--color-text-muted);
}

@media (max-width: 1024px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hotspot-item {
    grid-template-columns: 1fr;
  }

  .section-title-row,
  .panel-head {
    flex-direction: column;
  }
}
</style>
