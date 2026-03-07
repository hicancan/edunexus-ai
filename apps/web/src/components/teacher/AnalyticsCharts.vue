<script setup lang="ts">
import { computed } from "vue";
import { NEmpty, NButton } from "naive-ui";
import { Target, TrendingUp, BrainCircuit, PenTool, CheckCircle, Award } from "lucide-vue-next";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { RadarChart } from "echarts/charts";
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from "echarts/components";
import VChart from "vue-echarts";
import { useRouter } from "vue-router";
import { useAnalyticsStore } from "../../features/teacher-workspace/model/analytics";
import type { StudentAttributionVO, TeacherStudentVO } from "../../services/contracts";

use([CanvasRenderer, RadarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent]);

const props = defineProps<{
  selectedStudent: TeacherStudentVO | null;
  currentAttribution: StudentAttributionVO | null;
}>();

const router = useRouter();
const analyticsStore = useAnalyticsStore();
const accuracyRate = computed(() => {
  const totalQuestions = analyticsStore.analytics?.totalQuestions ?? 0;
  const correctCount = analyticsStore.analytics?.correctCount ?? 0;
  if (totalQuestions <= 0) return 0;
  return (correctCount / totalQuestions) * 100;
});

const radarOption = computed(() => {
  const points = analyticsStore.analytics?.topWeakPoints || [];
  if (points.length === 0) return {};

  const maxVal = Math.max(...points.map((p) => p.wrongCount || 0), 5) + 2;
  return {
    tooltip: {
      trigger: "item",
      padding: [12, 16],
      backgroundColor: "rgba(255,255,255,0.9)",
      borderColor: "rgba(92, 101, 246, 0.3)",
      textStyle: { color: "#1e293b" },
      borderRadius: 12
    },
    radar: {
      indicator: points.map((p) => ({ name: p.knowledgePoint, max: maxVal })),
      shape: "circle",
      splitNumber: 4,
      axisName: {
        color: "#475569",
        fontSize: 13,
        fontFamily: "Outfit, sans-serif",
        fontWeight: 600
      },
      splitLine: {
        lineStyle: {
          color: [
            "rgba(92, 101, 246, 0.1)",
            "rgba(92, 101, 246, 0.2)",
            "rgba(92, 101, 246, 0.3)",
            "rgba(92, 101, 246, 0.5)"
          ]
        }
      },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: "rgba(92, 101, 246, 0.15)" } }
    },
    series: [
      {
        name: "薄弱知识点",
        type: "radar",
        data: [
          {
            value: points.map((p) => p.wrongCount),
            name: "错误次数",
            symbol: "circle",
            symbolSize: 8,
            itemStyle: { color: "#ef4444", borderColor: "#fff", borderWidth: 2 },
            areaStyle: { color: "rgba(239, 68, 68, 0.25)" },
            lineStyle: { width: 3, color: "#ef4444" }
          }
        ]
      }
    ]
  };
});
</script>

