<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart, BarChart, GraphChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import VChart from "vue-echarts";
import { RefreshCw, User as UserIcon, AlertTriangle, Sparkles, TrendingUp } from "lucide-vue-next";
import { getMe } from "../../features/auth/api/auth.service";
import {
  getProfileAnalytics,
  getKnowledgeTopology
} from "../../features/student/api/student.service";
import { toErrorMessage } from "../../services/error-message";
import type { StudentAnalyticsVO, UserVO, KnowledgeTopologyVO } from "../../services/contracts";
import { useAuthStore } from "../../features/auth/model/auth";

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  GraphChart,
  GridComponent,
  TooltipComponent,
  LegendComponent
]);

const auth = useAuthStore();
const profile = ref<UserVO | null>(null);
const analytics = ref<StudentAnalyticsVO | null>(null);
const topologyData = ref<KnowledgeTopologyVO | null>(null);
const loading = ref(false);
const analyticsLoading = ref(false);
const topologyLoading = ref(false);
const error = ref("");

const topWeakPoints = computed(() => analytics.value?.topWeakPoints?.slice(0, 5) || []);
const recentPerformance = computed(() => analytics.value?.recentPerformance || []);
const latestAccuracy = computed(() => Number(analytics.value?.recentAccuracy || 0));
const rollingAccuracy = computed(() => Number(analytics.value?.rollingAccuracy || 0));
const aiCompletionRate = computed(() => Number(analytics.value?.aiCompletionRate || 0));
const activeWrongCount = computed(() => Number(analytics.value?.wrongBookCount || 0));
const masteredWrongCount = computed(() => Number(analytics.value?.masteredWrongCount || 0));
const activeDays7d = computed(() => Number(analytics.value?.activeDays7d || 0));
const interactionProfile = computed(
  () => analytics.value?.interactionProfile || "等待新近课堂数据"
);
const teacherSuggestionCount = computed(() => Number(analytics.value?.teacherSuggestionCount || 0));
const behaviorSignals = computed(() => analytics.value?.behaviorSignals || []);
const recommendedActions = computed(() => analytics.value?.recommendedActions || []);
const realtimeState = computed(() => {
  const fallback = {
    dataState: "UNAVAILABLE",
    windowMinutes: 10,
    recentChatQuestions: null,
    recentExerciseSubmissions: null,
    recentAiInteractions: null,
    recentWrongCount: null,
    recentQuestionAttempts: null,
    recentErrorDensity: null,
    hotspotKnowledgePoints: [],
    signals: ["近时态课堂状态暂不可用。"]
  };
  return analytics.value?.realtimeState || fallback;
});
const realtimeHotspots = computed(() => realtimeState.value.hotspotKnowledgePoints || []);
const latestActivityLabel = computed(() => {
  const raw = analytics.value?.latestActivityAt;
  if (!raw) return "暂无";
  return raw.replace("T", " ").slice(0, 16);
});
const supportStage = computed(() => {
  const fallback = {
    label: "等待新近数据",
    description: "完成一轮提问或练习后，系统会自动更新支持区间。",
    tone: "warning",
    supportZone: "待生成"
  };
  return analytics.value?.supportStage || fallback;
});

const recordsLineOption = computed(() => {
  const rows = recentPerformance.value;
  return {
    tooltip: {
      trigger: "axis",
      formatter: (params: Array<{ dataIndex: number; seriesName: string; value: number }>) => {
        const index = params[0]?.dataIndex ?? 0;
        const record = rows[index];
        const label = record?.createdAt?.slice(5, 16).replace("T", " ") || `记录 ${index + 1}`;
        const source = record?.source === "AI_PRACTICE" ? "AI 再练" : "课堂练习";
        const lines = [`${label} · ${source}`];
        params.forEach((item) => {
          lines.push(`${item.seriesName}：${item.value}`);
        });
        return lines.join("<br/>");
      }
    },
    legend: { data: ["得分", "正确率"], bottom: 0 },
    grid: { left: 24, right: 36, top: 42, bottom: 48, containLabel: true },
    xAxis: {
      type: "category",
      data: rows.map((record, index) => record.createdAt?.slice(5, 10) || `记录 ${index + 1}`)
    },
    yAxis: [
      { type: "value", name: "得分" },
      { type: "value", name: "正确率", max: 100 }
    ],
    series: [
      {
        name: "得分",
        type: "line",
        smooth: true,
        data: rows.map((record) => Number(record.totalScore || 0)),
        lineStyle: { color: "#2563eb", width: 3 },
        itemStyle: { color: "#2563eb" }
      },
      {
        name: "正确率",
        type: "line",
        smooth: true,
        yAxisIndex: 1,
        data: rows.map((record) => Number(record.accuracyRate || 0)),
        lineStyle: { color: "#10b981", width: 3 },
        itemStyle: { color: "#10b981" }
      }
    ]
  };
});

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

