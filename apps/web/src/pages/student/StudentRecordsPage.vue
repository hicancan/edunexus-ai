<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { readQueryInt, readQueryString, replaceQuery } from "../../app/providers/query-state";
import { useExerciseStore } from "../../stores/exercise";

const route = useRoute();
const router = useRouter();
const exerciseStore = useExerciseStore();

const filters = reactive({
  startDate: "",
  endDate: "",
  page: 1,
  size: 20
});

const selectedRecordId = ref("");
const currentAnalysis = computed(() => {
  if (!selectedRecordId.value) {
    return null;
  }
  return exerciseStore.analysisByRecord[selectedRecordId.value] || null;
});

function hydrateFromQuery(): void {
  filters.startDate = readQueryString(route.query, "startDate");
  filters.endDate = readQueryString(route.query, "endDate");
  filters.page = readQueryInt(route.query, "page", 1);
  filters.size = readQueryInt(route.query, "size", 20);
}

async function syncQueryAndLoad(): Promise<void> {
  await replaceQuery(router, route.query, {
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    page: String(filters.page),
    size: String(filters.size)
  });
}

async function loadRecords(): Promise<void> {
  await exerciseStore.loadRecords({
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    page: filters.page,
    size: filters.size
  });
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  await syncQueryAndLoad();
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await syncQueryAndLoad();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await syncQueryAndLoad();
}

async function viewAnalysis(recordId: string): Promise<void> {
  selectedRecordId.value = recordId;
  await exerciseStore.loadAnalysis(recordId);
}

watch(
  () => route.query,
  async () => {
    hydrateFromQuery();
    await loadRecords();
  }
);

onMounted(async () => {
  hydrateFromQuery();
  await loadRecords();
});
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">做题记录</h2>
        <p class="panel-note">支持按时间区间检索，并可查看逐题解析。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="exerciseStore.recordsLoading" @click="loadRecords">
        {{ exerciseStore.recordsLoading ? "加载中..." : "刷新记录" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="records-start-date">开始日期</label>
        <input id="records-start-date" v-model="filters.startDate" type="date" />
      </div>
      <div class="field-block">
        <label for="records-end-date">结束日期</label>
        <input id="records-end-date" v-model="filters.endDate" type="date" />
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn" type="button" :disabled="exerciseStore.recordsLoading" @click="applyFilters">按条件查询</button>
    </div>

    <p v-if="exerciseStore.recordsError" class="status-box error" role="alert">{{ exerciseStore.recordsError }}</p>
    <div v-if="exerciseStore.recordsLoading && !exerciseStore.recordsLoaded" class="status-box info">正在加载记录...</div>
    <div v-else-if="exerciseStore.records.length === 0" class="status-box empty">暂无记录。</div>
    <div v-else class="list-stack">
      <article v-for="record in exerciseStore.records" :key="record.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ record.subject || "未分类" }}</p>
          <p class="list-item-meta">
            记录 ID：{{ record.id }} · 得分：{{ record.totalScore }} · 正确/总题：{{ record.correctCount }}/{{ record.totalQuestions }} · 时间：{{ record.createdAt }}
          </p>
        </div>
        <button class="btn secondary small" type="button" @click="viewAnalysis(record.id || '')">查看解析</button>
      </article>

      <PaginationBar
        :page="exerciseStore.recordsPage"
        :size="exerciseStore.recordsSize"
        :total-pages="exerciseStore.recordsTotalPages"
        :total-elements="exerciseStore.recordsTotalElements"
        :disabled="exerciseStore.recordsLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>

    <p v-if="exerciseStore.analysisError" class="status-box error" role="alert">{{ exerciseStore.analysisError }}</p>
    <section v-if="currentAnalysis" class="analysis-block">
      <h3>记录解析</h3>
      <div class="list-stack">
        <article v-for="item in currentAnalysis.items || []" :key="item.questionId" class="list-item analysis-item">
          <div class="list-item-main">
            <p class="list-item-title">{{ item.content }}</p>
            <p class="list-item-meta">你的答案：{{ item.userAnswer }} · 正确答案：{{ item.correctAnswer }} · {{ item.isCorrect ? "正确" : "错误" }}</p>
            <p class="analysis-line">解析：{{ item.analysis || "暂无解析" }}</p>
            <p v-if="item.teacherSuggestion" class="analysis-line">教师建议：{{ item.teacherSuggestion }}</p>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.analysis-block {
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  padding-top: 14px;
}

.analysis-block h3 {
  margin: 0 0 10px;
}

.analysis-item {
  align-items: flex-start;
}

.analysis-line {
  margin: 6px 0 0;
  color: #345b7f;
}
</style>
