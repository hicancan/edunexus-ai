<script setup lang="ts">
import { computed, onMounted, type Component } from "vue";
import { NButton, NAlert } from "naive-ui";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import VChart from "vue-echarts";
import {
  RefreshCw,
  Activity,
  Users,
  FileText,
  Database,
  MessageSquare,
  ShieldCheck
} from "lucide-vue-next";
import { useAdminStore } from "../../features/admin/model/admin";
import type { DashboardMetricsVO } from "../../services/contracts";

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent]);

const adminStore = useAdminStore();
type DashboardFlowMetric = NonNullable<DashboardMetricsVO["flowLinkage"]>[number];
type DashboardExperimentMetric = NonNullable<DashboardMetricsVO["experimentComparisons"]>[number];
type DashboardResponseMetric = NonNullable<DashboardMetricsVO["responseBenchmarks"]>[number];

const METRIC_LABELS: Record<string, { label: string; icon: Component; color: string }> = {
  totalUsers: { label: "活跃用户数", icon: Users, color: "#2080f0" },
  totalStudents: { label: "活跃学生数", icon: Users, color: "#18a058" },
  totalTeachers: { label: "活跃教师数", icon: Users, color: "#f2c97d" },
  totalAdmins: { label: "活跃管理员数", icon: Users, color: "#d03050" },
  totalChatMessages: { label: "问答消息总量", icon: MessageSquare, color: "#2563eb" },
  totalExerciseRecords: { label: "课堂练习记录", icon: FileText, color: "#f59e0b" },
  totalDocuments: { label: "知识文档", icon: Database, color: "#10b981" },
  totalAiQuestionSessions: { label: "AI 出题会话", icon: Activity, color: "#7c3aed" }
};

const metricEntries = computed(() => {
  if (!adminStore.metrics) {
    return [];
  }
  return Object.entries(METRIC_LABELS).map(([key, config]) => ({
    key,
    value: Number((adminStore.metrics as Record<string, number>)[key] || 0),
    ...config
  }));
});

const executionOption = computed(() => {
  const rows = adminStore.metrics?.executionDistribution || [];
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    grid: { left: 24, right: 48, top: 24, bottom: 24, containLabel: true },
    xAxis: { type: "value", name: "任务量" },
    yAxis: { type: "category", data: rows.map((row) => row.label || row.lane) },
    series: [
      {
        name: "任务量",
        type: "bar",
        barWidth: 18,
        data: rows.map((row) => Number(row.taskCount || 0)),
        itemStyle: {
          borderRadius: [0, 999, 999, 0],
          color: "#2563eb"
        }
      }
    ]
  };
});

const benchmarkOption = computed(() => {
  const rows = adminStore.metrics?.responseBenchmarks || [];
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { data: ["平均时延", "P95 时延"], bottom: 0 },
    grid: { left: 60, right: 24, top: 36, bottom: 56, containLabel: true },
    xAxis: { type: "category", data: rows.map((row) => row.scene || "未知场景") },
    yAxis: { type: "value", name: "ms", nameTextStyle: { padding: [0, 24, 0, 0] } },
    series: [
      {
        name: "平均时延",
        type: "bar",
        data: rows.map((row) => row.avgLatencyMs ?? null),
        itemStyle: { borderRadius: 10, color: "#0ea5e9" }
      },
      {
        name: "P95 时延",
        type: "bar",
        data: rows.map((row) => row.p95LatencyMs ?? null),
        itemStyle: { borderRadius: 10, color: "#f97316" }
      }
    ]
  };
});

const strategyOption = computed(() => {
  const rows = adminStore.metrics?.strategyComparison || [];
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { data: ["平均时延", "完成率", "隐私留存"], bottom: 0 },
    grid: { left: 24, right: 24, top: 16, bottom: 56, containLabel: true },
    xAxis: { type: "category", data: rows.map((row) => row.strategy || "未知策略") },
    yAxis: { type: "value" },
    series: [
      {
        name: "平均时延",
        type: "bar",
        data: rows.map((row) =>
          hasMetricValue(row.avgLatencyMs) ? Number(row.avgLatencyMs) : null
        ),
        itemStyle: { color: "#2563eb", borderRadius: 10 }
      },
      {
        name: "完成率",
        type: "bar",
        data: rows.map((row) =>
          hasMetricValue(row.completionRate) ? Number(row.completionRate) : null
        ),
        itemStyle: { color: "#10b981", borderRadius: 10 }
      },
      {
        name: "隐私留存",
        type: "bar",
        data: rows.map((row) =>
          hasMetricValue(row.privacyRetentionRate) ? Number(row.privacyRetentionRate) : null
        ),
        itemStyle: { color: "#f59e0b", borderRadius: 10 }
      }
    ]
  };
});

