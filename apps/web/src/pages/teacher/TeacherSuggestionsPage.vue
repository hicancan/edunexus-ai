<script setup lang="ts">
import { ref } from "vue";
import { NButton, useMessage } from "naive-ui";
import { Send, Users, ShieldAlert, Sparkles, CheckCircle2 } from "lucide-vue-next";
import { useTeacherStore } from "../../features/teacher-workspace/model/teacher";

const teacherStore = useTeacherStore();
const message = useMessage();

const pendingInterventions = ref([
  {
    id: "int-1",
    topic: "闭包与作用域链的内存驻留漏斗",
    studentCount: 18,
    courseDetails: "建议立刻向该弱项群体并联投射【V8 引擎垃圾回收图解】视觉靶向题库",
    dispatched: false,
    loading: false
  },
  {
    id: "int-2",
    topic: "动态规划方程状态转移失败",
    studentCount: 12,
    courseDetails: "监测到逻辑断层。建议投放【背包问题全集与阶段坍缩原理】拔高流模型",
    dispatched: false,
    loading: false
  }
]);

async function dispatchIntervention(item: any) {
  item.loading = true;
  // Simulate network delay for AI dispatching to multiple students
  await new Promise((resolve) => setTimeout(resolve, 1500));
  
  // Submit a mock suggestion request to register in the actual API backend
  const studentId = localStorage.getItem("teacher.analytics.studentId") || "00000000-0000-0000-0000-000000000000";
  
  await teacherStore.submitSuggestion({
    studentId: studentId,
    knowledgePoint: item.topic,
    suggestion: `AI自动下发干预策略：${item.courseDetails}`
  });
  
  item.loading = false;
  item.dispatched = true;
  message.success(`已成功向 ${item.studentCount} 名特工通讯录定向投放干预流！`);
}
</script>

<template>
  <div class="suggestions-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">智能靶向干预中枢</h1>
          <p class="workspace-subtitle">搭载核心引擎的数据聚类，自动捕获群体认知偏航，实施一键降维打击与知识补救。</p>
        </div>
      </div>

      <div class="intervention-grid">
         <div v-for="item in pendingInterventions" :key="item.id" class="panel glass-card int-card" :class="{ 'dispatched-card': item.dispatched }">
            <div class="int-header">
               <div class="int-icon">
                 <ShieldAlert v-if="!item.dispatched" :size="24" class="text-danger" />
                 <CheckCircle2 v-else :size="24" class="text-success" />
               </div>
               <div class="int-title-area">
                 <h3 class="int-topic">{{ item.topic }}</h3>
                 <span class="int-meta"><Users :size="14" style="margin-right: 4px" /> 影响范围：{{ item.studentCount }} 名特工</span>
               </div>
            </div>
            
            <div class="int-body">
               <div class="ai-suggest-box">
                  <span class="ai-badge"><Sparkles :size="12" style="margin-right: 4px"/> Nexus AI 决策方案</span>
                  <p class="ai-strategy">{{ item.courseDetails }}</p>
               </div>
            </div>

            <div class="int-footer">
               <n-button 
                 v-if="!item.dispatched"
                 type="primary" 
                 class="animate-pop dispatch-btn" 
                 :loading="item.loading" 
                 @click="dispatchIntervention(item)"
               >
                  <template #icon><Send :size="16" /></template>
                  一键授权下发补救包
               </n-button>
               <n-button v-else type="success" secondary ghost disabled class="dispatched-btn">
                  已完成维稳干预
               </n-button>
            </div>
         </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.intervention-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: var(--space-5);
  margin-top: 16px;
}

.int-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  border-top: 4px solid var(--color-danger);
  transition: all 0.3s ease;
}

.int-card.dispatched-card {
  border-top-color: var(--color-success);
  opacity: 0.8;
}

.int-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.int-icon {
  background: rgba(255,255,255,0.8);
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.text-danger { color: var(--color-danger); }
.text-success { color: var(--color-success); }

.int-title-area {
  flex: 1;
}

.int-topic {
  margin: 0 0 6px 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--color-text-main);
  line-height: 1.4;
}

.int-meta {
  display: inline-flex;
  align-items: center;
  color: var(--color-danger);
  font-size: 0.85rem;
  font-weight: 600;
  background: rgba(239, 68, 68, 0.1);
  padding: 4px 10px;
  border-radius: 6px;
}

.dispatched-card .int-meta {
  color: var(--color-success);
  background: rgba(16, 185, 129, 0.1);
}

.ai-suggest-box {
  background: linear-gradient(135deg, rgba(92,101,246,0.05), rgba(92,101,246,0.15));
  padding: 16px;
  border-radius: 8px;
  border: 1px solid rgba(92, 101, 246, 0.2);
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.75rem;
  color: var(--color-primary);
  font-weight: 700;
  margin-bottom: 8px;
  border-bottom: 1px dashed rgba(92, 101, 246, 0.4);
  padding-bottom: 4px;
}

.ai-strategy {
  margin: 0;
  font-size: 0.95rem;
  color: var(--color-text-main);
  line-height: 1.5;
}

.int-footer {
  margin-top: 24px;
  text-align: right;
}

.dispatch-btn {
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
  background: var(--color-danger);
  border: none;
}

.dispatch-btn:hover {
  background: #dc2626 !important;
}

.dispatched-btn {
  font-weight: 600;
}

@media (max-width: 768px) {
  .intervention-grid {
    grid-template-columns: 1fr;
  }
}
</style>