<template>
  <div class="analytics-dashboard">
    <div class="metrics-grid">
      <div class="metric-glass target-glass animate-pop">
        <Target :size="28" class="metric-icon" />
        <div class="metric-data">
          <span class="metric-label">学生</span>
          <span class="metric-value str-val">{{ analyticsStore.analytics!.username }}</span>
        </div>
      </div>
      <div class="metric-glass animate-pop">
        <PenTool :size="28" class="metric-icon info" />
        <div class="metric-data">
          <span class="metric-label">练习次数</span>
          <span class="metric-value"
            >{{ analyticsStore.analytics!.totalExercises }} <span class="unit">次</span></span
          >
        </div>
      </div>
      <div class="metric-glass animate-pop">
        <BrainCircuit :size="28" class="metric-icon warning" />
        <div class="metric-data">
          <span class="metric-label">总题数</span>
          <span class="metric-value"
            >{{ analyticsStore.analytics!.totalQuestions }} <span class="unit">道</span></span
          >
        </div>
      </div>
      <div class="metric-glass animate-pop">
        <CheckCircle :size="28" class="metric-icon success" />
        <div class="metric-data">
          <span class="metric-label">正确数</span>
          <span class="metric-value"
            >{{ analyticsStore.analytics!.correctCount }} <span class="unit">道</span></span
          >
        </div>
      </div>
      <div class="metric-glass highlight-glass animate-pop">
        <Award :size="28" class="metric-icon highlight" />
        <div class="metric-data">
          <span class="metric-label">题目正确率</span>
          <span class="metric-value highlight-val"
            >{{ accuracyRate.toFixed(1) }} <span class="unit">%</span></span
          >
        </div>
      </div>
      <div class="metric-glass animate-pop">
        <Award :size="28" class="metric-icon highlight" />
        <div class="metric-data">
          <span class="metric-label">平均得分</span>
          <span class="metric-value"
            >{{ Number(analyticsStore.analytics!.averageScore ?? 0).toFixed(1) }}
            <span class="unit">分</span></span
          >
        </div>
      </div>
    </div>

    <div class="visual-domain-grid">
      <div class="panel glass-card radar-panel">
        <div class="panel-head">
          <h3 class="panel-title">
            薄弱知识点雷达
            <TrendingUp :size="18" style="color: var(--color-danger); margin-left: 8px" />
          </h3>
          <p class="panel-note">根据错题频次识别当前薄弱点</p>
        </div>
        <div class="echart-container">
          <n-empty
            v-if="!analyticsStore.analytics?.topWeakPoints?.length"
            description="暂无明显薄弱知识点"
          />
          <v-chart v-else class="radar-chart" :option="radarOption" autoresize />
        </div>
      </div>

      <div class="panel glass-card wrong-book-panel">
        <div class="panel-head">
          <h3 class="panel-title">错题本待处理</h3>
          <p class="panel-note">当前仍未掌握的错题数量</p>
        </div>
        <div class="wrong-book-dashboard">
          <div class="pulse-ring">
            <span class="huge-number">{{ analyticsStore.analytics!.wrongBookCount }}</span>
            <span class="huge-unit">未掌握题目</span>
          </div>
          <p class="dashboard-tip">建议结合教师建议页或课堂讲解继续跟进。</p>
        </div>
      </div>
    </div>

    <div class="panel glass-card ai-attribution-panel">
      <div class="panel-head">
        <h3
          class="panel-title"
          style="color: var(--color-danger); display: flex; align-items: center"
        >
          <BrainCircuit :size="18" style="margin-right: 8px" />
          错因归因分析
        </h3>
      </div>
      <div v-if="currentAttribution" class="attribution-content">
        <div class="attribution-header">
          <div class="attribution-badge">
            <span class="badge-label">重点知识点</span>
            <span class="badge-value">{{ currentAttribution.knowledgePoint || "未识别" }}</span>
          </div>
          <div class="attribution-stats">
            <Target :size="16" class="stats-icon" />
            <span class="stats-text">累计影响 {{ currentAttribution.impactCount || 0 }} 次</span>
          </div>
        </div>
        <p class="attribution-text">{{ currentAttribution.summary || "暂无归因摘要" }}</p>
        <div v-if="currentAttribution.examples?.length" class="examples-list">
          <div v-for="(example, i) in currentAttribution.examples" :key="i" class="example-item">
            <div class="example-head">
              <span>样本 {{ i + 1 }}</span>
              <span>错误 {{ example.wrongCount || 0 }} 次</span>
            </div>
            <p class="example-content">{{ example.content || "无样本内容" }}</p>
          </div>
        </div>
        <div class="attribution-actions">
          <n-button
            type="primary"
            class="animate-pop"
            color="#ef4444"
            :disabled="!selectedStudent"
            @click="router.push('/teacher/suggestions')"
          >
            前往教师建议
          </n-button>
        </div>
      </div>
      <n-empty v-else description="该学生暂无可用错因归因数据" style="padding: 20px 0" />
    </div>
  </div>
</template>

<style scoped>
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  margin-top: 8px;
}

.metric-glass {
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(12px);
  border: 1px solid var(--color-border-glass);
  border-radius: var(--radius-lg);
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: var(--transition-smooth);
}

.metric-glass:hover {
  background: rgba(255, 255, 255, 0.8);
  box-shadow: var(--shadow-float);
  transform: translateY(-4px);
}

.target-glass {
  background: linear-gradient(135deg, rgba(92, 101, 246, 0.1), rgba(255, 255, 255, 0.6));
  border-bottom: 3px solid var(--color-primary);
}
.highlight-glass {
  background: linear-gradient(135deg, rgba(32, 128, 240, 0.1), rgba(255, 255, 255, 0.6));
  border-bottom: 3px solid #3b82f6;
}

