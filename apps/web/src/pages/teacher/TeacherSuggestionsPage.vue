<script setup lang="ts">
import { reactive, ref } from "vue";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  NText,
  NAlert,
  NStatistic,
  useMessage
} from "naive-ui";
import { Send, Clock } from "lucide-vue-next";
import { teacherSuggestionSchema } from "../../features/teacher-workspace/model/teacher.schemas";
import { useTeacherStore } from "../../features/teacher-workspace/model/teacher";

const teacherStore = useTeacherStore();
const message = useMessage();

const form = reactive({
  studentId: localStorage.getItem("teacher.analytics.studentId") || "",
  questionId: "",
  knowledgePoint: "",
  suggestion: ""
});

async function submitSuggestion(): Promise<void> {
  const parsed = teacherSuggestionSchema.safeParse(form);
  if (!parsed.success) {
    message.warning(parsed.error.issues[0]?.message || "建议参数不合法");
    return;
  }

  const result = await teacherStore.submitSuggestion({
    studentId: parsed.data.studentId,
    questionId: parsed.data.questionId || undefined,
    knowledgePoint: parsed.data.knowledgePoint || undefined,
    suggestion: parsed.data.suggestion
  });

  if (result) {
    message.success("跨模态学情干预成功投递，底层分析架构已感知。");
    form.suggestion = "";
  }
}
</script>

<template>
  <div class="suggestions-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">动态干预指导</n-text>
          <n-text depth="3">录入针对性意见。提交后将融入引擎知识库，反哺关联学生的底层画像与 AI出题倾向。</n-text>
        </div>
      </div>

      <n-card :bordered="true" class="suggestion-card">
        <n-form :model="form" label-placement="left" label-width="120" require-mark-placement="right-hanging">
          <n-form-item label="目标学生 UUID" path="studentId" required>
            <n-input v-model:value="form.studentId" placeholder="输入绑定 UUID 以建立靶向链接" style="max-width: 400px" />
          </n-form-item>
          
          <n-form-item label="锚定题目 UUID" path="questionId">
            <n-input v-model:value="form.questionId" placeholder="如需点对点纠错可选填" style="max-width: 400px" />
          </n-form-item>
          
          <n-form-item label="辐射知识点" path="knowledgePoint">
            <n-input v-model:value="form.knowledgePoint" placeholder="例如：动能定理，关联 AI RAG 会聚流" style="max-width: 400px" />
          </n-form-item>
          
          <n-form-item label="高教指导意见" path="suggestion" required>
            <n-input
              v-model:value="form.suggestion"
              type="textarea"
              placeholder="编写深度剖析意见，将被注入 AI 分析流中..."
              :autosize="{ minRows: 4, maxRows: 8 }"
            />
          </n-form-item>
          
          <n-form-item>
             <n-space justify="end" style="width: 100%;">
                <n-button type="primary" size="large" :loading="teacherStore.suggestionLoading" @click="submitSuggestion">
                  <template #icon><Send :size="18" /></template>
                  下发战略指导意见
                </n-button>
             </n-space>
          </n-form-item>
        </n-form>
      </n-card>

      <n-alert v-if="teacherStore.suggestionError" type="error" :show-icon="true">{{ teacherStore.suggestionError }}</n-alert>

      <n-card v-if="teacherStore.latestSuggestion" title="数据管道最新追踪快照" :bordered="true" size="small" style="margin-top: 8px;">
         <template #header-extra>
            <n-space align="center" :size="4">
               <Clock :size="14" style="color: #64748b;" />
               <n-text depth="3">即时回执</n-text>
            </n-space>
         </template>
         <n-space :size="40">
           <div>
             <n-text depth="3">分发事务 ID号段</n-text>
             <div><n-text strong>{{ teacherStore.latestSuggestion.id }}</n-text></div>
           </div>
           <div>
             <n-text depth="3">路由命中节点 / 学生</n-text>
             <div><n-text strong>{{ teacherStore.latestSuggestion.studentId }}</n-text></div>
           </div>
           <div>
             <n-text depth="3">事件写入戳</n-text>
             <div><n-text strong>{{ teacherStore.latestSuggestion.createdAt }}</n-text></div>
           </div>
         </n-space>
      </n-card>
    </n-space>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.suggestion-card {
  border-radius: 8px;
  background-color: #fafcfe;
}
</style>