async function loadAnalytics(): Promise<void> {
  analyticsLoading.value = true;
  try {
    analytics.value = await getProfileAnalytics();
  } catch (requestError) {
    error.value = toErrorMessage(requestError, "加载学情画像失败");
  } finally {
    analyticsLoading.value = false;
  }
}

async function loadTopology(): Promise<void> {
  topologyLoading.value = true;
  try {
    topologyData.value = await getKnowledgeTopology();
  } catch (e) {
    console.error("加载知识拓扑失败", e);
  } finally {
    topologyLoading.value = false;
  }
}

const topologyOption = computed(() => {
  if (!topologyData.value || !topologyData.value.nodes || topologyData.value.nodes.length === 0)
    return {};

  return {
    tooltip: { trigger: "item", formatter: "{b}" },
    series: [
      {
        type: "graph",
        layout: "force",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        data: topologyData.value.nodes.map((n: any) => ({
          id: n.id,
          name: n.name,
          symbolSize: n.status === "weak" ? 40 : 25,
          itemStyle: { color: n.status === "weak" ? "#ef4444" : "#10b981" }
        })),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        links: topologyData.value.edges.map((e: any) => ({
          source: e.source,
          target: e.target,
          label: { show: true, formatter: e.type, fontSize: 10 }
        })),
        roam: true,
        label: { show: true, position: "right" },
        force: {
          repulsion: 300,
          edgeLength: 120
        },
        lineStyle: {
          color: "source",
          curveness: 0.2
        }
      }
    ]
  };
});

async function refreshProfile(): Promise<void> {
  await Promise.all([loadProfile(), loadAnalytics(), loadTopology()]);
}

onMounted(async () => {
  await refreshProfile();
});

function getRoleType(role: string): "default" | "error" | "warning" | "info" {
  if (role === "ADMIN") return "error";
  if (role === "TEACHER") return "warning";
  if (role === "STUDENT") return "info";
  return "default";
}

function getStatusType(status: string): "success" | "error" {
  return status === "ACTIVE" ? "success" : "error";
}

function realtimeTagType(state?: string): "success" | "warning" | "error" | "default" | "info" {
  if (state === "LIVE") return "success";
  if (state === "NO_ACTIVITY") return "warning";
  if (state === "UNAVAILABLE") return "error";
  return "default";
}

function realtimeStatusLabel(state?: string): string {
  if (state === "LIVE") return "实时可用";
  if (state === "NO_ACTIVITY") return "近窗无活动";
  if (state === "UNAVAILABLE") return "近时态不可用";
  return "状态未知";
}

function realtimeCountDisplay(value?: number | null): string {
  if (realtimeState.value.dataState === "NO_ACTIVITY") {
    return "0";
  }
  if (realtimeState.value.dataState === "UNAVAILABLE") {
    return "不可用";
  }
  return value != null ? String(value) : "--";
}

function realtimeDensityDisplay(value?: number | null): string {
  if (realtimeState.value.dataState === "NO_ACTIVITY") {
    return "0%";
  }
  if (realtimeState.value.dataState === "UNAVAILABLE") {
    return "不可用";
  }
  return value != null ? `${value}%` : "--";
}
</script>

