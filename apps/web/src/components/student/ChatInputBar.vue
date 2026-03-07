<script setup lang="ts">
import { ref } from "vue";
import { NInput, NButton } from "naive-ui";
import { SendHorizontal } from "lucide-vue-next";

const props = defineProps<{ sending: boolean }>();
const emit = defineEmits<{ (e: "send", message: string): void }>();

const inputValue = ref("");

function send(): void {
  const content = inputValue.value.trim();
  if (!content || props.sending) return;
  emit("send", content);
  inputValue.value = "";
}
</script>

<template>
  <div class="chat-input-area glass-card">
    <div class="input-box-wrapper">
      <n-input
        v-model:value="inputValue"
        type="textarea"
        class="main-chat-input"
        placeholder="输入问题并回车发送"
        :autosize="{ minRows: 1, maxRows: 6 }"
        :bordered="false"
        @keydown.enter.prevent="send"
      />
      <n-button type="primary" circle class="send-btn animate-pop" :loading="sending" @click="send">
        <template #icon><SendHorizontal :size="16" /></template>
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.chat-input-area {
  margin: var(--space-4);
  padding: 6px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 24px;
  border: 1px solid var(--color-border-glass);
  box-shadow: var(--shadow-glass);
}

.input-box-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 10px;
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
</style>
