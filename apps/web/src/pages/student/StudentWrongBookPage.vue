<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NButton,
  NSpace,
  NText,
  NAlert,
  NDataTable,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import { Search, Check, RefreshCw } from "lucide-vue-next";
import { readQueryInt, readQueryString, replaceQuery } from "../../app/providers/query-state";
import { useExerciseStore } from "../../features/student/model/exercise";
import type { WrongBookEntryVO } from "../../services/contracts";

const route = useRoute();
const router = useRouter();
const exerciseStore = useExerciseStore();
const dialog = useDialog();
const message = useMessage();

const filters = reactive<{
  subject: string;
  status: "ACTIVE" | "MASTERED";
  page: number;
  size: number;
}>({
  subject: "",
  status: "ACTIVE",
  page: 1,
  size: 20
});

const statusOptions = [
  { label: "练习中 ACTIVE", value: "ACTIVE" },
  { label: "已掌握 MASTERED", value: "MASTERED" }
];

function parseStatus(value: unknown): "ACTIVE" | "MASTERED" {
  if (value === "MASTERED") {
    return "MASTERED";
  }
  return "ACTIVE";
}

function hydrateFromQuery(): void {
  filters.subject = readQueryString(route.query, "subject");
  filters.status = parseStatus(route.query.status);
  filters.page = readQueryInt(route.query, "page", 1);
  filters.size = readQueryInt(route.query, "size", 20);
}

async function syncQueryAndLoad(): Promise<void> {
  await replaceQuery(router, route.query, {
    subject: filters.subject || undefined,
    status: filters.status,
    page: String(filters.page),
    size: String(filters.size)
  });
}

async function loadWrongBook(): Promise<void> {
  await exerciseStore.loadWrongEntries({
    subject: filters.subject || undefined,
    status: filters.status,
    page: filters.page,
    size: filters.size
  });
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  await syncQueryAndLoad();
}

const pagination = reactive({
  page: filters.page,
  pageSize: filters.size,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page;
    filters.page = page;
    syncQueryAndLoad();
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    filters.size = pageSize;
    filters.page = 1;
    syncQueryAndLoad();
  },
  itemCount: 0
});

watch(
  () => exerciseStore.wrongTotalElements,
  (total) => {
    pagination.itemCount = total;
  }
);

watch(
  () => route.query,
  async () => {
    hydrateFromQuery();
    pagination.page = filters.page;
    pagination.pageSize = filters.size;
    await loadWrongBook();
  }
);

function confirmMarkMastered(questionId: string): void {
  dialog.warning({
    title: "标记已掌握",
    content: "确认将该错题标记为已掌握吗？它将被移入已掌握列表。",
    positiveText: "确认掌握",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await exerciseStore.markWrongMastered(questionId, {
          subject: filters.subject || undefined,
          status: filters.status,
          page: filters.page,
          size: filters.size
        });
        message.success("已成功移出当前错题本");
      } catch (e) {
        message.error("操作失败");
      }
    }
  });
}

const columns: DataTableColumns<WrongBookEntryVO> = [
  {
    title: "题目内容",
    key: "question.content",
    minWidth: 300,
    render(row) {
      return h(
        NText,
        { depth: 1, style: "white-space: pre-wrap; display: block; max-height: 80px; overflow: hidden; text-overflow: ellipsis;" },
        { default: () => row.question?.content || "内容缺失" }
      );
    }
  },
  {
    title: "错误次数",
    key: "wrongCount",
    width: 100,
    align: "center",
    render(row) {
      return h(
        NText,
        { type: "error", strong: true },
        { default: () => row.wrongCount }
      );
    }
  },
  {
    title: "最近错误时间",
    key: "lastWrongTime",
    width: 200,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.lastWrongTime });
    }
  },
  {
    title: "状态",
    key: "status",
    width: 120,
    render(row) {
      return h(
        NTag,
        {
          type: row.status === "ACTIVE" ? "warning" : "success",
          size: "small",
          bordered: false
        },
        { default: () => (row.status === "ACTIVE" ? "练习中" : "已掌握") }
      );
    }
  },
  {
    title: "操作",
    key: "actions",
    width: 120,
    align: "center",
    render(row) {
      if (row.status === "ACTIVE") {
        return h(
          NButton,
          {
            size: "small",
            type: "primary",
            secondary: true,
            onClick: () => confirmMarkMastered(row.questionId || "")
          },
          {
            default: () => "标记掌握",
            icon: () => h(Check, { size: 14 })
          }
        );
      }
      return h(NText, { depth: 3 }, { default: () => "-" });
    }
  }
];

onMounted(async () => {
  hydrateFromQuery();
  pagination.page = filters.page;
  pagination.pageSize = filters.size;
  await loadWrongBook();
});
</script>

<template>
  <div class="wrong-book-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">错题本</n-text>
          <n-text depth="3">支持按学科与状态筛选，掌握后可标记为 MASTERED 移出租本。</n-text>
        </div>
      </div>

      <n-card :bordered="true">
        <n-form inline :model="filters" label-placement="left" :show-feedback="false">
          <n-form-item label="学科">
             <n-input v-model:value="filters.subject" placeholder="例如：物理" clearable @keydown.enter="applyFilters" />
          </n-form-item>
          <n-form-item label="状态">
             <n-select v-model:value="filters.status" :options="statusOptions" style="width: 160px" @update:value="applyFilters" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" :loading="exerciseStore.wrongLoading" @click="applyFilters">
               <template #icon>
                <Search :size="16" />
              </template>
              查询错题
            </n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <n-alert v-if="exerciseStore.wrongError" type="error" :show-icon="true">{{ exerciseStore.wrongError }}</n-alert>

      <n-card :bordered="true" class="table-card" content-style="padding: 0;">
        <n-data-table
          remote
          :loading="exerciseStore.wrongLoading"
          :columns="columns"
          :data="exerciseStore.wrongEntries"
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
  font-size: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-card {
  border-radius: 8px;
  overflow: hidden;
}
</style>
