<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import {
  NCard,
  NButton,
  NInput,
  NAlert,
  NEmpty,
  NPagination,
  NSpin,
  NSpace,
  NText,
  NDivider,
  useDialog,
  useMessage,
  NUpload,
  NUploadTrigger,
  NImage,
  NTooltip
} from "naive-ui";
import { Plus, Trash2, MessageSquare, PanelLeftOpen, Image as ImageIcon, SendHorizontal, Bot, User, FileText } from "lucide-vue-next";
import MarkdownPreview from "../../components/common/MarkdownPreview.vue";
import { useChatStore } from "../../features/student/model/chat";

const chatStore = useChatStore();
const dialog = useDialog();
const message = useMessage();

const messageInput = ref("");
const showSidebar = ref(false);
const messageBoardRef = ref<HTMLElement | null>(null);
const uploadedImages = ref<string[]>([]);

const chatError = computed(() => chatStore.sessionsError || chatStore.detailError);
const activeSessionTitle = computed(() => {
  const current = chatStore.sessions.find((item) => item.id === chatStore.activeSessionId);
  return current?.title || "新建脑暴会话";
});

function scrollMessageBoardToBottom(): void {
  if (!messageBoardRef.value) {
    return;
  }
  messageBoardRef.value.scrollTop = messageBoardRef.value.scrollHeight;
}

async function loadSessions(): Promise<void> {
  await chatStore.loadSessions({ page: chatStore.page, size: chatStore.size });
}

async function createNewSession(): Promise<void> {
  const sessionId = await chatStore.createSession();
  localStorage.setItem("student_active_session", sessionId);
}

async function openSession(sessionId: string): Promise<void> {
  if (!sessionId) {
    return;
  }
  await chatStore.openSession(sessionId);
  localStorage.setItem("student_active_session", sessionId);
  showSidebar.value = false;
}

function confirmRemoveSession(sessionId: string): void {
  dialog.warning({
    title: "销毁会话",
    content: "此操作不可逆。确认切断并粉碎该会话的时空羁绊吗？",
    positiveText: "执行销毁",
    negativeText: "保留",
    onPositiveClick: async () => {
      try {
        await chatStore.removeSession(sessionId);
        if (chatStore.activeSessionId) {
          localStorage.setItem("student_active_session", chatStore.activeSessionId);
        } else {
          localStorage.removeItem("student_active_session");
        }
        message.success("会话已从本地连续体中擦除");
      } catch {
        message.error("由于维度震荡，销毁失败");
      }
    }
  });
}

function handleImageUpload(options: { fileList: any[] }): void {
  uploadedImages.value = [];
  for (const item of options.fileList) {
    const file = item.file;
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        if (e.target?.result && typeof e.target.result === "string") {
          uploadedImages.value.push(e.target.result);
        }
      };
      reader.readAsDataURL(file);
    }
  }
}

function removeImage(index: number): void {
  uploadedImages.value.splice(index, 1);
}

async function sendMessage(): Promise<void> {
  const content = messageInput.value.trim();
  const hasImages = uploadedImages.value.length > 0;
  
  if ((!content && !hasImages) || chatStore.sending) {
    return;
  }

  // Construct message payload. In the actual backend, you'd send images in Base64 or IDs.
  // For the frontend simulation of multimodel input, we bundle it.
  const payload = hasImages 
    ? `[多模态载荷附带 ${uploadedImages.value.length} 张图片]\n${content}`
    : content;

  messageInput.value = "";
  uploadedImages.value = [];
  
  try {
    await chatStore.sendMessage(payload);
    await nextTick();
    scrollMessageBoardToBottom();
  } catch {
    messageInput.value = content;
    message.error("跨模态传输失败");
  }
}

async function updatePage(page: number): Promise<void> {
  await chatStore.loadSessions({ page, size: chatStore.size });
}

watch(
  () => chatStore.messages.length,
  async () => {
    await nextTick();
    scrollMessageBoardToBottom();
  }
);

onMounted(async () => {
  await loadSessions();
  const cachedSession = localStorage.getItem("student_active_session");
  if (cachedSession) {
    await openSession(cachedSession);
    return;
  }

  if (chatStore.activeSessionId) {
    await openSession(chatStore.activeSessionId);
  }
});
</script>

