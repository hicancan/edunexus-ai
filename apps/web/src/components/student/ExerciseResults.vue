<script setup lang="ts">
import { NButton, NAlert, NSpin } from "naive-ui";
import { CheckCircle, AlertCircle, RefreshCw } from "lucide-vue-next";
import { useExerciseStore } from "../../features/student/model/exercise";

const exerciseStore = useExerciseStore();
</script>

<template>
  <div class="results-section">
    <div v-if="exerciseStore.latestResult" class="panel glass-card result-card">
      <h3 class="panel-title" style="margin-bottom: 24px">练习结果</h3>
      <div class="metric-grid">
        <div class="metric-glass">
          <span class="metric-label">总题数</span>
          <span class="metric-value">{{ exerciseStore.latestResult.totalQuestions }}</span>
        </div>
        <div class="metric-glass success-glass">
          <span class="metric-label">正确数</span>
          <span class="metric-value">{{ exerciseStore.latestResult.correctCount }}</span>
        </div>
        <div class="metric-glass highlight-glass">
          <span class="metric-label">总得分</span>
          <span class="metric-value">{{ exerciseStore.latestResult.totalScore }}</span>
        </div>
      </div>

      <div class="result-details-stack">
        <div
          v-for="(item, index) in exerciseStore.latestResult.items || []"
          :key="item.questionId"
          class="result-detail-item glass-pill-box"
        >
          <div class="detail-header">
            <span class="detail-name">第 {{ index + 1 }} 题</span>
            <component
              :is="item.isCorrect ? CheckCircle : AlertCircle"
              :size="20"
              :class="item.isCorrect ? 'text-success' : 'text-danger'"
            />
          </div>
          <div class="detail-body">
            <div class="detail-row">
              <span class="detail-label">你的答案：</span>
              <span class="detail-value" :class="item.isCorrect ? 'text-success' : 'text-danger'">{{
                item.userAnswer
              }}</span>
            </div>
            <div v-if="!item.isCorrect" class="detail-row">
              <span class="detail-label">正确答案：</span>
              <span class="detail-value text-success">{{ item.correctAnswer }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">本题得分：</span>
              <span class="detail-value font-code">{{ item.score }} 分</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="exerciseStore.analysisError" class="analysis-error-stack">
      <n-alert type="error" :show-icon="true" style="border-radius: var(--radius-md)">
        {{ exerciseStore.analysisError }}
      </n-alert>
      <div class="analysis-error-actions">
        <n-button
          size="small"
          @click="exerciseStore.loadAnalysis(exerciseStore.latestResult?.recordId || '')"
        >
          <template #icon><RefreshCw :size="14" /></template>
          重试
        </n-button>
      </div>
    </div>

    <n-spin v-if="exerciseStore.analysisLoading" :show="true" style="padding: 24px 0" />

    <div v-if="exerciseStore.currentAnalysis" class="panel glass-card analysis-card">
      <h3 class="panel-title">逐题解析</h3>
      <div class="analysis-stack">
        <div
          v-for="(item, index) in exerciseStore.currentAnalysis.items || []"
          :key="item.questionId"
          class="analysis-item-box"
        >
          <div class="a-header">
            <span class="a-title">节点 {{ index + 1 }}: {{ item.content }}</span>
            <span class="glass-pill a-badge" :class="item.isCorrect ? 'a-success' : 'a-error'">{{
              item.isCorrect ? "正确" : "错误"
            }}</span>
          </div>
          <div class="a-body">
            <div class="a-content-box">
              <h4 class="a-label">题目解析</h4>
              <p class="a-text">{{ item.analysis || "暂无解析" }}</p>
            </div>
            <div v-if="item.teacherSuggestion" class="a-content-box suggestion-box">
              <h4 class="a-label warn-color">学习建议</h4>
              <p class="a-text warn-color">{{ item.teacherSuggestion }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.results-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.metric-glass {
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid var(--color-border-glass);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.metric-label {
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 0.9rem;
}

.metric-value {
  font-size: 2.2rem;
  font-weight: 800;
  font-family: var(--font-code);
  color: var(--color-text-main);
  line-height: 1;
}

.success-glass {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.2);
}
.success-glass .metric-value {
  color: var(--color-success);
}
.highlight-glass {
  background: rgba(92, 101, 246, 0.1);
  border-color: rgba(92, 101, 246, 0.2);
}
.highlight-glass .metric-value {
  color: var(--color-primary);
}

.result-details-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-detail-item {
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--color-border-glass);
  border-radius: 12px;
  padding: 16px 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-name {
  font-weight: 700;
  font-size: 1.1rem;
}

.detail-body {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.95rem;
}

.detail-label {
  color: var(--color-text-muted);
}
.detail-value {
  font-weight: 600;
}
.font-code {
  font-family: var(--font-code);
}
.text-success {
  color: var(--color-success);
}
.text-danger {
  color: var(--color-danger);
}

.result-actions {
  margin-top: 24px;
  text-align: right;
}

.analysis-error-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.analysis-error-actions {
  display: flex;
  justify-content: flex-end;
}

.analysis-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
}

.analysis-item-box {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid var(--color-border-glass);
  border-left: 4px solid var(--color-primary);
  border-radius: 16px;
  padding: 20px;
}

.a-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.a-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-text-main);
}
.a-badge.a-success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
  border: none;
}
.a-badge.a-error {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-danger);
  border: none;
}
.a-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.a-content-box {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  padding: 16px;
}
.a-label {
  margin: 0 0 8px 0;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}
.a-text {
  margin: 0;
  line-height: 1.6;
}
.suggestion-box {
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
}
.warn-color {
  color: var(--color-warning);
}

@media (max-width: 768px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
