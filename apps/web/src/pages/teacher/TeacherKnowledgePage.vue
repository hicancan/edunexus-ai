<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import {
  NForm,
  NFormItem,
  NSelect,
  NButton,
  NText,
  NAlert,
  NUpload,
  NEmpty,
  NSpin,
  useDialog,
  useMessage
} from "naive-ui";
import type { UploadFileInfo } from "naive-ui";
import { UploadCloud, RefreshCw, Trash2, FileText, SearchCheck } from "lucide-vue-next";
import type { DocumentStatus } from "../../services/contracts";
import { useClassroomStore } from "../../features/teacher-workspace/model/classroom";
import { useDocumentStore } from "../../features/teacher-workspace/model/documents";

const classroomStore = useClassroomStore();
const documentStore = useDocumentStore();
const dialog = useDialog();
const message = useMessage();

const statusFilter = ref<"" | DocumentStatus>("");
const selectedFiles = ref<UploadFileInfo[]>([]);
const selectedClassId = ref<string | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;

const classOptions = computed(() =>
  classroomStore.classrooms
    .filter((row) => Boolean(row.id))
    .map((row) => ({
      label: `${row.name || "未命名班级"} (${row.studentCount ?? 0}人)`,
      value: row.id as string
    }))
);

const statusOptions = [
  { label: "全部状态", value: "" },
  { label: "上传中 UPLOADING", value: "UPLOADING" },
  { label: "解析中 PARSING", value: "PARSING" },
  { label: "向量化中 EMBEDDING", value: "EMBEDDING" },
  { label: "就绪 READY", value: "READY" },
  { label: "失败 FAILED", value: "FAILED" }
];
const statusInputProps = {
  id: "knowledge-status-filter",
  name: "knowledgeStatusFilter",
  "aria-label": "文档状态筛选"
};
const classInputProps = {
  id: "knowledge-classroom",
  name: "knowledgeClassroom",
  "aria-label": "归属班级"
};
const uploadInputProps = {
  id: "knowledge-file-upload",
  name: "knowledgeFile",
  "aria-label": "选择知识库文档"
};

const hasPendingDocument = computed(() =>
  documentStore.documents.some((document) =>
    ["UPLOADING", "PARSING", "EMBEDDING"].includes(document.status || "")
  )
);
const selectedFile = computed(() => selectedFiles.value[0]?.file ?? null);
const selectedFileName = computed(() => selectedFiles.value[0]?.name || "");

function statusLabel(status: DocumentStatus): string {
  if (status === "READY") return "已就绪";
  if (status === "FAILED") return "处理失败";
  if (status === "UPLOADING") return "上传中";
  if (status === "PARSING") return "解析中";
  if (status === "EMBEDDING") return "向量化中";
  return "未知";
}

async function loadDocuments(): Promise<void> {
  await documentStore.loadDocuments(statusFilter.value || undefined);
}

function handleFileListUpdate(fileList: UploadFileInfo[]): void {
  selectedFiles.value = fileList.slice(-1);
}

async function uploadDocument(): Promise<void> {
  if (!selectedFile.value) {
    message.warning("请先选择文件");
    return;
  }
  if (!selectedClassId.value) {
    message.warning("请先选择班级");
    return;
  }

  await documentStore.uploadDocument(selectedFile.value, selectedClassId.value);
  if (documentStore.operationError) {
    message.error(documentStore.operationError);
    return;
  }
  selectedFiles.value = [];
  message.success("文档已上传，正在异步处理");
  await loadDocuments();
}

function confirmRemoveDocument(documentId: string): void {
  dialog.warning({
    title: "删除文档",
    content: "删除后将同时移除知识向量索引，且不可恢复。",
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      await documentStore.removeDocument(documentId);
      if (documentStore.operationError) {
        message.error(documentStore.operationError);
        return;
      }
      message.success("文档已删除");
      await loadDocuments();
    }
  });
}

function startPolling(): void {
  stopPolling();
  pollTimer = setInterval(() => {
    if (hasPendingDocument.value) {
      void loadDocuments();
    }
  }, 5000);
}

function stopPolling(): void {
  if (!pollTimer) return;
  clearInterval(pollTimer);
  pollTimer = null;
}

onMounted(async () => {
  await classroomStore.loadClassrooms();
  if (!selectedClassId.value && classroomStore.classrooms.length === 1) {
    selectedClassId.value = classroomStore.classrooms[0].id || null;
  }
  await loadDocuments();
  startPolling();
});

onUnmounted(stopPolling);
</script>

