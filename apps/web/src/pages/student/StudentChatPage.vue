<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { useChatStore } from "../../stores/chat";

const chatStore = useChatStore();
const messageInput = ref("");
const showSidebar = ref(false);
const messageBoardRef = ref<HTMLElement | null>(null);

const chatError = computed(() => chatStore.sessionsError || chatStore.detailError);
const activeSessionTitle = computed(() => {
  const current = chatStore.sessions.find((item) => item.id === chatStore.activeSessionId);
  return current?.title || "新建对话";
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

async function removeSession(sessionId: string): Promise<void> {
  if (!window.confirm("确认删除该会话吗？")) {
    return;
  }
  await chatStore.removeSession(sessionId);
  if (chatStore.activeSessionId) {
    localStorage.setItem("student_active_session", chatStore.activeSessionId);
  } else {
    localStorage.removeItem("student_active_session");
  }
}

async function sendMessage(): Promise<void> {
  const content = messageInput.value.trim();
  if (!content || chatStore.sending) {
    return;
  }

  messageInput.value = "";
  try {
    await chatStore.sendMessage(content);
    await nextTick();
    scrollMessageBoardToBottom();
  } catch {
    messageInput.value = content;
  }
}

async function updatePage(page: number): Promise<void> {
  await chatStore.loadSessions({ page, size: chatStore.size });
}

async function updateSize(size: number): Promise<void> {
  await chatStore.loadSessions({ page: 1, size });
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
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">智能问答</h2>
        <p class="panel-note">支持会话管理、RAG 引用展示与会话归属隔离。</p>
      </div>
      <div class="list-item-actions">
        <button class="btn secondary" type="button" :disabled="chatStore.sessionsLoading" @click="loadSessions">刷新列表</button>
        <button class="btn" type="button" :disabled="chatStore.sessionsLoading || chatStore.sending" @click="createNewSession">新建会话</button>
        <button class="btn ghost mobile-only" type="button" aria-label="切换会话列表" @click="showSidebar = !showSidebar">会话列表</button>
      </div>
    </header>

    <p v-if="chatError" class="status-box error" role="alert">{{ chatError }}</p>

    <div class="chat-layout">
      <aside class="chat-sidebar" :class="{ open: showSidebar }" aria-label="会话列表">
        <div v-if="chatStore.sessionsLoading && !chatStore.sessionsLoaded" class="status-box info">正在加载会话列表...</div>
        <div v-else-if="chatStore.sessions.length === 0" class="status-box empty">暂无会话，点击“新建会话”开始提问。</div>
        <div v-else class="list-stack">
          <article v-for="session in chatStore.sessions" :key="session.id" class="session-item" :class="{ active: chatStore.activeSessionId === session.id }">
            <button class="session-trigger" type="button" @click="openSession(session.id || '')">
              {{ session.title || "新建对话" }}
            </button>
            <button class="btn danger small" type="button" aria-label="删除会话" @click="removeSession(session.id || '')">删除</button>
          </article>
          <PaginationBar
            :page="chatStore.page"
            :size="chatStore.size"
            :total-pages="chatStore.totalPages"
            :total-elements="chatStore.totalElements"
            :disabled="chatStore.sessionsLoading"
            @update:page="updatePage"
            @update:size="updateSize"
          />
        </div>
      </aside>

      <div class="chat-main">
        <header class="chat-main-head">
          <h3>{{ activeSessionTitle }}</h3>
          <p class="panel-note">回复包含引用时，会展示文档名、相似度与片段。</p>
        </header>

        <div ref="messageBoardRef" class="chat-message-board" aria-live="polite">
          <div v-if="chatStore.detailLoading && chatStore.messages.length === 0" class="status-box info">正在加载会话内容...</div>
          <div v-else-if="chatStore.messages.length === 0" class="status-box empty">输入问题后即可开始对话。</div>
          <article
            v-for="(message, index) in chatStore.messages"
            :key="`${message.id || 'msg'}-${index}`"
            class="chat-message"
            :class="message.role === 'USER' ? 'user' : 'assistant'"
          >
            <p class="chat-message-role">{{ message.role === "USER" ? "我" : "AI 助教" }}</p>
            <p class="chat-message-content">{{ message.content }}</p>
            <div v-if="message.citations && message.citations.length > 0" class="citation-list">
              <article v-for="(citation, citationIndex) in message.citations" :key="citationIndex" class="citation-item">
                <p class="citation-title">{{ citation.filename || "未知来源" }} · {{ Number(citation.score || 0).toFixed(2) }}</p>
                <p class="citation-snippet">{{ citation.content || "无引用片段" }}</p>
              </article>
            </div>
          </article>
        </div>

        <div class="chat-composer">
          <input
            v-model="messageInput"
            aria-label="输入提问内容"
            placeholder="输入问题，例如：牛顿第二定律在斜面题中如何应用？"
            @keyup.enter="sendMessage"
          />
          <button class="btn" type="button" :disabled="chatStore.sending" @click="sendMessage">
            {{ chatStore.sending ? "发送中..." : "发送" }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 12px;
}

.chat-sidebar,
.chat-main {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  padding: 12px;
}

.session-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #ffffff;
  padding: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.session-item.active {
  border-color: #86bbe8;
  background: #ecf6ff;
}

.session-trigger {
  background: transparent;
  border: 0;
  text-align: left;
  font: inherit;
  color: var(--color-text);
  font-weight: 600;
  flex: 1;
  min-height: 32px;
  cursor: pointer;
}

.chat-main-head h3 {
  margin: 0;
}

.chat-main-head {
  margin-bottom: 10px;
}

.chat-message-board {
  max-height: 480px;
  min-height: 280px;
  overflow: auto;
  display: grid;
  gap: 10px;
}

.chat-message {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #ffffff;
  padding: 10px 12px;
}

.chat-message.user {
  border-color: #b9d9f3;
  background: #eff7ff;
}

.chat-message-role {
  margin: 0;
  font-size: 0.82rem;
  color: #365b7e;
  font-weight: 700;
}

.chat-message-content {
  margin: 5px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.citation-list {
  margin-top: 8px;
  display: grid;
  gap: 7px;
}

.citation-item {
  border: 1px dashed #c8daeb;
  border-radius: var(--radius-sm);
  background: #f7fbff;
  padding: 8px;
}

.citation-title {
  margin: 0;
  color: #376089;
  font-size: 0.78rem;
  font-weight: 700;
}

.citation-snippet {
  margin: 5px 0 0;
  color: #496b8c;
  font-size: 0.8rem;
}

.chat-composer {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.mobile-only {
  display: none;
}

@media (max-width: 1279px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .chat-message-board {
    max-height: 400px;
  }
}

@media (max-width: 767px) {
  .mobile-only {
    display: inline-flex;
  }

  .chat-sidebar {
    display: none;
  }

  .chat-sidebar.open {
    display: block;
  }

  .chat-composer {
    grid-template-columns: 1fr;
  }
}
</style>
