<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  NCard,
  NForm,
  NFormItem,
  NDatePicker,
  NButton,
  NSpace,
  NText,
  NAlert,
  NDataTable,
  NTag,
  NModal,
  NSpin,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import { Search, Eye, CheckCircle, AlertCircle } from "lucide-vue-next";
import { readQueryInt, readQueryString, replaceQuery } from "../../app/providers/query-state";
import { useExerciseStore } from "../../features/student/model/exercise";
import type { ExerciseRecordVO } from "../../services/contracts";

const route = useRoute();
const router = useRouter();
const exerciseStore = useExerciseStore();
const message = useMessage();

const filters = reactive({
  dateRange: null as [number, number] | null,
  page: 1,
  size: 20
});

const selectedRecordId = ref("");
const showAnalysisModal = ref(false);

const currentAnalysis = computed(() => {
  if (!selectedRecordId.value) {
    return null;
  }
  return exerciseStore.analysisByRecord[selectedRecordId.value] || null;
});

function hydrateFromQuery(): void {
  const start = readQueryString(route.query, "startDate");
  const end = readQueryString(route.query, "endDate");
  if (start && end) {
    filters.dateRange = [Date.parse(start), Date.parse(end)];
  } else {
    filters.dateRange = null;
  }
  filters.page = readQueryInt(route.query, "page", 1);
  filters.size = readQueryInt(route.query, "size", 20);
}

async function syncQueryAndLoad(): Promise<void> {
  let startDate = undefined;
  let endDate = undefined;
  
  if (filters.dateRange && filters.dateRange.length === 2) {
    const d1 = new Date(filters.dateRange[0]);
    const d2 = new Date(filters.dateRange[1]);
    startDate = `${d1.getFullYear()}-${String(d1.getMonth() + 1).padStart(2, '0')}-${String(d1.getDate()).padStart(2, '0')}`;
    endDate = `${d2.getFullYear()}-${String(d2.getMonth() + 1).padStart(2, '0')}-${String(d2.getDate()).padStart(2, '0')}`;
  }

  await replaceQuery(router, route.query, {
    startDate,
    endDate,
    page: String(filters.page),
    size: String(filters.size)
  });
}

async function loadRecords(): Promise<void> {
  let startDate = undefined;
  let endDate = undefined;
  
  if (filters.dateRange && filters.dateRange.length === 2) {
     const d1 = new Date(filters.dateRange[0]);
     const d2 = new Date(filters.dateRange[1]);
     startDate = `${d1.getFullYear()}-${String(d1.getMonth() + 1).padStart(2, '0')}-${String(d1.getDate()).padStart(2, '0')}`;
     endDate = `${d2.getFullYear()}-${String(d2.getMonth() + 1).padStart(2, '0')}-${String(d2.getDate()).padStart(2, '0')}`;
  }

  await exerciseStore.loadRecords({
    startDate,
    endDate,
    page: filters.page,
    size: filters.size
  });
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  pagination.page = 1;
  await syncQueryAndLoad();
}

async function viewAnalysis(recordId: string): Promise<void> {
  selectedRecordId.value = recordId;
  showAnalysisModal.value = true;
  await exerciseStore.loadAnalysis(recordId);
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
  () => exerciseStore.recordsTotalElements,
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
    await loadRecords();
  }
);

const columns: DataTableColumns<ExerciseRecordVO> = [
  {
    title: "学科",
    key: "subject",
    render(row) {
      return h(NTag, { type: "info", size: "small", bordered: false }, { default: () => row.subject || "未分类" });
    }
  },
  {
    title: "记录 ID",
    key: "id",
    width: 280,
    render(row) {
      return h(NText, { depth: 3, code: true, style: "font-size: 12px" }, { default: () => row.id });
    }
  },
  {
    title: "得分",
    key: "totalScore",
    align: "center",
    render(row) {
      return h(NText, { type: "info", strong: true }, { default: () => `${row.totalScore} 分` });
    }
  },
  {
    title: "正确数 / 总题数",
    key: "correctCount",
    align: "center",
    render(row) {
      return h(
        NSpace,
        { align: "center", justify: "center", size: 4 },
        () => [
          h(NText, { type: "success", strong: true }, { default: () => row.correctCount }),
          h(NText, { depth: 3 }, { default: () => "/" }),
          h(NText, {}, { default: () => row.totalQuestions })
        ]
      );
    }
  },
  {
    title: "时间",
    key: "createdAt",
    width: 180,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.createdAt });
    }
  },
  {
    title: "操作",
    key: "actions",
    align: "center",
    width: 120,
    render(row) {
      return h(
        NButton,
        {
          size: "small",
          type: "primary",
          secondary: true,
          onClick: () => viewAnalysis(row.id || "")
        },
        { default: () => "查看解析", icon: () => h(Eye, { size: 14 }) }
      );
    }
  }
];