<template>
  <div class="knowledge-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">知识库管理</h1>
          <p class="workspace-subtitle">上传并管理班级知识文档，系统将自动完成解析与向量化。</p>
        </div>
      </div>

      <div class="panel glass-card search-panel">
        <n-form inline label-placement="left" :show-feedback="false" class="ethereal-form">
          <n-form-item label="文档状态">
            <n-select
              v-model:value="statusFilter"
              :options="statusOptions"
              :input-props="statusInputProps"
              style="width: 200px"
              @update:value="loadDocuments"
            />
          </n-form-item>
          <n-form-item>
            <n-button
              class="glass-pill"
              :loading="documentStore.documentsLoading"
              @click="loadDocuments"
            >
              <template #icon><RefreshCw :size="16" /></template>
              刷新
            </n-button>
          </n-form-item>
          <n-form-item label="归属班级">
            <n-select
              v-model:value="selectedClassId"
              :options="classOptions"
              :input-props="classInputProps"
              placeholder="请选择班级"
              style="width: 220px"
              :loading="classroomStore.classroomsLoading"
            />
          </n-form-item>
          <n-form-item>
            <n-upload
              accept=".pdf,.docx,.txt,.md"
              :default-upload="false"
              :show-file-list="false"
              :max="1"
              :file-list="selectedFiles"
              :input-props="uploadInputProps"
              @update:file-list="handleFileListUpdate"
            >
              <n-button :type="selectedFile ? 'warning' : 'primary'" ghost>
                <template #icon><UploadCloud :size="16" /></template>
                {{ selectedFile ? "重新选择" : "选择文件" }}
              </n-button>
            </n-upload>
          </n-form-item>
          <n-form-item v-if="selectedFile">
            <n-text>{{ selectedFileName }}</n-text>
          </n-form-item>
          <n-form-item>
            <n-button
              type="primary"
              :loading="documentStore.operationLoading"
              @click="uploadDocument"
              >上传</n-button
            >
          </n-form-item>
        </n-form>
      </div>

      <n-alert v-if="classroomStore.classroomsError" type="error" :show-icon="true">{{
        classroomStore.classroomsError
      }}</n-alert>
      <n-alert
        v-else-if="!classroomStore.classroomsLoading && classOptions.length === 0"
        type="warning"
        :show-icon="true"
      >
        暂无可用班级，请先完成师生绑定。
      </n-alert>
      <n-alert v-if="documentStore.documentsError" type="error" :show-icon="true">{{
        documentStore.documentsError
      }}</n-alert>
      <n-alert v-if="documentStore.operationError" type="error" :show-icon="true">{{
        documentStore.operationError
      }}</n-alert>

      <n-spin :show="documentStore.documentsLoading">
        <n-empty
          v-if="documentStore.documentsLoaded && documentStore.documents.length === 0"
          description="暂无文档"
          class="empty-layout"
        >
          <template #icon
            ><SearchCheck :size="48" style="color: var(--color-border-strong)"
          /></template>
        </n-empty>

        <div v-else class="doc-grid">
          <div
            v-for="doc in documentStore.documents"
            :key="doc.id"
            class="panel glass-card doc-card"
          >
            <div class="doc-header">
              <FileText :size="24" class="doc-icon" />
              <div class="doc-info">
                <h3 class="doc-title" :title="doc.filename">{{ doc.filename }}</h3>
                <span class="doc-id">{{ doc.id }}</span>
              </div>
            </div>

            <div class="doc-meta">
              <div class="meta-item">
                <span class="meta-label">大小</span>
                <span class="meta-value">{{ ((doc.fileSize || 0) / 1024).toFixed(1) }} KB</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">班级</span>
                <span class="meta-value">{{ doc.className || doc.classId || "未绑定" }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">状态</span>
                <span class="meta-value">{{ statusLabel(doc.status as DocumentStatus) }}</span>
              </div>
            </div>

            <div v-if="doc.status === 'FAILED'" class="doc-error">{{ doc.errorMessage }}</div>

            <div class="doc-actions">
              <n-button
                size="small"
                type="error"
                ghost
                @click="confirmRemoveDocument(doc.id || '')"
              >
                <template #icon><Trash2 :size="14" /></template>
                删除
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 16px 20px;
}

.empty-layout {
  padding: 80px 0;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
  margin-top: 12px;
}

.doc-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 1px solid var(--color-border-glass);
}

.doc-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.doc-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.doc-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.doc-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-id {
  font-family: var(--font-code);
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.doc-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(0, 0, 0, 0.02);
  padding: 10px 12px;
  border-radius: 8px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.meta-value {
  font-size: 0.85rem;
  font-weight: 600;
}

.doc-error {
  font-size: 0.8rem;
  color: var(--color-danger);
  background: rgba(239, 68, 68, 0.05);
  padding: 8px;
  border-radius: 6px;
}

.doc-actions {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border-glass);
}
</style>
