<script setup lang="ts">
import { onMounted, reactive } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { useAdminStore } from "../../stores/admin";

const adminStore = useAdminStore();

const filters = reactive({
  page: 1,
  size: 20
});

async function loadAudits(): Promise<void> {
  await adminStore.loadAudits({
    page: filters.page,
    size: filters.size
  });
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await loadAudits();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await loadAudits();
}

onMounted(loadAudits);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">操作日志</h2>
        <p class="panel-note">记录关键治理操作，支持审计追踪。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="adminStore.auditsLoading" @click="loadAudits">
        {{ adminStore.auditsLoading ? "加载中..." : "刷新日志" }}
      </button>
    </header>

    <p v-if="adminStore.auditsError" class="status-box error" role="alert">{{ adminStore.auditsError }}</p>
    <div v-if="adminStore.auditsLoading && !adminStore.auditsLoaded" class="status-box info">正在加载日志...</div>
    <div v-else-if="adminStore.audits.length === 0" class="status-box empty">暂无日志。</div>
    <div v-else class="list-stack">
      <article v-for="audit in adminStore.audits" :key="audit.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ audit.action }}</p>
          <p class="list-item-meta">
            操作者：{{ audit.actorId || "系统" }} · 角色：{{ audit.actorRole || "--" }} · 资源：{{ audit.resourceType }} / {{ audit.resourceId }} · 时间：{{ audit.createdAt }}
          </p>
        </div>
      </article>

      <PaginationBar
        :page="adminStore.auditsPage"
        :size="adminStore.auditsSize"
        :total-pages="adminStore.auditsTotalPages"
        :total-elements="adminStore.auditsTotalElements"
        :disabled="adminStore.auditsLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>
  </section>
</template>
