<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { NButton, NAlert, NSpace, useDialog, useMessage } from "naive-ui";
import { Plus, PanelLeftOpen } from "lucide-vue-next";
import ChatSessionList from "../../components/student/ChatSessionList.vue";
import ChatMessageList from "../../components/student/ChatMessageList.vue";
import ChatInputBar from "../../components/student/ChatInputBar.vue";
import { useChatStore } from "../../features/student/model/chat";

const chatStore = useChatStore();
const dialog = useDialog();
const message = useMessage();

const showSidebar = ref(false);
const messageBoardRef = ref<HTMLElement | null>(null);

const chatError = computed(() => chatStore.sessionsError || chatStore.detailError);
const activeSessionTitle = computed(() => {
  const current = chatStore.sessions.find((item) => item.id === chatStore.activeSessionId);
  return current?.title || "新建会话";
});

function scrollToBottom(): void {
  if (!messageBoardRef.value) return;
  messageBoardRef.value.scrollTop = messageBoardRef.value.scrollHeight;
}

async function loadSessions(): Promise<void> {
  await chatStore.loadSessions({ page: chatStore.page, size: chatStore.size });
}

async function createNewSession(): Promise<void> {
  await chatStore.createSession();
}

async function openSession(sessionId: string): Promise<void> {
  if (!sessionId) return;
  await chatStore.openSession(sessionId);
  showSidebar.value = false;
}

function confirmRemoveSession(sessionId: string): void {
  dialog.warning({
    title: "删除会话",
    content: "此操作不可恢复，确认删除该会话吗？",
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await chatStore.removeSession(sessionId);
        message.success("会话已删除");
      } catch {
        message.error("删除失败，请稍后重试");
      }
    }
  });
}

async function handleSend(content: string): Promise<void> {
  try {
    await chatStore.sendMessage(content);
    await nextTick();
    scrollToBottom();
  } catch {
    message.error(chatStore.detailError || "消息发送失败");
  }
}

watch(
  () => chatStore.messages.length,
  async () => {
    await nextTick();
    scrollToBottom();
  }
);

watch(
  () => {
    const last =
      chatStore.messages.length > 0 ? chatStore.messages[chatStore.messages.length - 1] : null;
    return last?.content;
  },
  async () => {
    await nextTick();
    scrollToBottom();
  }
);

onMounted(async () => {
  await loadSessions();

  let targetId = chatStore.activeSessionId;
  if (targetId) {
    await chatStore.openSession(targetId);
    if (chatStore.detailError && chatStore.detailError.includes("不存在")) {
      chatStore.clearActiveSession();
      targetId = "";
    }
  }

  if (!targetId) {
    if (chatStore.sessions.length > 0) {
      await openSession(chatStore.sessions[0].id || "");
    } else {
      await createNewSession();
    }
  } else {
    showSidebar.value = false;
  }
});
</script>

<template>
  <div class="chat-container app-container">
    <n-space vertical :size="24">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">智能问答</h1>
          <p class="workspace-subtitle">基于知识库检索与上下文记忆的智能问答对话。</p>
        </div>
        <div class="header-actions">
          <n-button
            class="glass-pill animate-pop"
            :disabled="chatStore.sessionsLoading"
            @click="loadSessions"
            >刷新会话</n-button
          >
          <n-button
            type="primary"
            class="animate-pop"
            :disabled="chatStore.sessionsLoading || chatStore.sending"
            @click="createNewSession"
          >
            <template #icon><Plus :size="16" /></template>
            新建会话
          </n-button>
          <n-button class="mobile-only glass-pill" @click="showSidebar = !showSidebar">
            <template #icon><PanelLeftOpen :size="16" /></template>
          </n-button>
        </div>
      </div>

      <n-alert
        v-if="chatError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ chatError }}</n-alert
      >

      <div class="chat-layout">
        <div class="chat-sidebar glass-card" :class="{ open: showSidebar }">
          <ChatSessionList
            @open="openSession"
            @remove="confirmRemoveSession"
            @page-change="(p) => chatStore.loadSessions({ page: p, size: chatStore.size })"
          />
        </div>

        <div class="chat-main glass-card">
          <div class="main-header">
            <h3 class="active-title">{{ activeSessionTitle }}</h3>
            <span style="font-size: 13px; color: var(--color-text-muted)"
              >输入问题后可获得基于知识库引用的回答与证据片段。</span
            >
          </div>

          <div ref="messageBoardRef" class="message-board">
            <ChatMessageList />
          </div>

          <ChatInputBar :sending="chatStore.sending" @send="handleSend" />
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

.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border-glass);
  background: rgba(255, 255, 255, 0.2);
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
    background: rgba(255, 255, 255, 0.9) !important;
  }

  .chat-sidebar.open {
    display: flex;
  }
}
</style>
