<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { NButton, NEmpty, NSpin, useMessage } from "naive-ui";
import { Send, Users, ShieldAlert, Sparkles, CheckCircle2 } from "lucide-vue-next";
import { useAnalyticsStore } from "../../features/teacher-workspace/model/analytics";
import { useSuggestionStore } from "../../features/teacher-workspace/model/suggestions";

const analyticsStore = useAnalyticsStore();
const suggestionStore = useSuggestionStore();
const message = useMessage();
const dispatchingPoint = ref("");

const recommendations = computed(() => analyticsStore.interventions);

function isDispatched(knowledgePoint: string): boolean {
  return suggestionStore.dispatchedPoints.includes(knowledgePoint || "");
}

async function dispatchIntervention(
  knowledgePoint: string,
  suggestionTemplate: string
): Promise<void> {
  dispatchingPoint.value = knowledgePoint;
  try {
    const result = await suggestionStore.dispatchSuggestion({
      knowledgePoint,
      suggestion: suggestionTemplate
    });
    if (!result) {
      message.error(suggestionStore.suggestionError || "发送失败");
      return;
    }
    message.success(`已发送给 ${result.createdCount} 名学生`);
    await analyticsStore.loadInterventions();
  } finally {
    dispatchingPoint.value = "";
  }
}

onMounted(() => {
  void analyticsStore.loadInterventions();
});
</script>

<template>
  <div class="suggestions-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">教师干预建议</h1>
          <p class="workspace-subtitle">基于错题聚合推荐可批量发送的教学建议。</p>
        </div>
      </div>

      <n-spin :show="analyticsStore.interventionsLoading || suggestionStore.suggestionLoading">
        <n-empty
          v-if="recommendations.length === 0"
          description="暂无可发送的教学建议"
          style="margin-top: 40px"
        />

        <div v-else class="intervention-grid">
          <div
            v-for="item in recommendations"
            :key="item.knowledgePoint"
            class="panel glass-card int-card"
            :class="{ 'dispatched-card': isDispatched(item.knowledgePoint || '') }"
          >
            <div class="int-header">
              <div class="int-icon">
                <ShieldAlert
                  v-if="!isDispatched(item.knowledgePoint || '')"
                  :size="24"
                  class="text-danger"
                />
                <CheckCircle2 v-else :size="24" class="text-success" />
              </div>
              <div class="int-title-area">
                <h3 class="int-topic">{{ item.knowledgePoint }}</h3>
                <span class="int-meta">
                  <Users :size="14" style="margin-right: 4px" />
                  影响学生：{{ item.studentCount }} 人 · 累计错次：{{ item.totalWrongCount }}
                </span>
              </div>
            </div>

            <div class="int-body">
              <div class="ai-suggest-box">
                <span class="ai-badge"
                  ><Sparkles :size="12" style="margin-right: 4px" /> 系统建议</span
                >
                <p class="ai-strategy">{{ item.suggestionTemplate }}</p>
              </div>
            </div>

            <div class="int-footer">
              <n-button
                v-if="!isDispatched(item.knowledgePoint || '')"
                type="primary"
                :loading="dispatchingPoint === (item.knowledgePoint || '')"
                :disabled="
                  dispatchingPoint !== '' && dispatchingPoint !== (item.knowledgePoint || '')
                "
                @click="
                  dispatchIntervention(item.knowledgePoint || '', item.suggestionTemplate || '')
                "
              >
                <template #icon><Send :size="16" /></template>
                批量发送
              </n-button>
              <n-button v-else type="success" secondary disabled> 已发送 </n-button>
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
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
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
  opacity: 0.85;
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
  margin: 0 0 6px 0;
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
}

.dispatched-card .int-meta {
  color: var(--color-success);
  background: rgba(16, 185, 129, 0.1);
}

.ai-suggest-box {
  background: linear-gradient(135deg, rgba(92, 101, 246, 0.05), rgba(92, 101, 246, 0.15));
  padding: 16px;
  border-radius: 8px;
  border: 1px solid rgba(92, 101, 246, 0.2);
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.75rem;
  color: var(--color-primary);
  font-weight: 700;
  margin-bottom: 8px;
}

.ai-strategy {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.5;
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