<template>
  <div class="profile-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">学情画像</n-text>
          <n-text depth="3"
            >把近期作答、错题积压和 AI 练习完成情况整合为可读的个人学习支持画像。</n-text
          >
        </div>
        <n-button
          secondary
          type="primary"
          :loading="loading || analyticsLoading"
          @click="refreshProfile"
        >
          <template #icon><RefreshCw :size="14" /></template>
          刷新画像
        </n-button>
      </div>

      <n-alert v-if="error" type="error" :show-icon="true">{{ error }}</n-alert>

      <div class="summary-grid">
        <div class="summary-card panel glass-card">
          <div class="summary-icon primary"><TrendingUp :size="20" /></div>
          <div>
            <p class="summary-label">最近一次正确率</p>
            <h3 class="summary-value">{{ latestAccuracy }}%</h3>
            <p class="summary-hint">反映当下课堂即时诊断后的掌握状态</p>
          </div>
        </div>
        <div class="summary-card panel glass-card">
          <div class="summary-icon warning"><AlertTriangle :size="20" /></div>
          <div>
            <p class="summary-label">活跃错题数</p>
            <h3 class="summary-value">{{ activeWrongCount }}</h3>
            <p class="summary-hint">已掌握 {{ masteredWrongCount }} 道，仍需继续闭环复盘</p>
          </div>
        </div>
        <div class="summary-card panel glass-card">
          <div class="summary-icon success"><Sparkles :size="20" /></div>
          <div>
            <p class="summary-label">AI 练习完成率</p>
            <h3 class="summary-value">{{ aiCompletionRate }}%</h3>
            <p class="summary-hint">反映个性化练习被真正完成的比例</p>
          </div>
        </div>
        <div class="summary-card panel glass-card">
          <div class="summary-icon info"><UserIcon :size="20" /></div>
          <div>
            <p class="summary-label">近 7 天活跃学习日</p>
            <h3 class="summary-value">{{ activeDays7d }}</h3>
            <p class="summary-hint">画像会结合连续学习节奏与行为密度判断支持强度</p>
          </div>
        </div>
      </div>

      <div class="top-grid">
        <n-card :bordered="true" class="profile-card">
          <template #header>
            <n-space align="center" :size="8">
              <UserIcon :size="20" class="card-icon" />
              <n-text strong>基础信息</n-text>
            </n-space>
          </template>

          <n-spin :show="loading && !profile">
            <div v-if="!profile && !loading" style="padding: 40px; text-align: center">
              <n-text depth="3">暂无个人信息数据</n-text>
            </div>
            <n-descriptions
              v-else-if="profile"
              label-placement="left"
              bordered
              :column="1"
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
              <n-descriptions-item label="互动画像">
                {{ interactionProfile }}
              </n-descriptions-item>
              <n-descriptions-item label="最近活跃时间">
                {{ latestActivityLabel }}
              </n-descriptions-item>
            </n-descriptions>
          </n-spin>
        </n-card>

        <n-card :bordered="true" class="support-card" :class="supportStage.tone">
          <template #header>
            <n-space align="center" :size="8">
              <Sparkles :size="20" />
              <n-text strong>当前学习支持区间</n-text>
            </n-space>
          </template>

          <h3 class="support-title">{{ supportStage.label }}</h3>
          <p class="support-copy">{{ supportStage.description }}</p>
          <p class="support-zone">支持区间：{{ supportStage.supportZone }}</p>
          <ul class="support-list">
            <li>滚动正确率：{{ rollingAccuracy }}%</li>
            <li>AI 练习完成：{{ aiCompletionRate }}%</li>
            <li>Top 薄弱点：{{ topWeakPoints[0]?.knowledgePoint || "暂无明显薄弱点" }}</li>
            <li>教师建议数：{{ teacherSuggestionCount }}</li>
          </ul>
        </n-card>
      </div>

      <section class="panel glass-card realtime-panel">
        <div class="panel-head">
          <div>
            <h3 class="panel-title">近 10 分钟课堂态</h3>
            <p class="panel-note">这部分直接反映 Redis 聚合的近时态行为，不再只看历史累计统计。</p>
          </div>
          <n-tag :type="realtimeTagType(realtimeState.dataState)" :bordered="false">
            {{ realtimeStatusLabel(realtimeState.dataState) }}
          </n-tag>
        </div>

        <div class="realtime-metrics">
          <div class="rt-metric">
            <span class="rt-label">提问次数</span>
            <strong>{{ realtimeCountDisplay(realtimeState.recentChatQuestions) }}</strong>
          </div>
          <div class="rt-metric">
            <span class="rt-label">练习提交</span>
            <strong>{{ realtimeCountDisplay(realtimeState.recentExerciseSubmissions) }}</strong>
          </div>
          <div class="rt-metric">
            <span class="rt-label">AI 再练</span>
            <strong>{{ realtimeCountDisplay(realtimeState.recentAiInteractions) }}</strong>
          </div>
          <div class="rt-metric">
            <span class="rt-label">错误密度</span>
            <strong>{{ realtimeDensityDisplay(realtimeState.recentErrorDensity) }}</strong>
          </div>
        </div>

        <div class="realtime-grid">
          <div>
            <h4 class="realtime-title">即时信号</h4>
            <ul class="signal-list">
              <li v-for="signal in realtimeState.signals || []" :key="signal">{{ signal }}</li>
            </ul>
          </div>
          <div>
            <h4 class="realtime-title">即时热点知识点</h4>
            <div v-if="realtimeHotspots.length === 0" class="empty-block">
              近 10 分钟暂无新的热点知识点。
            </div>
            <div v-else class="realtime-hotspots">
              <div
                v-for="item in realtimeHotspots"
                :key="item.knowledgePoint"
                class="realtime-chip"
              >
                <span>{{ item.knowledgePoint }}</span>
                <strong>{{ item.eventCount }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="chart-grid">
        <section class="panel glass-card chart-panel">
          <div class="panel-head">
            <div>
              <h3 class="panel-title">近期作答走势</h3>
              <p class="panel-note">按最近 6 次课堂练习 / AI 再练观察得分与正确率变化。</p>
            </div>
          </div>
          <n-spin :show="analyticsLoading">
            <div v-if="recentPerformance.length === 0" class="empty-block">暂无练习记录。</div>
            <v-chart v-else class="trend-chart" :option="recordsLineOption" autoresize />
          </n-spin>
        </section>

        <section class="panel glass-card chart-panel" style="grid-column: span 1">
          <div class="panel-head">
            <div>
              <h3 class="panel-title">知识点拓扑图谱 (Knowledge Topology)</h3>
              <p class="panel-note">基于最近错题关联推理生成的前置与后置知识节点依赖关系。</p>
            </div>
          </div>
          <n-spin :show="topologyLoading">
            <div
              v-if="!topologyData || !topologyData.nodes || topologyData.nodes.length === 0"
              class="empty-block"
            >
              暂无足够的错误数据生成拓扑关联。
            </div>
            <v-chart
              v-else
              class="trend-chart"
              :option="topologyOption"
              autoresize
              style="min-height: 300px"
            />
          </n-spin>
        </section>
      </div>

      <n-card :bordered="true" class="warning-card animate-pop">
        <template #header>
          <n-space align="center" :size="8">
            <AlertTriangle :size="20" class="warning-icon text-danger" />
            <n-text strong style="color: var(--color-danger); font-size: 1.1rem">
              AI 诊断：核心薄弱知识域
            </n-text>
          </n-space>
        </template>

        <p class="warning-desc">根据近期作答与错题轨迹，以下知识点最值得优先复盘。</p>

        <n-spin :show="analyticsLoading">
          <div v-if="topWeakPoints.length === 0" class="weak-empty">
            <n-text depth="3">暂无薄弱知识点，继续保持练习。</n-text>
          </div>
          <div v-else class="weak-points-list">
            <div
              v-for="(item, index) in topWeakPoints"
              :key="`${item.knowledgePoint}-${index}`"
              class="weak-point-item"
            >
              <div class="wp-header">
                <n-text strong class="wp-name">{{ item.knowledgePoint || "未命名知识点" }}</n-text>
                <span class="wp-badge">失误率 {{ Number(item.errorRate || 0).toFixed(1) }}%</span>
              </div>
              <div class="wp-bar-bg">
                <div
                  class="wp-bar-fill"
                  :style="{ width: Number(item.errorRate || 0) + '%' }"
                ></div>
              </div>
              <n-text depth="3" class="wp-footnote">累计错误 {{ item.wrongCount || 0 }} 次</n-text>
            </div>
          </div>
        </n-spin>
      </n-card>

      <div class="insight-grid">
        <section class="panel glass-card insight-panel">
          <div class="panel-head">
            <div>
              <h3 class="panel-title">行为序列信号</h3>
              <p class="panel-note">系统把近 7 天问答、练习、AI 再练和教师干预整合成可读信号。</p>
            </div>
          </div>
          <div v-if="behaviorSignals.length === 0" class="empty-block">
            暂无行为信号，先完成一轮课堂互动或练习。
          </div>
          <ul v-else class="signal-list">
            <li v-for="signal in behaviorSignals" :key="signal">{{ signal }}</li>
          </ul>
        </section>

        <section class="panel glass-card insight-panel">
          <div class="panel-head">
            <div>
              <h3 class="panel-title">下一步支持建议</h3>
              <p class="panel-note">把画像结果转成下一轮最值得执行的具体动作。</p>
            </div>
          </div>
          <div v-if="recommendedActions.length === 0" class="empty-block">暂无建议。</div>
          <ol v-else class="signal-list ordered">
            <li v-for="action in recommendedActions" :key="action">{{ action }}</li>
          </ol>
        </section>
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
}

