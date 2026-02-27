<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  NCard,
  NForm,
  NFormItem,
  NSelect,
  NButton,
  NSpace,
  NText,
  NAlert,
  NTag,
  NUpload,
  NEmpty,
  NSpin,
  useDialog,
  useMessage
} from "naive-ui";
import { UploadCloud, RefreshCw, Trash2, FileText, Database, Plus, SearchCheck, CheckCircle2, ArrowRight } from "lucide-vue-next";
import type { DocumentVO, DocumentStatus } from "../../services/contracts";
import { useTeacherStore } from "../../features/teacher-workspace/model/teacher";

const teacherStore = useTeacherStore();
const router = useRouter();
const dialog = useDialog();
const message = useMessage();

const statusFilter = ref<"" | DocumentStatus>("");
const selectedFile = ref<File | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;

const statusOptions = [
  { label: "全息视野 (全部状态)", value: "" },
  { label: "上传中 UPLOADING", value: "UPLOADING" },
  { label: "解析中 PARSING", value: "PARSING" },
  { label: "高维嵌入 EMBEDDING", value: "EMBEDDING" },
  { label: "就绪 READY", value: "READY" },
  { label: "坍缩 FAILED", value: "FAILED" }
];

const hasPendingDocument = computed(() =>
  teacherStore.documents.some((document) =>
    ["UPLOADING", "PARSING", "EMBEDDING"].includes(document.status || "")
  )
);

function getStatusComponentProps(status: DocumentStatus): { type: "default" | "error" | "info" | "success" | "warning"; label: string; class: string } {
  switch(status) {
    case "READY": return { type: "success", label: "已就绪", class: "status-ready" };
    case "FAILED": return { type: "error", label: "解析坍缩", class: "status-failed" };
    case "UPLOADING": return { type: "info", label: "上传跃迁中", class: "status-pending" };
    case "PARSING": return { type: "warning", label: "拓扑解析中", class: "status-pending" };
    case "EMBEDDING": return { type: "info", label: "高维向量化", class: "status-pending" };
    default: return { type: "default", label: "未知态", class: "" };
  }
}

async function loadDocuments(): Promise<void> {
  await teacherStore.loadDocuments(statusFilter.value || undefined);
}

async function handleFileChange(options: { fileList: any[] }): Promise<void> {
  if (options.fileList.length > 0) {
    selectedFile.value = options.fileList[0].file;
  } else {
    selectedFile.value = null;
  }
}

async function uploadDocument(): Promise<void> {
  if (!selectedFile.value) {
    message.warning("请先锚定 PDF 或 Docx 源文件");
    return;
  }
  
  try {
    await teacherStore.uploadDocument(selectedFile.value);
    selectedFile.value = null;
    message.success("源生数据已注入知识穹顶，开始异步解析");
    await loadDocuments();
  } catch (error) {
    message.error("注入操作遭遇干涉失败");
  }
}

