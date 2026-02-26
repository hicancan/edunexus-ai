<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useAdminStore } from "../../stores/admin";

const adminStore = useAdminStore();

const metricEntries = computed(() => {
  if (!adminStore.metrics) {
    return [];
  }
  return Object.entries(adminStore.metrics);
});

async function loadMetrics(): Promise<void> {
  await adminStore.loadMetrics();
}

onMounted(loadMetrics);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">平台指标看板</h2>
        <p class="panel-note">展示用户规模、聊天会话、做题记录、文档与向量等核心指标。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="adminStore.metricsLoading" @click="loadMetrics">
        {{ adminStore.metricsLoading ? "刷新中..." : "刷新看板" }}
      </button>
    </header>

    <p v-if="adminStore.metricsError" class="status-box error" role="alert">{{ adminStore.metricsError }}</p>
    <div v-if="adminStore.metricsLoading && !adminStore.metrics" class="status-box info">正在加载指标...</div>
    <div v-else-if="!adminStore.metrics" class="status-box empty">暂无指标数据。</div>
    <div v-else class="metrics-grid">
      <article v-for="[name, value] in metricEntries" :key="name" class="metric-card">
        <p class="metric-name">{{ name }}</p>
        <p class="metric-value">{{ value }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.metrics-grid {
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
  font-size: 1.34rem;
  font-weight: 800;
  color: #24486e;
  overflow-wrap: anywhere;
}

@media (max-width: 1279px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