.summary-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  height: 100%;
}

.summary-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.summary-icon.primary {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
}

.summary-icon.warning {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.summary-icon.success {
  background: linear-gradient(135deg, #10b981, #047857);
}

.summary-icon.info {
  background: linear-gradient(135deg, #06b6d4, #0f766e);
}

.summary-label {
  margin: 0 0 6px;
  color: var(--color-text-muted);
}

.summary-value {
  margin: 0;
  font-size: 1.85rem;
  font-family: var(--font-code);
}

.summary-hint {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.top-grid,
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.realtime-panel {
  display: grid;
  gap: 18px;
}

.realtime-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.rt-metric {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(59, 130, 246, 0.14);
}

.rt-label {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  margin-bottom: 8px;
}

.rt-metric strong {
  font-size: 1.25rem;
}

.realtime-grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 18px;
}

.realtime-title {
  margin: 0 0 10px;
  font-size: 0.98rem;
}

.realtime-hotspots {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.realtime-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.12);
  color: #0f172a;
  font-weight: 600;
}

.profile-card {
  border-radius: 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.profile-card :deep(.n-card__content) {
  flex: 1;
}

.card-icon {
  color: var(--color-primary);
}

.support-card {
  border-radius: 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.support-card :deep(.n-card__content) {
  flex: 1;
}

.support-card.danger {
  background: linear-gradient(135deg, rgba(254, 226, 226, 0.7), rgba(255, 255, 255, 0.9));
}

.support-card.warning {
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.7), rgba(255, 255, 255, 0.9));
}

.support-card.success {
  background: linear-gradient(135deg, rgba(209, 250, 229, 0.7), rgba(255, 255, 255, 0.9));
}

.support-title {
  margin: 0 0 8px;
  font-size: 1.2rem;
}

.support-copy {
  margin: 0;
  line-height: 1.6;
  color: var(--color-text-main);
}

.support-zone {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  font-size: 0.92rem;
}

.support-list {
  margin: 16px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}

.chart-panel,
.insight-panel {
  min-height: 360px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-head {
  margin-bottom: 14px;
}

.panel-title {
  margin: 0;
  font-size: 1.05rem;
}

.panel-note {
  margin: 6px 0 0;
  color: var(--color-text-muted);
}

.trend-chart {
  width: 100%;
  height: 280px;
}

.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 280px;
  color: var(--color-text-muted);
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

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.insight-panel {
  min-height: 220px;
}

.signal-list {
  margin: 0;
  padding-left: 20px;
  color: var(--color-text-main);
  line-height: 1.85;
}

.signal-list.ordered {
  list-style: decimal;
}

.weak-point-item {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(239, 68, 68, 0.15);
  padding: 16px;
  border-radius: 8px;
}

.weak-empty {
  padding: 20px 0;
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

.wp-footnote {
  display: block;
  margin-top: 10px;
  font-size: 0.8rem;
}

@media (max-width: 1024px) {
  .top-grid,
  .chart-grid,
  .realtime-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>