function confirmRemoveDocument(documentId: string): void {
  dialog.warning({
    title: "抹除高维索引",
    content: "此操作将粉碎底层空间的向量索引，该破坏不可逆转是否执行？",
    positiveText: "确认湮灭",
    negativeText: "撤销指令",
    onPositiveClick: async () => {
      try {
        await teacherStore.removeDocument(documentId);
        teacherStore.removeFromCart(documentId);
        message.success("知识碎片已从穹顶彻底抹除");
        await loadDocuments();
      } catch (error) {
        message.error("抹除协议失效");
      }
    }
  });
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

function isInCart(docId: string): boolean {
  return teacherStore.examCart.some(item => item.id === docId);
}

function toggleCart(doc: DocumentVO): void {
  if (isInCart(doc.id || '')) {
    teacherStore.removeFromCart(doc.id || '');
    message.info(`已将 [${doc.filename}] 移出组阵池`);
  } else {
    teacherStore.addToCart(doc);
    message.success(`已将 [${doc.filename}] 锁定至组阵池`);
  }
}

function navigateToAssembly(): void {
  if (teacherStore.examCart.length === 0) {
    message.warning("组阵池空载，请先从知识穹顶汲取源文件");
    return;
  }
  router.push('/teacher/plans');
}

onMounted(async () => {
  await loadDocuments();
  startPolling();
});

onUnmounted(stopPolling);
</script>

<template>
  <div class="knowledge-page app-container">
    <div class="workspace-stack with-sidebar">
      <div class="workspace-main">
        <div class="workspace-header">
          <div>
            <h1 class="workspace-title">知识穹顶管理库</h1>
            <p class="workspace-subtitle">构建 AI 专属向量网，勾选核心知识碎片加入组阵池，一键生成多元试卷。</p>
          </div>
        </div>

        <div class="panel glass-card search-panel">
          <div class="top-ops-row">
            <n-form inline label-placement="left" :show-feedback="false" class="ethereal-form">
               <n-form-item label="监控阵列状态">
                 <n-select v-model:value="statusFilter" :options="statusOptions" style="width: 220px" @update:value="loadDocuments" />
               </n-form-item>
               <n-form-item>
                 <n-button class="animate-pop glass-pill-btn" @click="loadDocuments" :loading="teacherStore.documentsLoading">
                   <template #icon><RefreshCw :size="16" /></template>
                   重同步
                 </n-button>
               </n-form-item>
            </n-form>
            
            <div class="upload-cluster">
               <n-upload
                  accept=".pdf,.doc,.docx"
                  :default-upload="false"
                  :show-file-list="false"
                  @change="handleFileChange"
               >
                  <n-button :type="selectedFile ? 'warning' : 'primary'" ghost class="animate-pop upload-trigger-btn">
                    <template #icon><UploadCloud :size="16" /></template>
                    {{ selectedFile ? '重选上传源' : '接入 PDF/Docx 源' }}
                  </n-button>
               </n-upload>
               
               <n-text v-if="selectedFile" class="file-name-truncated">
                  {{ selectedFile.name }}
               </n-text>
               
               <n-button
                  v-if="selectedFile"
                  type="primary"
                  class="animate-pop submit-btn"
                  :loading="teacherStore.operationLoading"
                  @click="uploadDocument"
               >
                 <template #icon><Database :size="16" /></template>
                 注入向量场
               </n-button>
            </div>
          </div>
        </div>

        <n-alert v-if="teacherStore.documentsError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ teacherStore.documentsError }}</n-alert>
        <n-alert v-if="teacherStore.operationError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ teacherStore.operationError }}</n-alert>

        <n-spin :show="teacherStore.documentsLoading">
          <n-empty v-if="teacherStore.documentsLoaded && teacherStore.documents.length === 0" description="穹顶知识库为空，等待首源数据注入" class="empty-layout">
             <template #icon><SearchCheck :size="48" style="color: var(--color-border-strong)"/></template>
          </n-empty>

          <div v-else class="doc-grid">
             <div 
               v-for="doc in teacherStore.documents" 
               :key="doc.id"
               class="panel glass-card doc-card"
               :class="{ 'in-cart': isInCart(doc.id || '') }"
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
                      <span class="meta-label">质量</span>
                      <span class="meta-value">{{ ((doc.fileSize || 0) / 1024).toFixed(1) }} KB</span>
                   </div>
                   <div class="meta-item">
                      <span class="meta-label">时空刻印</span>
                      <span class="meta-value">{{ doc.updatedAt?.split(' ')[0] }}</span>
                   </div>
                   <div class="meta-item status-col">
                      <span class="glass-pill" :class="getStatusComponentProps(doc.status as DocumentStatus).class">
                         {{ getStatusComponentProps(doc.status as DocumentStatus).label }}
                      </span>
                   </div>
                </div>

                <div class="doc-error" v-if="doc.status === 'FAILED'">
                   {{ doc.errorMessage }}
                </div>

                <div class="doc-actions">
                   <n-button 
                     size="small" 
                     :type="isInCart(doc.id || '') ? 'success' : 'primary'"
                     :ghost="!isInCart(doc.id || '')"
                     class="action-btn animate-pop"
                     :disabled="doc.status !== 'READY'"
                     @click="toggleCart(doc)"
                   >
                     <template #icon>
                       <component :is="isInCart(doc.id || '') ? CheckCircle2 : Plus" :size="16" />
                     </template>
                     {{ isInCart(doc.id || '') ? '已锚定至组阵池' : '拉入组阵池' }}
                   </n-button>
                   
                   <n-button 
                     size="small" 
                     type="error" 
                     quaternary 
                     circle
                     class="delete-btn"
                     @click="confirmRemoveDocument(doc.id || '')"
                   >
                     <template #icon><Trash2 :size="16" /></template>
                   </n-button>
                </div>
             </div>
          </div>
        </n-spin>
      </div>

      <!-- Right Sidebar: The Exam Cart -->
      <div class="workspace-sidebar">
         <div class="cart-panel glass-card panel sticky-top">
            <div class="cart-header">
               <div class="cart-title-row">
                 <Database :size="20" class="cart-icon" />
                 <h3>高维组阵池</h3>
               </div>
               <span class="cart-count">{{ teacherStore.examCart.length }} 源</span>
            </div>
            
            <div class="cart-body">
               <n-empty v-if="teacherStore.examCart.length === 0" description="阵列空置，请从左侧穹顶拉取所需文档..." style="margin-top: 40px" />
               
               <div v-else class="cart-items">
                  <div class="cart-item animate-pop" v-for="item in teacherStore.examCart" :key="item.id">
                     <FileText :size="16" class="item-icon" />
                     <span class="item-name" :title="item.filename">{{ item.filename }}</span>
                     <button class="item-remove" @click="teacherStore.removeFromCart(item.id || '')">
                        <Trash2 :size="14" />
                     </button>
                  </div>
               </div>
            </div>
            
            <div class="cart-footer">
               <n-button 
                 type="primary" 
                 class="assembly-btn animate-pop hover-glow" 
                 block 
                 size="large"
                 :disabled="teacherStore.examCart.length === 0"
                 @click="navigateToAssembly"
               >
                  发起共鸣生成试卷
                  <template #icon>
                    <ArrowRight :size="18" />
                  </template>
               </n-button>
               <n-button 
                 v-if="teacherStore.examCart.length > 0"
                 quaternary 
                 size="small" 
                 type="error" 
                 block 
                 style="margin-top: 12px"
                 @click="teacherStore.clearCart()"
               >
                  清空阵列
               </n-button>
            </div>
         </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.with-sidebar {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: var(--space-6);
  align-items: start;
}

.workspace-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.search-panel {
  padding: 16px 20px;
}

.top-ops-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.upload-cluster {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-trigger-btn {
  border-radius: 20px;
}

.file-name-truncated {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.9rem;
  color: var(--color-text-main);
  background: rgba(255,255,255,0.4);
  padding: 4px 12px;
  border-radius: 16px;
}

.submit-btn {
  border-radius: 20px;
  box-shadow: var(--shadow-glow);
}

.empty-layout {
  padding: 80px 0;
}

/* Document Grid */
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
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
  border: 1px solid var(--color-border-glass);
}

.doc-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-float);
  border-color: rgba(92, 101, 246, 0.3);
}