onMounted(async () => {
  hydrateFromQuery();
  pagination.page = filters.page;
  pagination.pageSize = filters.size;
  await loadRecords();
});
</script>

<template>
  <div class="records-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">做题记录</n-text>
          <n-text depth="3">支持按时间区间检索，并可查看逐题详尽解析。</n-text>
        </div>
      </div>

      <n-card :bordered="true">
        <n-form inline :model="filters" label-placement="left" :show-feedback="false">
          <n-form-item label="时间区间">
             <n-date-picker v-model:value="filters.dateRange" type="daterange" clearable />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" :loading="exerciseStore.recordsLoading" @click="applyFilters">
               <template #icon>
                <Search :size="16" />
              </template>
              查询记录
            </n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <n-alert v-if="exerciseStore.recordsError" type="error" :show-icon="true">{{ exerciseStore.recordsError }}</n-alert>

      <n-card :bordered="true" class="table-card" content-style="padding: 0;">
        <n-data-table
          remote
          :loading="exerciseStore.recordsLoading"
          :columns="columns"
          :data="exerciseStore.records"
          :pagination="pagination"
          :bordered="false"
          :bottom-bordered="false"
        />
      </n-card>
    </n-space>

    <!-- Modal overlay for analysis -->
    <n-modal
      v-model:show="showAnalysisModal"
      preset="card"
      title="记录深度解析"
      class="analysis-modal"
      :style="{ width: '800px', maxWidth: '95vw' }"
    >
      <div v-if="exerciseStore.analysisLoading" class="modal-loading">
        <n-spin size="large" />
      </div>
      <div v-else-if="exerciseStore.analysisError">
         <n-alert type="error" :show-icon="true">{{ exerciseStore.analysisError }}</n-alert>
      </div>
      <div v-else-if="currentAnalysis">
        <n-space vertical :size="16">
          <div v-for="(item, index) in currentAnalysis.items || []" :key="item.questionId" class="analysis-item-box">
            <n-text strong class="analysis-title">题目: {{ item.content }}</n-text>
            <div class="analysis-badge-row">
              <n-tag :type="item.isCorrect ? 'success' : 'error'" size="small">{{ item.isCorrect ? "正确" : "错误" }}</n-tag>
              <n-text depth="3">| 你的答案：{{ item.userAnswer }} | 正确答案：{{ item.correctAnswer }}</n-text>
            </div>
            <div class="analysis-content">
               <n-text depth="2" class="analysis-label">【考点解析】</n-text>
               <n-text>{{ item.analysis || "暂无解析" }}</n-text>
            </div>
            <div v-if="item.teacherSuggestion" class="teacher-suggestion">
               <n-text type="warning" class="analysis-label">【教师指导】</n-text>
               <n-text type="warning">{{ item.teacherSuggestion }}</n-text>
            </div>
          </div>
        </n-space>
      </div>
    </n-modal>
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

.modal-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px 0;
}

.analysis-item-box {
  padding: 16px;
  background-color: #f8fafc;
  border-radius: 8px;
  border-left: 4px solid #bae0ff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-title {
  font-size: 1.05rem;
}

.analysis-badge-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.analysis-content {
  margin-top: 4px;
  padding: 12px;
  background-color: #fff;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.teacher-suggestion {
  margin-top: 4px;
  padding: 12px;
  background-color: #fffbdf;
  border-radius: 6px;
  border: 1px solid #fce8a1;
}

.analysis-label {
  font-weight: 600;
  margin-right: 4px;
}
</style>