const responseBenchmarks = computed<DashboardResponseMetric[]>(
  () => adminStore.metrics?.responseBenchmarks || []
);

const governanceItems = computed(() => {
  const governance = adminStore.metrics?.governanceSummary;
  if (!governance) {
    return [];
  }
  return [
    {
      label: "审计追踪覆盖率",
      value: governance.traceCoverageRate,
      sampleCount: Number(governance.traceCoverageSamples || governance.auditedActions || 0),
      tone: "primary",
      hint: `${governance.auditedActions || 0} 条关键操作已进入审计链路`
    },
    {
      label: "教师建议采纳率",
      value: governance.teacherAdoptionRate,
      sampleCount: Number(governance.teacherAdoptionSamples || 0),
      tone: "warning",
      hint: `${governance.teacherAdoptedCount || 0} / ${governance.teacherAdoptionSamples || 0} 次建议查看在 10 分钟内转化为正式下发`
    },
    {
      label: "本地/近端留存率",
      value: governance.localRetentionRate,
      sampleCount: Number(governance.retentionTaskSamples || 0),
      tone: "success",
      hint: `基于 ${governance.retentionTaskSamples || 0} 条真实执行样本，按 EDGE / HUMAN_IN_LOOP 直接留存、HYBRID 协同折算`
    },
    {
      label: "敏感数据外发比例",
      value: governance.sensitiveOutboundRate,
      sampleCount: Number(governance.retentionTaskSamples || 0),
      tone: "danger",
      hint: `基于 ${governance.retentionTaskSamples || 0} 条真实执行样本折算的高敏字段外发压力，越低越好`
    },
    {
      label: "课堂检索命中率",
      value: governance.retrievalHitRate,
      sampleCount: Number(governance.retrievalHitSamples || 0),
      tone: "primary",
      hint: `${governance.retrievalHitCount || 0} / ${governance.retrievalHitSamples || 0} 次课堂问答命中证据链`
    },
    {
      label: "建议执行转化率",
      value: governance.suggestionExecutionRate,
      sampleCount: Number(governance.suggestionExecutionSamples || 0),
      tone: "success",
      hint: `${governance.suggestionExecutedCount || 0} / ${governance.suggestionDispatchCount || 0} 条建议在 7 天内触发后续练习`
    }
  ];
});

const outcomes = computed(() => adminStore.metrics?.interventionOutcomes || []);
const flowLinkage = computed<DashboardFlowMetric[]>(() => adminStore.metrics?.flowLinkage || []);
const experimentGroups = computed(() => {
  const groups = new Map<string, DashboardExperimentMetric[]>();
  for (const row of adminStore.metrics?.experimentComparisons || []) {
    const category = row.category || "未分类";
    const bucket = groups.get(category);
    if (bucket) {
      bucket.push(row);
      continue;
    }
    groups.set(category, [row]);
  }
  return Array.from(groups.entries()).map(([category, rows]) => ({ category, rows }));
});

function formatMetricValue(value?: number | null, unit?: string): string {
  if (!hasMetricValue(value)) {
    return "待采样";
  }
  const numeric = Number(value);
  return `${Number(numeric.toFixed(2))}${unit || ""}`;
}

function deltaLabel(row: DashboardExperimentMetric): string {
  if (row.dataState === "NO_SAMPLES" || !hasMetricValue(row.delta)) {
    return "待采样";
  }
  const delta = Number(row.delta);
  if (delta === 0) {
    return "持平";
  }
  return `${delta > 0 ? "改善" : "回退"} ${formatMetricValue(Math.abs(delta), row.unit)}`;
}

function deltaTone(row: DashboardExperimentMetric): string {
  if (row.dataState === "NO_SAMPLES" || !hasMetricValue(row.delta)) {
    return "neutral";
  }
  return Number(row.delta || 0) >= 0 ? "positive" : "negative";
}

function hasMetricValue(value?: number | null): boolean {
  return value !== null && value !== undefined && Number.isFinite(Number(value));
}

