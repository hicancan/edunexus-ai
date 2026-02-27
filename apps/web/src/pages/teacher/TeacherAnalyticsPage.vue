<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  NText,
  NAlert,
  NStatistic,
  NList,
  NListItem,
  NThing,
  useMessage
} from "naive-ui";
import { Search, TrendingUp, Target, BrainCircuit, PenTool, CheckCircle, Award } from "lucide-vue-next";
import { useTeacherStore } from "../../features/teacher-workspace/model/teacher";

import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { RadarChart, BarChart } from 'echarts/charts';
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components';
import VChart from 'vue-echarts';

use([
  CanvasRenderer,
  RadarChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
]);

const teacherStore = useTeacherStore();
const message = useMessage();
const studentId = ref(localStorage.getItem("teacher.analytics.studentId") || "");

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function loadAnalytics(): Promise<void> {
  const trimmedId = studentId.value.trim();
  if (!trimmedId) {
    message.warning("请求终止：必须输入学生身份锚点 (UUID)");
    return;
  }
  if (!UUID_PATTERN.test(trimmedId)) {
    message.warning("解析阻断：非法时空签名，请输入标准 UUID");
    return;
  }
  localStorage.setItem("teacher.analytics.studentId", trimmedId);
  await teacherStore.loadAnalytics(trimmedId);
}