<template>
  <div class="chat-container app-container">
    <n-space vertical :size="24">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">Hyper-AI 智能伴读舱</h1>
          <p class="workspace-subtitle">搭载 Qwen3-VL 的多模态知识网格，支持跨媒体的 RAG 引用与推演。</p>
        </div>
        <div class="header-actions">
          <n-button class="glass-pill animate-pop" :disabled="chatStore.sessionsLoading" @click="loadSessions">刷新链路</n-button>
          <n-button type="primary" class="animate-pop" :disabled="chatStore.sessionsLoading || chatStore.sending" @click="createNewSession">
            <template #icon>
              <Plus :size="16" />
            </template>
            唤醒新会话
          </n-button>
          <n-button class="mobile-only glass-pill" @click="showSidebar = !showSidebar">
            <template #icon>
              <PanelLeftOpen :size="16" />
            </template>
          </n-button>
        </div>
      </div>

      <n-alert v-if="chatError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ chatError }}</n-alert>

      <div class="chat-layout">
        <!-- Sidebar -->
        <div class="chat-sidebar glass-card" :class="{ open: showSidebar }">
          <div class="sidebar-inner">
            <div v-if="chatStore.sessionsLoading && !chatStore.sessionsLoaded" class="loading-wrapper">
              <n-spin size="small" />
              <n-text depth="3">正在同步云端记忆...</n-text>
            </div>
            <n-empty v-else-if="chatStore.sessions.length === 0" description="未发现历史干涉点" style="margin-top: 40px" />
            
            <div v-else class="session-list">
              <div
                v-for="session in chatStore.sessions"
                :key="session.id"
                class="session-item"
                :class="{ active: chatStore.activeSessionId === session.id }"
                @click="openSession(session.id || '')"
              >
                <div class="session-info">
                  <MessageSquare :size="16" class="session-icon" />
                  <n-text class="session-title" :depth="chatStore.activeSessionId === session.id ? 1 : 2">
                    {{ session.title || "新建脑暴会话" }}
                  </n-text>
                </div>
                <n-button
                  quaternary
                  circle
                  type="error"
                  size="small"
                  class="delete-btn"
                  @click.stop="confirmRemoveSession(session.id || '')"
                >
                  <template #icon>
                    <Trash2 :size="14" />
                  </template>
                </n-button>
              </div>
            </div>

            <div class="pagination-wrapper">
              <n-pagination
                v-model:page="chatStore.page"
                :page-count="chatStore.totalPages"
                :disabled="chatStore.sessionsLoading"
                size="small"
                @update:page="updatePage"
              />
            </div>
          </div>
        </div>

        <!-- Main Chat Area -->
        <div class="chat-main glass-card">
          <div class="main-header">
            <h3 class="active-title">{{ activeSessionTitle }}</h3>
            <n-text depth="3" style="font-size: 13px">启用 Qwen3-VL 多模态引擎，上传图片获取跨维度视角解析。</n-text>
          </div>

          <div ref="messageBoardRef" class="message-board">
            <div v-if="chatStore.detailLoading && chatStore.messages.length === 0" class="loading-wrapper">
               <n-spin size="medium" />
            </div>
            <n-empty v-else-if="chatStore.messages.length === 0" description="开启跨模态对话..." style="margin-top: 60px" />
            
            <div class="message-list" v-else>
              <div
                v-for="(message, index) in chatStore.messages"
                :key="`${message.id || 'msg'}-${index}`"
                class="message-bubble-wrapper"
                :class="message.role === 'USER' ? 'user' : 'assistant'"
              >
                <div class="avatar-box" v-if="message.role !== 'USER'">
                   <Bot :size="20" class="bot-avatar" />
                </div>
                
                <div class="message-bubble">
                  <n-text depth="3" class="message-role">{{ message.role === "USER" ? "我" : "Nexus Agent" }}</n-text>
                  <div class="message-content-box glass-card">
                    <MarkdownPreview v-if="message.role !== 'USER'" :content="message.content || ''" />
                    <n-text v-else class="user-content">{{ message.content }}</n-text>
                  </div>
                  
                  <div v-if="message.citations && message.citations.length > 0" class="citation-container">
                    <div v-for="(citation, citationIndex) in message.citations" :key="citationIndex" class="citation-card glass-pill">
                      <FileText :size="14" class="citation-icon" />
                      <div class="citation-body">
                         <n-text strong class="citation-title">{{ citation.filename || "未知知识碎块" }} <span class="citation-score">匹配度 {{ Number(citation.score || 0).toFixed(2) }}</span></n-text>
                         <n-text depth="3" class="citation-snippet">{{ citation.content || "无摘录碎片" }}</n-text>
                      </div>
                    </div>
                  </div>
                </div>
                
                <div class="avatar-box user-avatar-box" v-if="message.role === 'USER'">
                   <User :size="20" class="user-avatar" />
                </div>
              </div>
            </div>
          </div>

          <!-- Multimodal Input Area -->
          <div class="chat-input-area glass-card">
            <!-- Thumbnail Preview -->
            <div class="image-preview-tray" v-if="uploadedImages.length > 0">
               <div class="preview-item" v-for="(imgSrc, idx) in uploadedImages" :key="idx">
                 <n-image :src="imgSrc" object-fit="cover" class="preview-img" />
                 <n-button circle size="tiny" type="error" class="remove-img-btn" @click="removeImage(idx)">
                   <template #icon><Trash2 :size="10" /></template>
                 </n-button>
               </div>
            </div>
            
            <div class="input-box-wrapper">
               <div class="multimodal-actions">
                 <NUpload
                   abstract
                   accept="image/png, image/jpeg, image/webp"
                   :default-upload="false"
                   :show-file-list="false"
                   multiple
                   @change="handleImageUpload"
                 >
                   <NUploadTrigger #="{ handleClick }" abstract>
                      <n-tooltip placement="top">
                        <template #trigger>
                          <n-button quaternary circle class="upload-btn animate-pop" @click="handleClick">
                            <template #icon><ImageIcon :size="20" style="color: var(--color-primary)" /></template>
                          </n-button>
                        </template>
                        投喂图像载荷 (Qwen3-VL)
                      </n-tooltip>
                   </NUploadTrigger>
                 </NUpload>
               </div>
               
               <n-input
                 v-model:value="messageInput"
                 type="textarea"
                 class="main-chat-input"
                 placeholder="询问结构化知识，或者投喂图片进行 OCR 推理... (回车发送)"
                 :autosize="{ minRows: 1, maxRows: 6 }"
                 :bordered="false"
                 @keydown.enter.prevent="sendMessage"
               />
               
               <n-button type="primary" circle class="send-btn animate-pop" :loading="chatStore.sending" @click="sendMessage">
                 <template #icon><SendHorizontal :size="16" /></template>
               </n-button>
            </div>
          </div>
        </div>
      </div>
    </n-space>
  </div>
