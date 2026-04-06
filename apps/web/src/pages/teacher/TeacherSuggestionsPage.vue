<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { NButton, NEmpty, NSpin, NInput, useMessage } from "naive-ui";
import {
  Send,
  Users,
  ShieldAlert,
  Sparkles,
  CheckCircle2,
  ClipboardCheck,
  MessagesSquare
} from "lucide-vue-next";
import { useAnalyticsStore } from "../../features/teacher-workspace/model/analytics";
import { useSuggestionStore } from "../../features/teacher-workspace/model/suggestions";
import { useClassroomStore } from "../../features/teacher-workspace/model/classroom";
import { runInterventionSandbox } from "../../features/teacher-workspace/api/teacher.service";
import type { SandboxStrategy } from "../../services/contracts";

const analyticsStore = useAnalyticsStore();
const suggestionStore = useSuggestionStore();
const classroomStore = useClassroomStore();
const message = useMessage();
const dispatchingPoint = ref("");
const drafts = reactive<Record<string, string>>({});
const sandboxLoading = ref(false);
const sandboxStrategies = ref<SandboxStrategy[]>([]);

const classroomSnapshots = computed(() => analyticsStore.classroomAnalytics);

function ensureDraft(knowledgePoint: string, suggestionTemplate: string): void {
  if (!drafts[knowledgePoint]) {
    drafts[knowledgePoint] = suggestionTemplate;
  }
}

function targetStudents(knowledgePoint: string): string[] {
  return classroomSnapshots.value
    .filter((snapshot) =>
      (snapshot.topWeakPoints || []).some((point) => point.knowledgePoint === knowledgePoint)
    )
    .map((snapshot) => snapshot.username || "未命名学生")
    .slice(0, 4);
}

function supportSteps(knowledgePoint: string): string[] {
  return [
    `先围绕「${knowledgePoint}」统一复讲核心概念与典型错误。`,
    "再用 1 道低门槛诊断题确认学生是否已经补齐关键环节。",
    "最后把建议推送给命中的学生，安排一轮分层再练。"
  ];
}

function hasDispatchHistory(item: { dispatchedStudentCount?: number | null }): boolean {
  return Number(item.dispatchedStudentCount || 0) > 0;
}

function dispatchCoverage(item: {
  dispatchedStudentCount?: number | null;
  studentCount?: number | null;
}): string {
  const dispatchedStudentCount = Number(item.dispatchedStudentCount || 0);
  const studentCount = Number(item.studentCount || 0);
  return `${dispatchedStudentCount} / ${studentCount} 名学生已在后端登记`;
}

function formatDispatchedAt(raw?: string | null): string {
  if (!raw) {
    return "尚未正式下发";
  }
  return raw.replace("T", " ").slice(0, 16);
}

const enrichedRecommendations = computed(() =>
  analyticsStore.interventions.map((item) => {
    ensureDraft(item.knowledgePoint || "", item.suggestionTemplate || "");
    const impactedSnapshots = classroomSnapshots.value.filter((snapshot) =>
      (snapshot.topWeakPoints || []).some((point) => point.knowledgePoint === item.knowledgePoint)
    );
    const supportStageLabels = Array.from(
      new Set(impactedSnapshots.map((snapshot) => snapshot.supportStage?.label).filter(Boolean))
    );
    return {
      ...item,
      targets: targetStudents(item.knowledgePoint || ""),
      steps: supportSteps(item.knowledgePoint || ""),
      dispatchedStudentCount: Number(item.dispatchedStudentCount || 0),
      fullyDispatched: Boolean(item.fullyDispatched),
      lastDispatchedAt: item.lastDispatchedAt || null,
      evidenceSummary:
        item.studentCount && item.totalWrongCount
          ? `当前共有 ${item.studentCount} 名学生在该知识点出现 ${item.totalWrongCount} 次累计失误${supportStageLabels.length ? `，主要处于${supportStageLabels.join(" / ")}` : ""}。`
          : "当前建议来自系统对错题与薄弱点的聚合分析。"
    };
  })
);

async function prepareContext(): Promise<void> {
  if (!classroomStore.students.length) {
    await classroomStore.loadStudents();
  }
  if (!analyticsStore.classroomAnalytics.length && classroomStore.students.length) {
    const studentIds = classroomStore.students
      .map((student) => student.id)
      .filter(Boolean) as string[];
    await analyticsStore.loadClassroomAnalytics(studentIds);
  }
}

