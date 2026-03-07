<script setup lang="ts">
import { NSpin, NEmpty, NText } from "naive-ui";
import { Bot, User, FileText } from "lucide-vue-next";
import MarkdownPreview from "../common/MarkdownPreview.vue";
import { useChatStore } from "../../features/student/model/chat";

const chatStore = useChatStore();
</script>

<template>
  <div class="message-list-inner">
    <div v-if="chatStore.detailLoading && chatStore.messages.length === 0" class="loading-wrapper">
      <n-spin size="medium" />
    </div>
    <n-empty
      v-else-if="chatStore.messages.length === 0"
      description="开始提问吧"
      style="margin-top: 60px"
    />

    <div v-else class="message-list">
      <div
        v-for="(msg, index) in chatStore.messages"
        :key="`${msg.id || 'msg'}-${index}`"
        class="message-bubble-wrapper"
        :class="msg.role === 'USER' ? 'user' : 'assistant'"
      >
        <div v-if="msg.role !== 'USER'" class="avatar-box">
          <Bot :size="20" class="bot-avatar" />
        </div>

        <div class="message-bubble">
          <n-text depth="3" class="message-role">{{
            msg.role === "USER" ? "我" : "AI 助手"
          }}</n-text>
          <div class="message-content-box glass-card">
            <MarkdownPreview
              v-if="msg.role !== 'USER'"
              :content="msg.content || ''"
              :plain="true"
            />
            <n-text v-else class="user-content">{{ msg.content }}</n-text>
          </div>

          <div v-if="msg.citations && msg.citations.length > 0" class="citation-container">
            <div v-for="(citation, ci) in msg.citations" :key="ci" class="citation-card glass-pill">
              <FileText :size="14" class="citation-icon" />
              <div class="citation-body">
                <n-text strong class="citation-title">
                  {{ citation.filename || "未知知识片段" }}
                  <span class="citation-score"
                    >匹配度 {{ Number(citation.score || 0).toFixed(2) }}</span
                  >
                </n-text>
                <n-text depth="3" class="citation-snippet">{{
                  citation.content || "暂无摘录内容"
                }}</n-text>
              </div>
            </div>
          </div>
        </div>

        <div v-if="msg.role === 'USER'" class="avatar-box user-avatar-box">
          <User :size="20" class="user-avatar" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list-inner {
  height: 100%;
  overflow-y: auto;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
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
</style>
