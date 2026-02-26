<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { useAdminStore } from "../../stores/admin";

const adminStore = useAdminStore();

const filters = reactive<{
  resourceType: "" | "LESSON_PLAN" | "QUESTION" | "DOCUMENT";
  page: number;
  size: number;
}>({
  resourceType: "",
  page: 1,
  size: 20
});

const operationResult = ref("");

async function loadResources(): Promise<void> {
  await adminStore.loadResources({
    resourceType: filters.resourceType || undefined,
    page: filters.page,
    size: filters.size
  });
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  await loadResources();
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await loadResources();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await loadResources();
}

async function downloadResource(resourceId: string): Promise<void> {
  if (!resourceId) {
    return;
  }
  const blob = await adminStore.downloadResource(resourceId);
  if (!blob) {
    return;
  }

  const fileName = `resource-${resourceId}`;
  const blobUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = blobUrl;
  anchor.download = fileName;
  anchor.click();
  window.URL.revokeObjectURL(blobUrl);

  operationResult.value = `资源下载成功：${fileName}`;
}

onMounted(loadResources);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">资源管理</h2>
        <p class="panel-note">支持按资源类型筛选并下载教案、题库与知识文档。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="adminStore.resourcesLoading" @click="loadResources">
        {{ adminStore.resourcesLoading ? "加载中..." : "刷新资源" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="resource-type">资源类型</label>
        <select id="resource-type" v-model="filters.resourceType">
          <option value="">全部</option>
          <option value="LESSON_PLAN">LESSON_PLAN</option>
          <option value="QUESTION">QUESTION</option>
          <option value="DOCUMENT">DOCUMENT</option>
        </select>
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn" type="button" :disabled="adminStore.resourcesLoading" @click="applyFilters">按条件查询</button>
    </div>

    <p v-if="adminStore.resourcesError" class="status-box error" role="alert">{{ adminStore.resourcesError }}</p>
    <p v-if="adminStore.operationError" class="status-box error" role="alert">{{ adminStore.operationError }}</p>
    <p v-if="operationResult" class="status-box success">{{ operationResult }}</p>

    <div v-if="adminStore.resourcesLoading && !adminStore.resourcesLoaded" class="status-box info">正在加载资源...</div>
    <div v-else-if="adminStore.resources.length === 0" class="status-box empty">暂无资源。</div>
    <div v-else class="list-stack">
      <article v-for="resource in adminStore.resources" :key="resource.resourceId" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ resource.title }}</p>
          <p class="list-item-meta">资源 ID：{{ resource.resourceId }} · 类型：{{ resource.resourceType }} · 创建者：{{ resource.creatorUsername }} · 创建时间：{{ resource.createdAt }}</p>
        </div>
        <button class="btn secondary small" type="button" aria-label="下载资源" @click="downloadResource(resource.resourceId || '')">下载</button>
      </article>

      <PaginationBar
        :page="adminStore.resourcesPage"
        :size="adminStore.resourcesSize"
        :total-pages="adminStore.resourcesTotalPages"
        :total-elements="adminStore.resourcesTotalElements"
        :disabled="adminStore.resourcesLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>
  </section>
</template>