function metricBarWidth(value?: number | null): string {
  return `${hasMetricValue(value) ? Math.min(100, Number(value)) : 0}%`;
}

function sampleHint(hint: string, sampleCount?: number): string {
  return Number(sampleCount || 0) > 0 ? hint : `暂无有效样本，${hint}`;
}

async function loadMetrics(): Promise<void> {
  await adminStore.loadMetrics();
}

onMounted(loadMetrics);
</script>

<template>
  <div class="admin-dashboard">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">平台数据看板</h1>
          <p class="workspace-subtitle">
            把运行计数、协同调度、实验对比和治理指标汇总到一张可验证看板。
          </p>
        </div>
        <n-button
          type="primary"
          secondary
          class="glass-pill"
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

      <div class="metric-grid">
        <div v-for="item in metricEntries" :key="item.key" class="metric-card panel glass-card">
          <div class="metric-icon" :style="{ color: item.color }">
            <component :is="item.icon" :size="20" />
          </div>
          <div>
            <p class="metric-label">{{ item.label }}</p>
            <h3 class="metric-value">{{ item.value }}</h3>
          </div>
        </div>
      </div>

      <div class="chart-grid">
        <section class="panel glass-card chart-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title">云边端协同执行与调度可视化</h2>
              <p class="panel-note">
                按任务类型拆分当前平台的执行落点，核对课堂链路是否真的做到了云边分工。
              </p>
            </div>
          </div>
          <v-chart class="chart-canvas" :option="executionOption" autoresize />
          <div class="lane-grid">
            <div
              v-for="lane in adminStore.metrics?.executionDistribution || []"
              :key="lane.lane"
              class="lane-card"
            >
              <p class="lane-label">{{ lane.label }}</p>
              <strong class="lane-value">{{
                lane.dataState === "NO_SAMPLES" ? "待采样" : `${lane.share}%`
              }}</strong>
              <p class="lane-sample">
                {{
                  lane.dataState === "NO_SAMPLES"
                    ? "暂无有效执行样本"
                    : `真实样本 ${lane.sampleCount} 条`
                }}
              </p>
              <p class="lane-copy">{{ lane.description }}</p>
            </div>
          </div>
        </section>

        <section class="panel glass-card chart-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title">系统响应基准</h2>
              <p class="panel-note">
                只展示真实采到的关键场景时延；没有样本的链路不再用演示值补齐。
              </p>
            </div>
          </div>
          <v-chart class="chart-canvas" :option="benchmarkOption" autoresize />
          <div class="benchmark-list">
            <div v-for="row in responseBenchmarks" :key="row.scene" class="benchmark-item">
              <strong>{{ row.scene }}</strong>
              <span>{{
                row.dataState === "NO_SAMPLES" ? "暂无有效样本" : `样本 ${row.sampleCount} 条`
              }}</span>
            </div>
          </div>
        </section>
      </div>

      <section class="panel glass-card chart-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title">部署策略效果对比</h2>
            <p class="panel-note">
              只展示真实采样到的策略效果；没有跑过的策略明确标为待采样，不再反推演示值。
            </p>
          </div>
        </div>
        <v-chart class="chart-canvas wide" :option="strategyOption" autoresize />
        <div class="strategy-grid">
          <div
            v-for="row in adminStore.metrics?.strategyComparison || []"
            :key="row.strategy"
            class="strategy-card"
          >
            <div class="strategy-top">
              <strong>{{ row.strategy }}</strong>
              <span class="strategy-latency">{{ formatMetricValue(row.avgLatencyMs, " ms") }}</span>
            </div>
            <p class="strategy-copy">{{ row.basis }}</p>
            <div class="strategy-meta">
              <span>{{
                row.dataState === "NO_SAMPLES" ? "待采样" : `样本 ${row.sampleCount} 条`
              }}</span>
              <span>完成率 {{ formatMetricValue(row.completionRate, "%") }}</span>
              <span>隐私留存 {{ formatMetricValue(row.privacyRetentionRate, "%") }}</span>
              <span>单位成本 {{ formatMetricValue(row.unitCostIndex) }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="panel glass-card chart-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title">教学支持全流程联动效果</h2>
            <p class="panel-note">
              以“课前准备 → 课中诊断 → 个性化再练 → 教师在环 →
              课后闭环”展示教学支持链路是否真正跑通。
            </p>
          </div>
        </div>
        <div class="flow-grid">
          <article v-for="(row, index) in flowLinkage" :key="row.stage || index" class="flow-card">
            <div class="flow-step">0{{ index + 1 }}</div>
            <div class="flow-body">
              <div class="flow-top">
                <strong>{{ row.label }}</strong>
                <span class="flow-rate">{{ formatMetricValue(row.rate, "%") }}</span>
              </div>
              <p class="flow-count">
                {{
                  row.dataState === "NO_SAMPLES"
                    ? "暂无有效样本进入该阶段统计"
                    : `${row.count} 个关键事件纳入该阶段统计`
                }}
              </p>
              <div class="governance-bar compact">
                <span
                  class="governance-fill primary"
                  :style="{ width: metricBarWidth(row.rate) }"
                ></span>
              </div>
              <p class="flow-copy">{{ row.insight }}</p>
            </div>
          </article>
        </div>
      </section>

      <div class="governance-grid">
        <section class="panel glass-card chart-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title">治理与教师在环摘要</h2>
              <p class="panel-note">把 trace 覆盖、教师采纳和本地留存统一到治理视角下解释。</p>
            </div>
            <span class="panel-badge neutral">
              <ShieldCheck :size="12" />
              治理面
            </span>
          </div>
          <div class="governance-list">
            <div v-for="item in governanceItems" :key="item.label" class="governance-item">
              <div class="governance-top">
                <span>{{ item.label }}</span>
                <strong>{{ formatMetricValue(item.value, "%") }}</strong>
              </div>
              <div class="governance-bar">
                <span
                  class="governance-fill"
                  :class="item.tone"
                  :style="{ width: metricBarWidth(item.value) }"
                ></span>
              </div>
              <p class="governance-copy">{{ sampleHint(item.hint, item.sampleCount) }}</p>
            </div>
          </div>
        </section>

        <section class="panel glass-card chart-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title">教学支持闭环结果</h2>
              <p class="panel-note">展示知识接入、AI 练习、错题消减和教师建议覆盖等闭环结果。</p>
            </div>
          </div>
          <div class="outcome-list">
            <div v-for="item in outcomes" :key="item.label" class="outcome-card">
              <p class="outcome-label">{{ item.label }}</p>
              <div class="outcome-top">
                <strong class="outcome-value">{{
                  formatMetricValue(item.value, item.unit)
                }}</strong>
                <span class="outcome-target">目标 {{ item.target }}{{ item.unit }}</span>
              </div>
              <div class="governance-bar">
                <span
                  class="governance-fill success"
                  :style="{ width: metricBarWidth(item.value) }"
                ></span>
              </div>
              <p class="outcome-copy">
                {{
                  item.dataState === "NO_SAMPLES" ? `暂无有效样本，${item.insight}` : item.insight
                }}
              </p>
            </div>
          </div>
        </section>
      </div>

      <section class="panel glass-card chart-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title">系统响应与效果对比展示</h2>
            <p class="panel-note">
              对比区仅展示真实采样结果；基线策略未采到时明确显示待采样，而不是补默认值或推演值。
            </p>
          </div>
        </div>
        <div class="experiment-group-grid">
          <section v-for="group in experimentGroups" :key="group.category" class="experiment-group">
            <div class="experiment-group-head">
              <h3>{{ group.category }}</h3>
              <span>{{ group.rows.length }} 项指标</span>
            </div>
            <div class="experiment-list">
              <article
                v-for="row in group.rows"
                :key="`${group.category}-${row.metric}`"
                class="experiment-row"
              >
                <div class="experiment-copy">
                  <p class="experiment-metric">{{ row.metric }}</p>
                  <p class="experiment-evidence">{{ row.evidence }}</p>
                </div>
                <div class="experiment-values">
                  <span class="experiment-sample">{{
                    row.dataState === "NO_SAMPLES" ? "待采样" : `当前样本 ${row.sampleCount} 条`
                  }}</span>
                  <span class="experiment-baseline"
                    >基线策略 {{ formatMetricValue(row.baselineValue, row.unit) }}</span
                  >
                  <span class="experiment-current"
                    >当前策略 {{ formatMetricValue(row.currentValue, row.unit) }}</span
                  >
                  <span class="comparison-chip" :class="deltaTone(row)">{{ deltaLabel(row) }}</span>
                </div>
              </article>
            </div>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}