.metric-icon {
  padding: 12px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  color: var(--color-primary);
}
.metric-icon.info {
  color: #3b82f6;
}
.metric-icon.warning {
  color: #f59e0b;
}
.metric-icon.success {
  color: #10b981;
}
.metric-icon.highlight {
  color: #2563eb;
  background: #eff6ff;
}

.metric-data {
  display: flex;
  flex-direction: column;
}
.metric-label {
  font-size: 0.82rem;
  color: var(--color-text-muted);
  font-weight: 600;
  margin-bottom: 2px;
}
.metric-value {
  font-size: 1.8rem;
  font-weight: 800;
  font-family: var(--font-code);
  color: var(--color-text-main);
  line-height: 1;
}
.str-val {
  font-family: var(--font-title);
  font-size: 1.5rem;
}
.highlight-val {
  color: #2563eb;
}
.unit {
  font-size: 0.9rem;
  color: var(--color-text-muted);
  font-family: var(--font-body);
  font-weight: 600;
  margin-left: 2px;
}

.visual-domain-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-4);
}
.radar-panel {
  display: flex;
  flex-direction: column;
}

.echart-container {
  flex: 1;
  min-height: 400px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-md);
  margin-top: var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.radar-chart {
  width: 100%;
  height: 400px;
}

.wrong-book-panel {
  display: flex;
  flex-direction: column;
}
.wrong-book-dashboard {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-top: var(--space-4);
  padding: var(--space-4);
  background: linear-gradient(180deg, rgba(245, 158, 11, 0.05) 0%, rgba(245, 158, 11, 0.15) 100%);
  border-radius: var(--radius-md);
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.pulse-ring {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow:
    0 0 0 10px rgba(245, 158, 11, 0.1),
    0 0 0 20px rgba(245, 158, 11, 0.05),
    var(--shadow-float);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
  animation: pulse 3s infinite ease-in-out;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4);
  }
  70% {
    box-shadow: 0 0 0 20px rgba(245, 158, 11, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0);
  }
}

.huge-number {
  font-size: 4.5rem;
  font-weight: 800;
  line-height: 1;
  font-family: var(--font-code);
  color: var(--color-warning);
  text-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}
.huge-unit {
  font-size: 0.95rem;
  font-weight: 700;
  color: #b45309;
  margin-top: 4px;
}
.dashboard-tip {
  text-align: center;
  font-size: 13px;
  color: #92400e;
  line-height: 1.6;
  padding: 0 16px;
}

@media (max-width: 1024px) {
  .visual-domain-grid {
    grid-template-columns: 1fr;
  }
}

.ai-attribution-panel {
  margin-top: var(--space-4);
  background: linear-gradient(135deg, rgba(254, 226, 226, 0.4) 0%, rgba(255, 255, 255, 0.6) 100%);
  border-left: 4px solid var(--color-danger);
}
.attribution-content {
  padding: 16px 20px;
}
.attribution-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.attribution-badge {
  display: flex;
  align-items: center;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 6px;
  overflow: hidden;
}
.badge-label {
  background: var(--color-danger);
  color: #fff;
  padding: 4px 10px;
  font-size: 0.8rem;
  font-weight: 600;
}
.badge-value {
  padding: 4px 12px;
  color: var(--color-danger);
  font-weight: 700;
  font-size: 0.9rem;
}
.attribution-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-danger);
  font-size: 0.9rem;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.6);
  padding: 4px 10px;
  border-radius: 6px;
}
.attribution-text {
  font-size: 1rem;
  line-height: 1.6;
  color: var(--color-text-main);
  background: rgba(255, 255, 255, 0.7);
  padding: 16px;
  border-radius: 8px;
  border: 1px dashed rgba(239, 68, 68, 0.3);
}
.attribution-actions {
  margin-top: 16px;
  text-align: right;
}
.examples-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}
.example-item {
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  padding: 10px 12px;
}
.example-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
  color: var(--color-danger);
  font-weight: 600;
}
.example-content {
  margin: 8px 0 0;
  font-size: 0.9rem;
  color: var(--color-text-main);
  line-height: 1.5;
}
</style>