</template>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--space-4);
  height: calc(100vh - 220px);
  min-height: 500px;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.loading-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  gap: 8px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-smooth);
  margin-bottom: 6px;
  border: 1px solid transparent;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.4);
}

.session-item.active {
  background: rgba(255, 255, 255, 0.9);
  border-color: rgba(92, 101, 246, 0.2);
  box-shadow: var(--shadow-glass);
}

.session-item.active .session-title {
  color: var(--color-primary);
  font-weight: 700;
}

.session-item.active .session-icon {
  color: var(--color-primary);
}

.session-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  overflow: hidden;
}

.session-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.9rem;
}

.session-icon {
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.pagination-wrapper {
  padding: 12px;
  border-top: 1px solid var(--color-border-glass);
  display: flex;
  justify-content: center;
  background: rgba(255,255,255,0.3);
}

.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border-glass);
  background: rgba(255,255,255,0.2);
}

.active-title {
  margin: 0;
  font-size: 1.15rem;
  font-family: var(--font-title);
  color: var(--color-text-main);
}

.message-board {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
  scroll-behavior: smooth;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.message-bubble-wrapper {
  display: flex;
  width: 100%;
  gap: 12px;
}

.message-bubble-wrapper.user {
  justify-content: flex-end;
}

.avatar-box {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bot-avatar {
  color: #fff;
}
.message-bubble-wrapper.assistant .avatar-box {
  background: linear-gradient(135deg, var(--color-primary), #60a5fa);
  box-shadow: var(--shadow-glow);
}

.user-avatar-box {
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
}
.user-avatar {
  color: #475569;
}

.message-bubble {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.message-role {
  font-size: 0.75rem;
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.message-bubble-wrapper.user .message-role {
  text-align: right;
}

.message-content-box {
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-glass);
}

.message-bubble-wrapper.user .message-content-box {
  background: linear-gradient(135deg, hsl(237, 90%, 96%), #ffffff);
  border-color: rgba(92, 101, 246, 0.2);
  border-top-right-radius: 2px;
}

.message-bubble-wrapper.assistant .message-content-box {
  border-top-left-radius: 2px;
}

.user-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.citation-container {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.citation-card {
  padding: 10px 14px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.4);
}

.citation-icon {
  margin-top: 2px;
  color: var(--color-primary);
}

.citation-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.citation-title {
  color: var(--color-text-main);
  font-size: 0.8rem;
}

.citation-score {
  font-size: 0.7rem;
  color: #fff;
  background: var(--color-primary);
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 6px;
}

.citation-snippet {
  font-size: 0.75rem;
  line-height: 1.5;
}

/* Multimodal Input */
.chat-input-area {
  margin: var(--space-4);
  padding: 6px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 24px;
  border: 1px solid var(--color-border-glass);
  box-shadow: var(--shadow-glass);
}

.image-preview-tray {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.preview-item {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.preview-img {
  width: 100%;
  height: 100%;
}

.remove-img-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(239, 68, 68, 0.9) !important;
}

.input-box-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 10px;
}

.multimodal-actions {
  display: flex;
  padding-bottom: 2px;
}

.upload-btn {
  background: #f1f5f9;
}

.main-chat-input {
  flex: 1;
  background: transparent;
  font-size: 0.95rem;
}

:deep(.main-chat-input textarea) {
  padding: 8px 0;
}

.send-btn {
  box-shadow: var(--shadow-glow);
  margin-bottom: 2px;
}

.mobile-only {
  display: none;
}

@media (max-width: 1024px) {
  .chat-layout {
    grid-template-columns: 240px minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .mobile-only {
    display: inline-flex;
  }

  .chat-layout {
    grid-template-columns: 1fr;
    height: calc(100vh - 160px);
  }

  .chat-sidebar {
    display: none;
    position: absolute;
    z-index: 10;
    width: 280px;
    height: calc(100vh - 160px);
    backdrop-filter: blur(24px) !important;
    background: rgba(255,255,255,0.9) !important;
  }

  .chat-sidebar.open {
    display: flex;
  }
}
</style>
