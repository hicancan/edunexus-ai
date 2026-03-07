<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NButton,
  NAlert,
  NSpin,
  NPagination,
  NText,
  useMessage
} from "naive-ui";
import { Send, Search, ShieldQuestion } from "lucide-vue-next";
import QuestionCard from "../../components/student/QuestionCard.vue";
import ExerciseResults from "../../components/student/ExerciseResults.vue";
import { useExerciseStore } from "../../features/student/model/exercise";

const exerciseStore = useExerciseStore();
const message = useMessage();

const filters = reactive<{
  subject: string;
  difficulty: "" | "EASY" | "MEDIUM" | "HARD";
  page: number;
  size: number;
}>({
  subject: "",
  difficulty: "",
  page: 1,
  size: 10
});

const difficultyOptions = [
  { label: "全部", value: "" },
  { label: "简单 (EASY)", value: "EASY" },
  { label: "中等 (MEDIUM)", value: "MEDIUM" },
  { label: "困难 (HARD)", value: "HARD" }
];
const subjectInputProps = {
  id: "exercise-subject",
  name: "exerciseSubject",
  "aria-label": "练习学科"
};
const difficultyInputProps = {
  id: "exercise-difficulty",
  name: "exerciseDifficulty",
  "aria-label": "练习难度"
};

const answers = ref<Record<string, string>>({});

function resetAnswers(): void {
  answers.value = {};
}

async function loadQuestions(): Promise<void> {
  await exerciseStore.loadQuestions({
    subject: filters.subject || undefined,
    difficulty: filters.difficulty || undefined,
    page: filters.page,
    size: filters.size
  });
  resetAnswers();
}

async function submitExercise(): Promise<void> {
  const payload = exerciseStore.questions
    .filter((q) => Boolean(q.id))
    .map((q) => ({ questionId: q.id as string, userAnswer: answers.value[q.id as string] || "" }));

  if (payload.length === 0) {
    message.warning("当前没有可提交的题目");
    return;
  }
  if (payload.some((item) => !item.userAnswer.trim())) {
    message.warning("请完成所有题目后再提交");
    return;
  }

  const result = await exerciseStore.submitAnswers(payload);
  if (result?.recordId) {
    message.success("提交成功，解析结果已生成");
  }
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await loadQuestions();
}

onMounted(loadQuestions);
</script>

<template>
  <div class="exercise-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">智能练习中心</h1>
          <p class="workspace-subtitle">按学科与难度抽取题目，完成作答后获得 AI 判卷与解析。</p>
        </div>
      </div>

      <div class="panel glass-card search-panel">
        <n-form
          inline
          :model="filters"
          label-placement="left"
          :show-feedback="false"
          class="ethereal-form"
        >
          <n-form-item label="学科">
            <n-input
              v-model:value="filters.subject"
              placeholder="例如：物理、几何"
              clearable
              :input-props="subjectInputProps"
              @keydown.enter="loadQuestions"
            />
          </n-form-item>
          <n-form-item label="难度">
            <n-select
              v-model:value="filters.difficulty"
              :options="difficultyOptions"
              :input-props="difficultyInputProps"
              style="width: 180px"
            />
          </n-form-item>
          <n-form-item>
            <n-button
              type="primary"
              class="animate-pop glass-pill-btn"
              :loading="exerciseStore.questionsLoading"
              @click="loadQuestions"
            >
              <template #icon><Search :size="16" /></template>
              查询题目
            </n-button>
          </n-form-item>
        </n-form>
      </div>

      <n-alert
        v-if="exerciseStore.questionsError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ exerciseStore.questionsError }}</n-alert
      >
      <n-alert
        v-if="exerciseStore.submitError"
        type="error"
        :show-icon="true"
        style="border-radius: var(--radius-md)"
        >{{ exerciseStore.submitError }}</n-alert
      >

      <n-spin :show="exerciseStore.questionsLoading">
        <div
          v-if="exerciseStore.questionsLoaded && exerciseStore.questions.length === 0"
          class="empty-state"
        >
          <ShieldQuestion :size="48" class="empty-icon" />
          <n-text depth="3">当前没有符合条件的题目，请调整筛选条件。</n-text>
        </div>

        <div v-else class="question-list">
          <QuestionCard
            v-for="(question, index) in exerciseStore.questions"
            :key="question.id"
            :question="question"
            :index="(filters.page - 1) * filters.size + index + 1"
            :model-value="answers[question.id as string] || ''"
            @update:model-value="
              (v) => {
                answers[question.id as string] = v;
              }
            "
          />

          <div v-if="exerciseStore.questions.length > 0" class="actions-footer">
            <n-pagination
              v-model:page="exerciseStore.questionPage"
              :page-count="exerciseStore.questionTotalPages"
              :disabled="exerciseStore.questionsLoading"
              class="glass-pagination"
              @update:page="updatePage"
            />
            <n-button
              type="primary"
              size="large"
              class="animate-pop submit-btn"
              :loading="exerciseStore.submitLoading"
              @click="submitExercise"
            >
              <template #icon><Send :size="18" /></template>
              提交本页答案
            </n-button>
          </div>
        </div>
      </n-spin>

      <ExerciseResults />
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 16px 24px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 16px;
  color: var(--color-text-muted);
}

.empty-icon {
  color: var(--color-border-strong);
}

.question-list {
  display: grid;
  gap: 24px;
  margin-top: 10px;
}

.actions-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.submit-btn {
  box-shadow: var(--shadow-glow);
  padding: 0 32px;
  height: 44px;
  font-size: 1rem;
}
</style>