.metric-card {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-top: 0 !important;
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.78);
}

.metric-label {
  margin: 0 0 6px;
  color: var(--color-text-muted);
}

.metric-value {
  margin: 0;
  font-size: 1.8rem;
  font-family: var(--font-code);
}

.chart-grid,
.governance-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.chart-panel {
  min-height: 420px;
  height: 100%;
  margin-top: 0 !important;
  display: flex;
  flex-direction: column;
}

.chart-panel > :last-child {
  flex: 1;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
  font-size: 0.76rem;
  font-weight: 700;
}

.panel-badge.neutral {
  background: rgba(245, 158, 11, 0.12);
  color: #92400e;
}

.benchmark-list {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  align-content: start;
}

.benchmark-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text-muted);
}

.chart-canvas {
  width: 100%;
  height: 260px;
}

.chart-canvas.wide {
  height: 320px;
}

.lane-grid,
.strategy-grid,
.outcome-list,
.governance-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  align-content: start;
}

.lane-grid,
.strategy-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.flow-grid,
.experiment-group-grid {
  display: grid;
  gap: 14px;
  align-content: start;
}

.flow-card,
.experiment-group {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.58);
}

.flow-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.flow-card {
  display: flex;
  gap: 14px;
  padding: 16px;
}

.flow-step {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-code);
  font-weight: 700;
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.12);
}