async function dispatchIntervention(knowledgePoint: string): Promise<void> {
  dispatchingPoint.value = knowledgePoint;
  try {
    const result = await suggestionStore.dispatchSuggestion({
      knowledgePoint,
      suggestion: drafts[knowledgePoint] || ""
    });
    if (!result) {
      message.error(suggestionStore.suggestionError || "发送失败");
      return;
    }
    const affectedCount = Number(result.affectedCount || result.createdCount || 0);
    const createdCount = Number(result.createdCount || 0);
    const updatedCount = Number(result.updatedCount || 0);
    message.success(
      updatedCount > 0
        ? `已同步 ${affectedCount} 名学生，其中新增 ${createdCount} 条、更新 ${updatedCount} 条`
        : `已发送给 ${affectedCount} 名学生`
    );
    await analyticsStore.loadInterventions();
  } finally {
    dispatchingPoint.value = "";
  }
}

async function runSandbox(): Promise<void> {
  sandboxLoading.value = true;
  try {
    const totalStudents = classroomStore.students.length || 30;

    if (analyticsStore.interventions.length === 0) {
      message.warning("当前没有错题聚类数据，无法推演");
      return;
    }

    const res = await runInterventionSandbox({ studentCount: totalStudents });
    const rawData = (res as unknown as Record<string, unknown>).data;
    sandboxStrategies.value = Array.isArray(rawData) ? rawData : [];
    message.success(`沙盘推演完成，生成 ${sandboxStrategies.value.length} 条干预策略`);
  } catch (err: unknown) {
    const errMsg = err instanceof Error ? err.message : String(err);
    message.error("沙盘推演失败: " + errMsg);
  } finally {
    sandboxLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([analyticsStore.loadInterventions(), prepareContext()]);
});
</script>

<template>
  <div class="suggestions-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header" style="justify-content: space-between; display: flex; align-items: center;">
        <div>
          <h1 class="workspace-title">教师干预建议</h1>
          <p class="workspace-subtitle">
            系统先诊断，教师再确认、改写并下发，保证关键干预始终在教师掌控中。
          </p>
        </div>
        <n-button type="primary" :loading="sandboxLoading" @click="runSandbox">
          <template #icon><Sparkles :size="16" /></template>
          一键生成干预沙盘推演
        </n-button>
      </div>

      <!-- 沙盘推演结果展示 -->
      <div v-if="sandboxStrategies.length > 0" class="intervention-grid" style="margin-bottom: 24px;">
      <div v-for="(strat, idx) in sandboxStrategies" :key="idx" class="panel glass-card int-card" style="border-top-color: var(--color-primary);">
           <div class="int-header">
             <div class="int-title-area">
               <h3 class="int-topic">{{ strat.strategy_name }}</h3>
               <span class="int-meta" style="background: rgba(16, 185, 129, 0.1); color: var(--color-success);">预估修复率: {{ strat.estimated_fix_rate }}</span>
               <span class="int-meta" style="margin-left: 8px;">预估耗时: {{ strat.estimated_minutes }} min</span>
             </div>
           </div>
           <div class="int-body">
             <div class="evidence-box" style="margin-top: -10px;">
                <p class="evidence-title"><Sparkles :size="14" />AI 沙盘推演建议</p>
                <p class="evidence-copy">{{ strat.description }}</p>
                <p v-if="strat.target_knowledge_points?.length" class="evidence-copy" style="margin-top: 4px; opacity: 0.8;">
                  涉及知识点：{{ strat.target_knowledge_points.join('、') }}
                </p>
             </div>
           </div>
        </div>
      </div>

      <n-spin :show="analyticsStore.interventionsLoading || suggestionStore.suggestionLoading">
        <n-empty
          v-if="enrichedRecommendations.length === 0"
          description="暂无可发送的教学建议"
          style="margin-top: 40px"
        />

        <div v-else class="intervention-grid">
          <div
            v-for="item in enrichedRecommendations"
            :key="item.knowledgePoint"
            class="panel glass-card int-card"
            :class="{ 'dispatched-card': hasDispatchHistory(item) }"
          >
            <div class="int-header">
              <div class="int-icon">
                <ShieldAlert v-if="!hasDispatchHistory(item)" :size="24" class="text-danger" />
                <CheckCircle2 v-else :size="24" class="text-success" />
              </div>
              <div class="int-title-area">
                <div class="title-row">
                  <h3 class="int-topic">{{ item.knowledgePoint }}</h3>
                  <span class="teacher-loop-badge">
                    <ClipboardCheck :size="12" />
                    教师在环确认
                  </span>
                </div>
                <span class="int-meta">
                  <Users :size="14" style="margin-right: 4px" />
                  影响学生：{{ item.studentCount }} 人 · 累计错次：{{ item.totalWrongCount }}
                </span>
                <span v-if="item.dispatchedStudentCount > 0" class="dispatch-meta">
                  已覆盖 {{ dispatchCoverage(item) }} · 最近确认
                  {{ formatDispatchedAt(item.lastDispatchedAt) }}
                </span>
                <span v-if="item.generationSource === 'AI'" class="int-ai-meta">
                  AI 草案 · {{ item.provider || "unknown" }} / {{ item.model || "unknown" }} ·
                  {{ item.latencyMs || 0 }} ms
                </span>
              </div>
            </div>

            <div class="int-body">
              <div class="evidence-box">
                <p class="evidence-title">
                  <MessagesSquare :size="14" />
                  触发证据
                </p>
                <p class="evidence-copy">{{ item.evidenceSummary }}</p>
                <div v-if="item.targets.length" class="target-list">
                  <span v-for="name in item.targets" :key="name" class="target-chip">{{
                    name
                  }}</span>
                </div>
              </div>

              <div class="ai-suggest-box">
                <span class="ai-badge">
                  <Sparkles :size="12" style="margin-right: 4px" />
                  系统建议草案
                </span>
                <p v-if="item.routerDecision" class="ai-router-note">{{ item.routerDecision }}</p>
                <n-input
                  v-model:value="drafts[item.knowledgePoint || '']"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  placeholder="教师可在发送前修改建议内容"
                />
              </div>

              <div class="support-flow">
                <p class="flow-title">建议下发前的支架路径</p>
                <ol class="flow-list">
                  <li v-for="step in item.steps" :key="step">{{ step }}</li>
                </ol>
              </div>
            </div>

            <div class="int-footer">
              <n-button
                type="primary"
                :secondary="hasDispatchHistory(item)"
                :loading="dispatchingPoint === (item.knowledgePoint || '')"
                :disabled="
                  !drafts[item.knowledgePoint || ''] ||
                  (dispatchingPoint !== '' && dispatchingPoint !== (item.knowledgePoint || ''))
                "
                @click="dispatchIntervention(item.knowledgePoint || '')"
              >
                <template #icon><Send :size="16" /></template>
                {{ hasDispatchHistory(item) ? "更新并重新下发" : "教师确认并批量发送" }}
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>
    </div>
  </div>
