<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { useRouter } from "vue-router";
import { Bot, X, Sparkles, MessageSquare, ArrowRight } from "lucide-vue-next";
import { useMessage } from "naive-ui";

const router = useRouter();
const message = useMessage();

const isVisible = ref(false);
const isExpanded = ref(false);
const showIndicator = ref(true);

// Mocking proactive triggers - In a real system, this would tie into a WebSocket or Pinia store watching for "3 wrong answers" etc.
const proactiveMessages = [
  { id: 1, title: '发现薄弱点', content: '系统检测到您在【立体几何】部分连续出错，是否需要 AI 为您进行专项解析与概念巩固？' },
  { id: 2, title: '计划提醒', content: '您今天还没有完成基础词汇训练，是否立刻前往练习大厅提取试卷？' }
];

const currentMessage = ref(proactiveMessages[0]);

function toggleExpand() {
  isExpanded.value = !isExpanded.value;
  if (isExpanded.value) {
    showIndicator.value = false;
  }
}

function dismissAgent() {
  isVisible.value = false;
  isExpanded.value = false;
}

function handleAction() {
  message.success("正在生成专属辅导路线...");
  isExpanded.value = false;
  setTimeout(() => {
    router.push('/student/chat');
  }, 600);
}

// Randomly trigger the agent for demo purposes during this 2026 showcase
let triggerTimer: ReturnType<typeof setTimeout>;

onMounted(() => {
  triggerTimer = setTimeout(() => {
    isVisible.value = true;
    showIndicator.value = true;
  }, 5000); // Trigger after 5 seconds of entering the student domain
});

onUnmounted(() => {
  clearTimeout(triggerTimer);
});
</script>

<template>
  <Transition name="agent-slide">
    <div v-if="isVisible" class="floating-agent" :class="{ 'is-expanded': isExpanded }">
      <!-- Collapsed Avatar Button -->
      <button 
        v-if="!isExpanded" 
        class="agent-avatar hover-glow"
        @click="toggleExpand"
        aria-label="EduNexus AI 助手"
      >
        <div class="avatar-ring"></div>
        <div class="avatar-inner">
          <Bot :size="24" />
        </div>
        <span v-if="showIndicator" class="notification-dot"></span>
      </button>

      <!-- Expanded Panel -->
      <div v-if="isExpanded" class="agent-panel glass-card">
        <div class="panel-header">
          <div class="header-title">
            <Sparkles :size="16" class="icon-sparkle" />
            <span>EduNexus Proactive AI</span>
          </div>
          <button class="close-btn" @click="dismissAgent" aria-label="关闭">
            <X :size="16" />
          </button>
        </div>
        
        <div class="panel-body">
          <div class="message-bubble animate-pop">
            <h4 class="msg-title">{{ currentMessage.title }}</h4>
            <p class="msg-content">{{ currentMessage.content }}</p>
          </div>
        </div>
        
        <div class="panel-footer">
          <button class="action-btn" @click="handleAction">
            <MessageSquare :size="14" />
            <span>开启智导</span>
            <ArrowRight :size="14" class="arrow-icon" />
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.floating-agent {
  position: fixed;
  bottom: 40px;
  right: 40px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

/* Avatar Button */
.agent-avatar {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.agent-avatar:hover {
  transform: scale(1.05) translateY(-5px);
}

.avatar-inner {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary), #3b82f6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  box-shadow: 0 10px 25px rgba(92, 101, 246, 0.4);
}

.avatar-ring {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
  opacity: 0.5;
  animation: pulse-ring 2s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
  z-index: 1;
}

.notification-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 12px;
  height: 12px;
  background-color: var(--color-danger);
  border: 2px solid white;
  border-radius: 50%;
  z-index: 3;
}

/* Expanded Panel */
.agent-panel {
  width: 320px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.12), 0 0 0 1px rgba(255, 255, 255, 0.2) inset;
  backdrop-filter: blur(24px);
  background: rgba(255, 255, 255, 0.85);
  transform-origin: bottom right;
  animation: modal-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

html.dark .agent-panel {
  background: rgba(30, 30, 35, 0.85);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.05) inset;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(90deg, rgba(92,101,246,0.1), rgba(92,101,246,0.02));
  border-bottom: 1px solid var(--color-border-glass);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-primary);
}

.icon-sparkle {
  color: var(--color-warning);
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(0,0,0,0.05);
  color: var(--color-danger);
}

.panel-body {
  padding: 16px;
}

.message-bubble {
  background: var(--bg-card);
  border: 1px solid var(--color-border-glass);
  border-radius: 12px;
  border-bottom-right-radius: 4px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
}

.msg-title {
  margin: 0 0 8px;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-text-main);
}

.msg-content {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--color-text-main);
}

.panel-footer {
  padding: 12px 16px 16px;
  display: flex;
  justify-content: flex-end;
}

.action-btn {
  background: linear-gradient(135deg, var(--color-primary), #4f46e5);
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(92, 101, 246, 0.3);
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(92, 101, 246, 0.4);
}

.arrow-icon {
  transition: transform 0.3s ease;
}

.action-btn:hover .arrow-icon {
  transform: translateX(3px);
}

/* Animations */
@keyframes pulse-ring {
  0% { transform: scale(0.8); opacity: 0.8; }
  80% { transform: scale(1.5); opacity: 0; }
  100% { transform: scale(1.5); opacity: 0; }
}

@keyframes modal-pop {
  0% { opacity: 0; transform: scale(0.9) translateY(20px); }
  100% { opacity: 1; transform: scale(1) translateY(0); }
}

.agent-slide-enter-active,
.agent-slide-leave-active {
  transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.agent-slide-enter-from,
.agent-slide-leave-to {
  opacity: 0;
  transform: translateY(40px) scale(0.8);
}
</style>
