<script setup lang="ts">
import { onMounted, reactive, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { readQueryInt, readQueryString, replaceQuery } from "../../app/providers/query-state";
import { useExerciseStore } from "../../stores/exercise";

const route = useRoute();
const router = useRouter();
const exerciseStore = useExerciseStore();

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

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await syncQueryAndLoad();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await syncQueryAndLoad();
}

async function markAsMastered(questionId: string): Promise<void> {
  if (!questionId || !window.confirm("确认将该错题标记为已掌握吗？")) {
    return;
  }
  await exerciseStore.markWrongMastered(questionId, {
    subject: filters.subject || undefined,
    status: filters.status,
    page: filters.page,
    size: filters.size
  });
}

watch(
  () => route.query,
  async () => {
    hydrateFromQuery();
    await loadWrongBook();
  }
);

onMounted(async () => {
  hydrateFromQuery();
  await loadWrongBook();
});
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">错题本</h2>
        <p class="panel-note">支持按学科与状态筛选，掌握后可标记为 MASTERED。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="exerciseStore.wrongLoading" @click="loadWrongBook">
        {{ exerciseStore.wrongLoading ? "加载中..." : "刷新错题本" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="wrong-subject">学科</label>
        <input id="wrong-subject" v-model="filters.subject" placeholder="例如：物理" />
      </div>
      <div class="field-block">
        <label for="wrong-status">状态</label>
        <select id="wrong-status" v-model="filters.status">
          <option value="ACTIVE">ACTIVE</option>
          <option value="MASTERED">MASTERED</option>
        </select>
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn" type="button" :disabled="exerciseStore.wrongLoading" @click="applyFilters">按条件查询</button>
    </div>

    <p v-if="exerciseStore.wrongError" class="status-box error" role="alert">{{ exerciseStore.wrongError }}</p>
    <div v-if="exerciseStore.wrongLoading && !exerciseStore.wrongLoaded" class="status-box info">正在加载错题...</div>
    <div v-else-if="exerciseStore.wrongEntries.length === 0" class="status-box empty">暂无错题记录。</div>
    <div v-else class="list-stack">
      <article v-for="entry in exerciseStore.wrongEntries" :key="entry.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ entry.question?.content || "题目内容缺失" }}</p>
          <p class="list-item-meta">
            题目 ID：{{ entry.questionId }} · 错误次数：{{ entry.wrongCount }} · 最近错误时间：{{ entry.lastWrongTime }} · 状态：{{ entry.status }}
          </p>
        </div>
        <div class="list-item-actions">
          <span class="pill">{{ entry.status }}</span>
          <button
            v-if="entry.status === 'ACTIVE'"
            class="btn success small"
            type="button"
            aria-label="标记错题已掌握"
            @click="markAsMastered(entry.questionId || '')"
          >
            标记掌握
          </button>
        </div>
      </article>

      <PaginationBar
        :page="exerciseStore.wrongPage"
        :size="exerciseStore.wrongSize"
        :total-pages="exerciseStore.wrongTotalPages"
        :total-elements="exerciseStore.wrongTotalElements"
        :disabled="exerciseStore.wrongLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>
  </section>
</template>
