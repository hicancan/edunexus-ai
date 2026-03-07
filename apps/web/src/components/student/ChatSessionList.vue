<script setup lang="ts">
import { NPagination, NSpin, NEmpty, NButton, NText } from "naive-ui";
import { MessageSquare, Trash2 } from "lucide-vue-next";
import { useChatStore } from "../../features/student/model/chat";

const chatStore = useChatStore();

const emit = defineEmits<{
  (e: "open", sessionId: string): void;
  (e: "remove", sessionId: string): void;
  (e: "pageChange", page: number): void;
}>();
</script>

<template>
  <div class="session-list-inner">
    <div v-if="chatStore.sessionsLoading && !chatStore.sessionsLoaded" class="loading-wrapper">
      <n-spin size="small" />
      <n-text depth="3">正在加载会话...</n-text>
    </div>
    <n-empty
      v-else-if="chatStore.sessions.length === 0"
      description="暂无历史会话"
      style="margin-top: 40px"
    />

    <div v-else class="session-list">
      <div
        v-for="session in chatStore.sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: chatStore.activeSessionId === session.id }"
        @click="emit('open', session.id || '')"
      >
        <div class="session-info">
          <MessageSquare :size="16" class="session-icon" />
          <n-text class="session-title" :depth="chatStore.activeSessionId === session.id ? 1 : 2">
            {{ session.title || "新建会话" }}
          </n-text>
        </div>
        <n-button
          quaternary
          circle
          type="error"
          size="small"
          class="delete-btn"
          @click.stop="emit('remove', session.id || '')"
        >
          <template #icon><Trash2 :size="14" /></template>
        </n-button>
      </div>
    </div>

    <div class="pagination-wrapper">
      <n-pagination
        v-model:page="chatStore.page"
        :page-count="chatStore.totalPages"
        :disabled="chatStore.sessionsLoading"
        size="small"
        @update:page="(p) => emit('pageChange', p)"
      />
    </div>
  </div>
</template>

<style scoped>
.session-list-inner {
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
  background: rgba(255, 255, 255, 0.3);
}
</style>