const radarOption = computed(() => {
  const points = teacherStore.analytics?.topWeakPoints || [];
  if (points.length === 0) return {};
  
  const maxVal = Math.max(...points.map(p => p.wrongCount || 0), 5) + 2;
  return {
    tooltip: { trigger: 'item', padding: [12, 16], backgroundColor: 'rgba(255,255,255,0.9)', borderColor: 'rgba(92, 101, 246, 0.3)', textStyle: { color: '#1e293b' }, borderRadius: 12 },
    radar: {
      indicator: points.map(p => ({ name: p.knowledgePoint, max: maxVal })),
      shape: 'circle',
      splitNumber: 4,
      axisName: { color: '#475569', fontSize: 13, fontFamily: 'Outfit, sans-serif', fontWeight: 600 },
      splitLine: { lineStyle: { color: ['rgba(92, 101, 246, 0.1)', 'rgba(92, 101, 246, 0.2)', 'rgba(92, 101, 246, 0.3)', 'rgba(92, 101, 246, 0.5)'] } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(92, 101, 246, 0.15)' } }
    },
    series: [
      {
        name: '维度偏航分析',
        type: 'radar',
        data: [
          {
            value: points.map(p => p.wrongCount),
            name: '知识节点崩塌次数',
            symbol: 'circle',
            symbolSize: 8,
            itemStyle: { color: '#ef4444', borderColor: '#fff', borderWidth: 2 },
            areaStyle: { color: 'rgba(239, 68, 68, 0.25)' },
            lineStyle: { width: 3, color: '#ef4444' }
          }
        ]
      }
    ]
  };
});

onMounted(() => {
    if (studentId.value) {
        loadAnalytics();
    }
});
</script>

<template>
  <div class="analytics-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">学情综合追踪矩阵</h1>
          <p class="workspace-subtitle">搭载 ECharts 量子雷达，穿透式扫描单一学生的全景多维弱点画像。</p>
        </div>
      </div>

      <div class="panel glass-card search-panel">
        <n-form inline label-placement="left" :show-feedback="false" class="ethereal-form">
           <n-form-item label="探测坐标(UUID)">
             <n-input
                v-model:value="studentId"
                placeholder="在此输入目标学生的时空标签..."
                style="width: 360px"
                class="transparent-input"
                clearable
                @keydown.enter="loadAnalytics"
             />
           </n-form-item>
           <n-form-item>
             <n-button type="primary" class="animate-pop glass-pill-btn" :loading="teacherStore.analyticsLoading" @click="loadAnalytics">
               <template #icon><Search :size="16" /></template>
               执行深度跃迁扫描
             </n-button>
           </n-form-item>
        </n-form>
      </div>

      <n-alert v-if="teacherStore.analyticsError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ teacherStore.analyticsError }}</n-alert>

      <div v-if="teacherStore.analytics" class="analytics-dashboard">
         <!-- Data Pulse Grid -->
         <div class="metrics-grid">
            <div class="metric-glass target-glass animate-pop">
               <Target :size="28" class="metric-icon" />
               <div class="metric-data">
                 <span class="metric-label">追踪实体签核</span>
                 <span class="metric-value str-val">{{ teacherStore.analytics.username }}</span>
               </div>
            </div>
            
            <div class="metric-glass animate-pop">
               <PenTool :size="28" class="metric-icon info" />
               <div class="metric-data">
                 <span class="metric-label">总出击频次</span>
                 <span class="metric-value">{{ teacherStore.analytics.totalExercises }} <span class="unit">次</span></span>
               </div>
            </div>

            <div class="metric-glass animate-pop">
               <BrainCircuit :size="28" class="metric-icon warning" />
               <div class="metric-data">
                 <span class="metric-label">试炼题图总量</span>
                 <span class="metric-value">{{ teacherStore.analytics.totalQuestions }} <span class="unit">道</span></span>
               </div>
            </div>

            <div class="metric-glass animate-pop">
               <CheckCircle :size="28" class="metric-icon success" />
               <div class="metric-data">
                 <span class="metric-label">完美拟合击杀</span>
                 <span class="metric-value">{{ teacherStore.analytics.correctCount }} <span class="unit">道</span></span>
               </div>
            </div>

            <div class="metric-glass highlight-glass animate-pop">
               <Award :size="28" class="metric-icon highlight" />
               <div class="metric-data">
                 <span class="metric-label">绝对能盘捕获率</span>
                 <span class="metric-value highlight-val">{{ Number(teacherStore.analytics.averageScore).toFixed(1) }} <span class="unit">%</span></span>
               </div>
            </div>
         </div>
         
         <!-- Visual Analytics Domain -->
         <div class="visual-domain-grid">
            <div class="panel glass-card radar-panel">
               <div class="panel-head">
                  <h3 class="panel-title">高频认知坍缩雷达 <TrendingUp :size="18" style="color: var(--color-danger); margin-left: 8px" /></h3>
                  <p class="panel-note">检测到多维逻辑断层风险点</p>
               </div>
               
               <div class="echart-container">
                  <n-empty v-if="!teacherStore.analytics?.topWeakPoints?.length" description="该域不存在明显思维坍缩漏斗" />
                  <v-chart v-else class="radar-chart" :option="radarOption" autoresize />
               </div>
            </div>

            <div class="panel glass-card wrong-book-panel">
               <div class="panel-head">
                  <h3 class="panel-title">错能驻留池</h3>
                  <p class="panel-note">尚未被主观覆写消化的历史痛点总量</p>
               </div>
               
               <div class="wrong-book-dashboard">
                  <div class="pulse-ring">
                     <span class="huge-number">{{ teacherStore.analytics.wrongBookCount }}</span>
                     <span class="huge-unit">未解决节点</span>
                  </div>
                  <p class="dashboard-tip">建议启动「Teacher Suggestions」定向发射脉冲干预，或直接召唤 Nexus Agent 进行伴读辅导。</p>
               </div>
            </div>
         </div>

         <!-- AI Error Attribution Panel (Mock) -->
         <div class="panel glass-card ai-attribution-panel">
            <div class="panel-head">
              <h3 class="panel-title" style="color: var(--color-danger); display: flex; align-items: center;">
                <BrainCircuit :size="18" style="margin-right: 8px;" />
                Nexus Agent 群体报错深度聚类归因
              </h3>
            </div>
            <div class="attribution-content">
              <div class="attribution-header">
                <div class="attribution-badge">
                   <span class="badge-label">高频预警节点</span>
                   <span class="badge-value">闭包与作用域链</span>
                </div>
                <div class="attribution-stats">
                   <Target :size="16" class="stats-icon" />
                   <span class="stats-text">45% 样本受影响 (约 18人)</span>
                </div>
              </div>
              <p class="attribution-text">
                基于大模型对近期推演记录的无监督聚类分析，系统发现核心共性错因为：<br/>
                学生对<strong>跨空间生命周期理解不清，经常捕获旧指针或未能正确访问外层词法环境</strong>，导致 <code>undefined</code> 报错频发。
              </p>
              <div class="attribution-actions">
                <n-button type="primary" class="animate-pop" color="#ef4444" @click="message.success('已联动生成靶向干预计划！')">
                  采取 AI 一键补救干预
                </n-button>
              </div>
            </div>
         </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 16px 24px;
}

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
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  color: var(--color-primary);
}

.metric-icon.info { color: #3b82f6; }
.metric-icon.warning { color: #f59e0b; }
.metric-icon.success { color: #10b981; }
.metric-icon.highlight { color: #2563eb; background: #eff6ff; }

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
  box-shadow: 0 0 0 10px rgba(245, 158, 11, 0.1), 0 0 0 20px rgba(245, 158, 11, 0.05), var(--shadow-float);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
  animation: pulse 3s infinite ease-in-out;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  70% { box-shadow: 0 0 0 20px rgba(245, 158, 11, 0); }
  100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
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
  background: rgba(255,255,255,0.6);
  padding: 4px 10px;
  border-radius: 6px;
}

.attribution-text {
  font-size: 1rem;
  line-height: 1.6;
  color: var(--color-text-main);
  background: rgba(255,255,255,0.7);
  padding: 16px;
  border-radius: 8px;
  border: 1px dashed rgba(239, 68, 68, 0.3);
}

.attribution-text strong {
  color: var(--color-danger);
}

.attribution-actions {
  margin-top: 16px;
  text-align: right;
}
</style>