.doc-card.in-cart {
  background: linear-gradient(135deg, rgba(255,255,255,0.7), rgba(16, 185, 129, 0.05));
  border-color: rgba(16, 185, 129, 0.4);
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
  color: var(--color-text-main);
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
  background: rgba(0,0,0,0.02);
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
  color: var(--color-text-main);
}

.status-col {
  align-items: flex-end;
}

.status-ready { background: rgba(16,185,129,0.15); color: #059669; }
.status-failed { background: rgba(239,68,68,0.15); color: #dc2626; }
.status-pending { background: rgba(92,101,246,0.15); color: #4f46e5; animation: pulse-opacity 2s infinite; }

@keyframes pulse-opacity {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}

.doc-error {
  font-size: 0.8rem;
  color: var(--color-danger);
  background: rgba(239,68,68,0.05);
  padding: 8px;
  border-radius: 6px;
}

.doc-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border-glass);
}

.action-btn {
  border-radius: 16px;
  flex: 1;
  margin-right: 12px;
}

.delete-btn {
  color: var(--color-text-muted);
}
.delete-btn:hover {
  color: var(--color-danger);
}

/* Cart Panel */
.sticky-top {
  position: sticky;
  top: var(--space-6);
  height: calc(100vh - var(--space-6) * 2);
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.cart-header {
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(92, 101, 246, 0.05);
  border-bottom: 1px solid var(--color-border-glass);
}

.cart-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cart-icon {
  color: var(--color-primary);
}

.cart-title-row h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--color-primary);
}

.cart-count {
  background: var(--color-primary);
  color: white;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 700;
}

.cart-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.cart-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cart-item {
  display: flex;
  align-items: center;
  background: rgba(255,255,255,0.7);
  border: 1px solid var(--color-border-glass);
  padding: 12px 16px;
  border-radius: 12px;
  gap: 12px;
}

.item-icon {
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.item-name {
  flex: 1;
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--color-text-main);
}

.item-remove {
  background: transparent;
  border: none;
  color: var(--color-danger);
  opacity: 0.5;
  cursor: pointer;
  transition: opacity 0.2s;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-remove:hover {
  opacity: 1;
}

.cart-footer {
  padding: 20px 24px;
  border-top: 1px solid var(--color-border-glass);
  background: rgba(255,255,255,0.4);
}

.assembly-btn {
  height: 48px;
  font-size: 1.05rem;
  font-weight: 700;
  border-radius: 24px;
  display: flex;
  justify-content: space-between;
}

.assembly-btn :deep(.n-button__content) {
  width: 100%;
  display: flex;
  justify-content: space-between;
}

@media (max-width: 1024px) {
  .with-sidebar {
    grid-template-columns: 1fr;
  }
  
  .sticky-top {
    position: static;
    height: auto;
    min-height: 400px;
  }
}
</style>