</template>

<style scoped>
.intervention-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--space-5);
  margin-top: 16px;
}

.int-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  border-top: 4px solid var(--color-danger);
}

.int-card.dispatched-card {
  border-top-color: var(--color-success);
  opacity: 0.9;
}

.int-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.int-icon {
  background: rgba(255, 255, 255, 0.8);
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.title-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.teacher-loop-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
  color: #92400e;
  background: rgba(245, 158, 11, 0.14);
}

.text-danger {
  color: var(--color-danger);
}

.text-success {
  color: var(--color-success);
}

.int-title-area {
  flex: 1;
}

.int-topic {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  line-height: 1.4;
}

.int-meta {
  display: inline-flex;
  align-items: center;
  color: var(--color-danger);
  font-size: 0.85rem;
  font-weight: 600;
  background: rgba(239, 68, 68, 0.1);
  padding: 4px 10px;
  border-radius: 6px;
  margin-top: 10px;
}

.int-ai-meta {
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  font-size: 0.8rem;
  color: #0f766e;
  background: rgba(20, 184, 166, 0.12);
  padding: 4px 10px;
  border-radius: 999px;
}

.dispatch-meta {
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  font-size: 0.8rem;
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.1);
  padding: 4px 10px;
  border-radius: 999px;
}

.dispatched-card .int-meta {
  color: var(--color-success);
  background: rgba(16, 185, 129, 0.1);
}

.int-body {
  display: grid;
  gap: 16px;
}

.evidence-box,
.support-flow,
.ai-suggest-box {
  border-radius: 12px;
  padding: 16px;
}

.ai-router-note {
  margin: 10px 0 12px;
  color: var(--color-text-muted);
  font-size: 0.84rem;
  line-height: 1.5;
}

.evidence-box {
  background: rgba(248, 250, 252, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.evidence-title,
.flow-title {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin: 0 0 10px;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-text-main);
}

.evidence-copy {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.target-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.target-chip {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: #1d4ed8;
  font-size: 0.78rem;
  font-weight: 700;
}

.ai-suggest-box {
  background: linear-gradient(135deg, rgba(92, 101, 246, 0.05), rgba(92, 101, 246, 0.15));
  border: 1px solid rgba(92, 101, 246, 0.2);
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.75rem;
  color: var(--color-primary);
  font-weight: 700;
  margin-bottom: 10px;
}

.support-flow {
  background: rgba(255, 255, 255, 0.6);
  border: 1px dashed rgba(245, 158, 11, 0.35);
}

.flow-list {
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-main);
  line-height: 1.7;
}

.flow-list li + li {
  margin-top: 6px;
}

.int-footer {
  margin-top: 24px;
  text-align: right;
}

@media (max-width: 768px) {
  .intervention-grid {
    grid-template-columns: 1fr;
  }
}
</style>
