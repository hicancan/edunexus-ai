<script setup lang="ts">
import { h, onMounted, reactive, watch } from "vue";
import {
  NCard,
  NForm,
  NFormItem,
  NSelect,
  NButton,
  NSpace,
  NText,
  NAlert,
  NDataTable,
  NTag,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import { Download, Search, FileText, Database, BookOpen } from "lucide-vue-next";
import { useAdminStore } from "../../features/admin/model/admin";

const adminStore = useAdminStore();
const message = useMessage();

const filters = reactive<{
  resourceType: "" | "LESSON_PLAN" | "QUESTION" | "DOCUMENT";
  page: number;
  size: number;
}>({
  resourceType: "",
  page: 1,
  size: 20
});

const resourceTypeOptions = [
  { label: "全部资源", value: "" },
  { label: "教案 (LESSON_PLAN)", value: "LESSON_PLAN" },
  { label: "题目 (QUESTION)", value: "QUESTION" },
  { label: "文档 (DOCUMENT)", value: "DOCUMENT" }
];
const resourceTypeInputProps = {
  id: "admin-resource-type",
  name: "adminResourceType",
  "aria-label": "资源类型筛选"
};

async function loadResources(): Promise<void> {
  await adminStore.loadResources({
    resourceType: filters.resourceType || undefined,
    page: filters.page,
    size: filters.size
  });
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  pagination.page = 1;
  await loadResources();
}

const pagination = reactive({
  page: filters.page,
  pageSize: filters.size,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page;
    filters.page = page;
    loadResources();
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    filters.size = pageSize;
    filters.page = 1;
    loadResources();
  },
  itemCount: 0
});

watch(
  () => adminStore.resourcesTotalElements,
  (total) => {
    pagination.itemCount = total;
  }
);

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

  message.success(`资源下载完成：${fileName}`);
}

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  LESSON_PLAN: "教案",
  QUESTION: "题目",
  DOCUMENT: "文档"
};

function getResourceIcon(type: string): any {
  if (type === "LESSON_PLAN") return BookOpen;
  if (type === "QUESTION") return Database;
  if (type === "DOCUMENT") return FileText;
  return FileText;
}

function getResourceTagType(type: string): "info" | "success" | "warning" | "error" | "default" {
  if (type === "LESSON_PLAN") return "warning";
  if (type === "QUESTION") return "success";
  if (type === "DOCUMENT") return "info";
  return "default";
}

const columns: DataTableColumns<any> = [
  {
    title: "资源名称",
    key: "title",
    minWidth: 200,
    render(row) {
      return h(NSpace, { align: "center", size: 8 }, () => [
        h(getResourceIcon(row.resourceType || ""), { size: 16, style: "color: #64748b" }),
        h(NText, { strong: true }, { default: () => row.title })
      ]);
    }
  },
  {
    title: "资源 ID",
    key: "resourceId",
    width: 280,
    render(row) {
      return h(
        NText,
        { depth: 3, code: true, style: "font-size: 12px" },
        { default: () => row.resourceId }
      );
    }
  },
  {
    title: "类型",
    key: "resourceType",
    width: 150,
    render(row) {
      return h(
        NTag,
        { type: getResourceTagType(row.resourceType || ""), bordered: false, size: "small" },
        { default: () => RESOURCE_TYPE_LABELS[row.resourceType] || row.resourceType }
      );
    }
  },
  {
    title: "创建人",
    key: "creatorUsername",
    width: 150,
    render(row) {
      return h(NText, { depth: 2 }, { default: () => row.creatorUsername || "--" });
    }
  },
  {
    title: "创建时间",
    key: "createdAt",
    width: 180,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.createdAt });
    }
  },
  {
    title: "下载",
    key: "actions",
    align: "center",
    width: 100,
    render(row) {
      return h(
        NButton,
        {
          size: "small",
          type: "primary",
          quaternary: true,
          circle: true,
          "aria-label": `下载资源 ${row.title || row.resourceId || ""}`.trim(),
          title: `下载资源 ${row.title || row.resourceId || ""}`.trim(),
          onClick: () => downloadResource(row.resourceId || "")
        },
        { icon: () => h(Download, { size: 16 }) }
      );
    }
  }
];

onMounted(loadResources);
</script>

<template>
  <div class="resources-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">资源管理</n-text>
          <n-text depth="3">查看平台内的文档、题目和教案资源。</n-text>
        </div>
      </div>

      <n-card class="glass-card" :bordered="false" size="small">
        <n-form inline :model="filters" label-placement="left" :show-feedback="false">
          <n-form-item label="资源类型">
            <n-select
              v-model:value="filters.resourceType"
              :options="resourceTypeOptions"
              :input-props="resourceTypeInputProps"
              style="width: 240px"
            />
          </n-form-item>
          <n-form-item>
            <n-button
              type="primary"
              :loading="adminStore.resourcesLoading"
              class="animate-pop glass-pill"
              @click="applyFilters"
            >
              <template #icon><Search :size="16" /></template>
              查询
            </n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <n-alert v-if="adminStore.resourcesError" type="error" :show-icon="true">{{
        adminStore.resourcesError
      }}</n-alert>
      <n-alert v-if="adminStore.operationError" type="error" :show-icon="true">{{
        adminStore.operationError
      }}</n-alert>

      <n-card :bordered="false" class="table-card glass-card" content-style="padding: 0;">
        <n-data-table
          remote
          :loading="adminStore.resourcesLoading"
          :columns="columns"
          :data="adminStore.resources"
          :pagination="pagination"
          :bordered="false"
          :bottom-bordered="false"
        />
      </n-card>
    </n-space>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 1.6rem;
  font-weight: 800;
  background: linear-gradient(135deg, var(--color-primary) 0%, #60a5fa 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.table-card {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-glass);
}

:deep(.n-data-table) {
  background: transparent;
  --n-merged-th-color: rgba(255, 255, 255, 0.4);
  --n-merged-td-color: rgba(255, 255, 255, 0.1);
  --n-merged-td-color-hover: rgba(255, 255, 255, 0.3);
  --n-merged-border-color: var(--color-border-glass);
}

:deep(.n-data-table-th) {
  font-weight: 700;
  backdrop-filter: blur(8px);
}
</style>
