<script setup lang="ts">
import { h, onMounted, reactive, watch } from "vue";
import {
  NCard,
  NButton,
  NSpace,
  NText,
  NAlert,
  NDataTable,
  NTag,
  type DataTableColumns
} from "naive-ui";
import { RefreshCw, Activity, ShieldAlert, Cpu } from "lucide-vue-next";
import { useAdminStore } from "../../features/admin/model/admin";

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

const pagination = reactive({
  page: filters.page,
  pageSize: filters.size,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100],
  onChange: (page: number) => {
    pagination.page = page;
    filters.page = page;
    loadAudits();
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    filters.size = pageSize;
    filters.page = 1;
    loadAudits();
  },
  itemCount: 0
});

watch(
  () => adminStore.auditsTotalElements,
  (total) => {
    pagination.itemCount = total;
  }
);

function getActionColor(action: string): "default" | "error" | "info" | "success" | "warning" {
  const lowered = action?.toLowerCase() || "";
  if (lowered.includes("create") || lowered.includes("import") || lowered.includes("generate")) return "success";
  if (lowered.includes("delete") || lowered.includes("remove") || lowered.includes("destroy")) return "error";
  if (lowered.includes("update") || lowered.includes("edit") || lowered.includes("modify")) return "warning";
  if (lowered.includes("login") || lowered.includes("auth") || lowered.includes("read")) return "info";
  return "default";
}

const columns: DataTableColumns<any> = [
  {
    title: "安全事件标识",
    key: "action",
    width: 250,
    render(row) {
       return h(
         NSpace,
         { align: "center", size: 6 },
         () => [
            h(Activity, { size: 14, style: "color: #94a3b8" }),
            h(NTag, { type: getActionColor(row.action), size: "small", bordered: false }, { default: () => row.action || "UNKNOWN" })
         ]
       );
    }
  },
  {
    title: "触发主体 (Actor)",
    key: "actorId",
    width: 320,
    render(row) {
      if (!row.actorId) {
         return h(
            NSpace,
            { align: "center", size: 4 },
            () => [
               h(Cpu, { size: 14, style: "color: #8b5cf6" }),
               h(NText, { type: "info", strong: true }, { default: () => "引擎守护进程 / 系统级" })
            ]
         );
      }
      return h(
         NSpace,
         { vertical: true, size: 2 },
         () => [
            h(NText, { code: true, style: "font-size: 12px;" }, { default: () => row.actorId }),
            h(NText, { depth: 3, style: "font-size: 12px;" }, { default: () => `认证态: ${row.actorRole || "GUEST"}` })
         ]
      );
    }
  },
  {
    title: "受控路由客体 (Resource)",
    key: "resourceId",
    minWidth: 200,
    render(row) {
      return h(
         NSpace,
         { vertical: true, size: 2 },
         () => [
            h(NText, { strong: true }, { default: () => row.resourceType || "GLOBAL" }),
            row.resourceId ? h(NText, { depth: 3, code: true, style: "font-size: 12px;" }, { default: () => row.resourceId }) : null
         ]
      );
    }
  },
  {
    title: "物理写入总线时间",
    key: "createdAt",
    width: 180,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.createdAt });
    }
  }
];

onMounted(loadAudits);
</script>

<template>
  <div class="audits-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">零信任防火墙防篡改日志</n-text>
          <n-space align="center" :size="6" style="margin-top: 4px;">
            <ShieldAlert :size="14" style="color: #64748b;" />
            <n-text depth="3">基于不可变底层数据结构的事务审计总线，捕获记录一切危险与提权操作。</n-text>
          </n-space>
        </div>
        <n-button type="primary" secondary class="animate-pop glass-pill" :loading="adminStore.auditsLoading" @click="loadAudits">
           <template #icon><RefreshCw :size="16" /></template>
           拉取最新流向数据
        </n-button>
      </div>

      <n-alert v-if="adminStore.auditsError" type="error" :show-icon="true">{{ adminStore.auditsError }}</n-alert>

      <n-card :bordered="false" class="table-card glass-card" content-style="padding: 0;">
        <n-data-table
          remote
          :loading="adminStore.auditsLoading"
          :columns="columns"
          :data="adminStore.audits"
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
  --n-merged-th-color: rgba(255,255,255,0.4);
  --n-merged-td-color: rgba(255,255,255,0.1);
  --n-merged-td-color-hover: rgba(255,255,255,0.3);
  --n-merged-border-color: var(--color-border-glass);
}

:deep(.n-data-table-th) {
  font-weight: 700;
  backdrop-filter: blur(8px);
}
</style>
