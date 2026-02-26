<script setup lang="ts">
import { reactive, ref } from "vue";
import { teacherSuggestionSchema } from "../../schemas/teacher.schemas";
import { useTeacherStore } from "../../stores/teacher";

const teacherStore = useTeacherStore();

const form = reactive({
  studentId: localStorage.getItem("teacher.analytics.studentId") || "",
  questionId: "",
  knowledgePoint: "",
  suggestion: ""
});

const formError = ref("");
const success = ref("");

async function submitSuggestion(): Promise<void> {
  formError.value = "";
  success.value = "";

  const parsed = teacherSuggestionSchema.safeParse(form);
  if (!parsed.success) {
    formError.value = parsed.error.issues[0]?.message || "建议参数不合法";
    return;
  }

  const result = await teacherStore.submitSuggestion({
    studentId: parsed.data.studentId,
    questionId: parsed.data.questionId || undefined,
    knowledgePoint: parsed.data.knowledgePoint || undefined,
    suggestion: parsed.data.suggestion
  });

  if (result) {
    success.value = "教师建议已提交，学生解析页与 AI 出题上下文可消费该建议。";
    form.suggestion = "";
  }
}
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">教师建议管理</h2>
        <p class="panel-note">建议提交后将关联学生学习链路，用于解析增强与 AI 出题上下文。</p>
      </div>
      <button class="btn" type="button" :disabled="teacherStore.suggestionLoading" @click="submitSuggestion">
        {{ teacherStore.suggestionLoading ? "提交中..." : "提交建议" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="suggestion-student-id">学生 ID</label>
        <input id="suggestion-student-id" v-model="form.studentId" placeholder="学生 UUID" />
      </div>
      <div class="field-block">
        <label for="suggestion-question-id">题目 ID（可选）</label>
        <input id="suggestion-question-id" v-model="form.questionId" placeholder="题目 UUID" />
      </div>
      <div class="field-block">
        <label for="suggestion-knowledge-point">知识点（可选）</label>
        <input id="suggestion-knowledge-point" v-model="form.knowledgePoint" placeholder="例如：牛顿第二定律" />
      </div>
    </div>

    <div class="field-block" style="margin-top: 10px;">
      <label for="suggestion-content">建议内容</label>
      <textarea id="suggestion-content" v-model="form.suggestion" rows="5" placeholder="请输入建议内容" />
    </div>

    <p v-if="formError" class="status-box error" role="alert">{{ formError }}</p>
    <p v-if="teacherStore.suggestionError" class="status-box error" role="alert">{{ teacherStore.suggestionError }}</p>
    <p v-if="success" class="status-box success">{{ success }}</p>

    <section v-if="teacherStore.latestSuggestion" class="latest-suggestion">
      <h3>最近一次提交</h3>
      <p class="list-item-meta">建议 ID：{{ teacherStore.latestSuggestion.id }}</p>
      <p class="list-item-meta">学生 ID：{{ teacherStore.latestSuggestion.studentId }}</p>
      <p class="list-item-meta">创建时间：{{ teacherStore.latestSuggestion.createdAt }}</p>
    </section>
  </section>
</template>

<style scoped>
.latest-suggestion {
  margin-top: 12px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}

.latest-suggestion h3 {
  margin: 0 0 8px;
}
</style>