.flow-body {
  min-width: 0;
}

.flow-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.flow-rate {
  font-family: var(--font-code);
  color: #1d4ed8;
}

.flow-count {
  margin: 8px 0 0;
  color: var(--color-text-main);
  font-size: 0.92rem;
}

.flow-copy {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  line-height: 1.55;
}

.lane-card,
.strategy-card,
.outcome-card,
.governance-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.14);
  word-break: break-all;
}

.lane-label,
.outcome-label {
  margin: 0 0 8px;
  color: var(--color-text-muted);
}

.lane-value {
  font-size: 1.4rem;
  font-family: var(--font-code);
}

.lane-sample {
  margin: 8px 0 0;
  color: var(--color-text-main);
  font-size: 0.85rem;
}

.lane-copy,
.strategy-copy,
.outcome-copy,
.governance-copy {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.strategy-top,
.outcome-top,
.governance-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.strategy-latency,
.outcome-target {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.strategy-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 10px;
  color: var(--color-text-main);
  font-size: 0.85rem;
}

.governance-bar {
  width: 100%;
  height: 10px;
  margin-top: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.16);
}

.governance-bar.compact {
  margin-top: 12px;
}

.governance-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.governance-fill.primary {
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}

.governance-fill.warning {
  background: linear-gradient(90deg, #fbbf24, #f59e0b);
}

.governance-fill.success {
  background: linear-gradient(90deg, #34d399, #10b981);
}

.governance-fill.danger {
  background: linear-gradient(90deg, #f87171, #dc2626);
}

.outcome-value {
  font-size: 1.5rem;
  font-family: var(--font-code);
}

.experiment-group-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.experiment-group {
  padding: 16px;
}

.experiment-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.experiment-group-head h3 {
  margin: 0;
  font-size: 1rem;
}

.experiment-group-head span {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.experiment-list {
  display: grid;
  gap: 10px;
}

.experiment-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.66);
}

.experiment-copy {
  min-width: 0;
}

.experiment-metric {
  margin: 0;
  font-weight: 700;
}

.experiment-evidence {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.experiment-values {
  min-width: 132px;
  display: grid;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.experiment-baseline,
.experiment-sample,
.experiment-current {
  font-size: 0.85rem;
  color: var(--color-text-main);
}

.experiment-sample {
  color: var(--color-text-muted);
}

.comparison-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
}

.comparison-chip.positive {
  color: #047857;
  background: rgba(16, 185, 129, 0.14);
}

.comparison-chip.negative {
  color: #b91c1c;
  background: rgba(248, 113, 113, 0.16);
}

.comparison-chip.neutral {
  color: #475569;
  background: rgba(148, 163, 184, 0.16);
}

@media (max-width: 1024px) {
  .chart-grid,
  .governance-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .panel-head {
    flex-direction: column;
  }

  .experiment-row,
  .flow-card {
    flex-direction: column;
  }

  .experiment-values {
    justify-items: start;
    text-align: left;
  }
}
</style>
