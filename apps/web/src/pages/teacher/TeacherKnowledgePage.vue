<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import type { DocumentStatus } from "../../services/contracts";
import { useTeacherStore } from "../../stores/teacher";

const teacherStore = useTeacherStore();

const statusFilter = ref<"" | DocumentStatus>("");
const selectedFile = ref<File | null>(null);
const localError = ref("");

let pollTimer: ReturnType<typeof setInterval> | null = null;

const hasPendingDocument = computed(() =>
  teacherStore.documents.some((document) =>
    ["UPLOADING", "PARSING", "EMBEDDING"].includes(document.status || "")
  )
);

function onFileChange(event: Event): void {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
}

function statusClass(status?: DocumentStatus): string {
  if (status === "READY") {
    return "status-ready";
  }
  if (status === "FAILED") {
    return "status-failed";
  }
  return "status-pending";
}

async function loadDocuments(): Promise<void> {
  await teacherStore.loadDocuments(statusFilter.value || undefined);
}

async function uploadDocument(): Promise<void> {
  localError.value = "";
  if (!selectedFile.value) {
    localError.value = "请先选择 PDF 或 Docx 文件";
    return;
  }
  await teacherStore.uploadDocument(selectedFile.value);
  selectedFile.value = null;
  await loadDocuments();
}

async function removeDocument(documentId: string): Promise<void> {
  if (!documentId || !window.confirm("确认删除该文档吗？删除后向量索引会同步移除。")) {
    return;
  }
  await teacherStore.removeDocument(documentId);
  await loadDocuments();
}

function startPolling(): void {
  stopPolling();
  pollTimer = setInterval(() => {
    if (hasPendingDocument.value) {
      loadDocuments();
    }
  }, 5000);
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

onMounted(async () => {
  await loadDocuments();
  startPolling();
});

onUnmounted(stopPolling);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">知识库文档管理</h2>
        <p class="panel-note">支持上传 PDF/Docx，展示处理状态机：UPLOADING -> PARSING -> EMBEDDING -> READY。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="teacherStore.documentsLoading" @click="loadDocuments">
        {{ teacherStore.documentsLoading ? "加载中..." : "刷新状态" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="knowledge-status">状态筛选</label>
        <select id="knowledge-status" v-model="statusFilter">
          <option value="">全部</option>
          <option value="UPLOADING">UPLOADING</option>
          <option value="PARSING">PARSING</option>
          <option value="EMBEDDING">EMBEDDING</option>
          <option value="READY">READY</option>
          <option value="FAILED">FAILED</option>
        </select>
      </div>
      <div class="field-block">
        <label for="knowledge-file">上传文档</label>
        <input id="knowledge-file" accept=".pdf,.doc,.docx" type="file" @change="onFileChange" />
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn" type="button" :disabled="teacherStore.documentsLoading" @click="loadDocuments">按条件查询</button>
      <button class="btn success" type="button" :disabled="teacherStore.operationLoading" @click="uploadDocument">
        {{ teacherStore.operationLoading ? "上传中..." : "上传并处理" }}
      </button>
    </div>

    <p v-if="teacherStore.documentsError" class="status-box error" role="alert">{{ teacherStore.documentsError }}</p>
    <p v-if="localError" class="status-box error" role="alert">{{ localError }}</p>
    <p v-if="teacherStore.operationError" class="status-box error" role="alert">{{ teacherStore.operationError }}</p>

    <div v-if="teacherStore.documentsLoading && !teacherStore.documentsLoaded" class="status-box info">正在加载文档...</div>
    <div v-else-if="teacherStore.documents.length === 0" class="status-box empty">暂无知识文档。</div>
    <div v-else class="list-stack">
      <article v-for="document in teacherStore.documents" :key="document.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ document.filename }}</p>
          <p class="list-item-meta">
            文档 ID：{{ document.id }} · 类型：{{ document.fileType }} · 大小：{{ document.fileSize }} bytes · 更新时间：{{ document.updatedAt }}
          </p>
          <p v-if="document.errorMessage" class="status-box error" style="margin-top: 8px;">{{ document.errorMessage }}</p>
        </div>
        <div class="list-item-actions">
          <span class="pill" :class="statusClass(document.status)">{{ document.status }}</span>
          <button class="btn danger small" type="button" aria-label="删除知识文档" @click="removeDocument(document.id || '')">删除</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.status-ready {
  border-color: #bcecc6;
  background: #ebf9ef;
  color: #2a7b3e;
}

.status-failed {
  border-color: #f4bfd0;
  background: #fff1f6;
  color: #ba3b60;
}

.status-pending {
  border-color: #f2deaa;
  background: #fff8e8;
  color: #bf721c;
}
</style>
