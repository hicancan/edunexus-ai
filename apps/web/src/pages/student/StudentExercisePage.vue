<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { useExerciseStore } from "../../stores/exercise";

const exerciseStore = useExerciseStore();

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

const answers = ref<Record<string, string>>({});
const formError = ref("");
const selectedAnalysisRecordId = ref("");

const currentAnalysis = computed(() => {
  const recordId =
    selectedAnalysisRecordId.value ||
    exerciseStore.latestResult?.recordId ||
    "";
  return recordId ? exerciseStore.analysisByRecord[recordId] : null;
});

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
  formError.value = "";

  const payload = exerciseStore.questions
    .filter((question) => Boolean(question.id))
    .map((question) => ({
      questionId: question.id as string,
      userAnswer: answers.value[question.id as string] || ""
    }));

  if (payload.length === 0) {
    formError.value = "暂无可提交题目";
    return;
  }

  const hasBlank = payload.some((item) => !item.userAnswer.trim());
  if (hasBlank) {
    formError.value = "请完成全部题目作答后再提交";
    return;
  }

  const result = await exerciseStore.submitAnswers(payload);
  if (result?.recordId) {
    selectedAnalysisRecordId.value = result.recordId;
  }
}

async function viewAnalysis(recordId: string): Promise<void> {
  selectedAnalysisRecordId.value = recordId;
  await exerciseStore.loadAnalysis(recordId);
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await loadQuestions();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await loadQuestions();
}

onMounted(loadQuestions);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">练习做题</h2>
        <p class="panel-note">按学科与难度筛选题目，提交后自动判分并支持查看解析。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="exerciseStore.questionsLoading" @click="loadQuestions">
        {{ exerciseStore.questionsLoading ? "加载中..." : "刷新题目" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="exercise-subject">学科</label>
        <input id="exercise-subject" v-model="filters.subject" placeholder="例如：物理" />
      </div>
      <div class="field-block">
        <label for="exercise-difficulty">难度</label>
        <select id="exercise-difficulty" v-model="filters.difficulty">
          <option value="">全部</option>
          <option value="EASY">EASY</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HARD">HARD</option>
        </select>
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn" type="button" :disabled="exerciseStore.questionsLoading" @click="loadQuestions">按条件查询</button>
      <button class="btn success" type="button" :disabled="exerciseStore.submitLoading" @click="submitExercise">
        {{ exerciseStore.submitLoading ? "提交中..." : "提交答案" }}
      </button>
    </div>

    <p v-if="formError" class="status-box error" role="alert">{{ formError }}</p>
    <p v-if="exerciseStore.questionsError" class="status-box error" role="alert">{{ exerciseStore.questionsError }}</p>
    <p v-if="exerciseStore.submitError" class="status-box error" role="alert">{{ exerciseStore.submitError }}</p>

    <div v-if="exerciseStore.questionsLoading && !exerciseStore.questionsLoaded" class="status-box info">题目加载中...</div>
    <div v-else-if="exerciseStore.questions.length === 0" class="status-box empty">暂无题目，请调整筛选条件后重试。</div>
    <div v-else class="list-stack">
      <article v-for="question in exerciseStore.questions" :key="question.id" class="list-item question-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ question.content }}</p>
          <p class="list-item-meta">题型：{{ question.questionType }} · 难度：{{ question.difficulty }} · 分值：{{ question.score }}</p>
          <div class="field-block" style="margin-top: 8px;">
            <label :for="`answer-${question.id}`">作答</label>
            <select :id="`answer-${question.id}`" v-model="answers[question.id as string]">
              <option value="">请选择答案</option>
              <option v-for="(value, key) in question.options || {}" :key="key" :value="key">{{ key }}. {{ value }}</option>
            </select>
          </div>
        </div>
      </article>

      <PaginationBar
        :page="exerciseStore.questionPage"
        :size="exerciseStore.questionSize"
        :total-pages="exerciseStore.questionTotalPages"
        :total-elements="exerciseStore.questionTotalElements"
        :disabled="exerciseStore.questionsLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>

    <section v-if="exerciseStore.latestResult" class="result-block">
      <h3>本次判分结果</h3>
      <div class="result-metrics">
        <span class="pill">总题数：{{ exerciseStore.latestResult.totalQuestions }}</span>
        <span class="pill">正确数：{{ exerciseStore.latestResult.correctCount }}</span>
        <span class="pill">总分：{{ exerciseStore.latestResult.totalScore }}</span>
      </div>
      <div class="list-stack" style="margin-top: 10px;">
        <article v-for="item in exerciseStore.latestResult.items || []" :key="item.questionId" class="list-item">
          <div class="list-item-main">
            <p class="list-item-title">题目 {{ item.questionId }}</p>
            <p class="list-item-meta">你的答案：{{ item.userAnswer }} · 正确答案：{{ item.correctAnswer }} · 得分：{{ item.score }}</p>
          </div>
          <button class="btn secondary small" type="button" @click="viewAnalysis(exerciseStore.latestResult?.recordId || '')">查看解析</button>
        </article>
      </div>
    </section>

    <section v-if="exerciseStore.analysisError" class="status-box error" role="alert">{{ exerciseStore.analysisError }}</section>
    <section v-if="currentAnalysis" class="result-block">
      <h3>解析详情</h3>
      <div class="list-stack">
        <article v-for="item in currentAnalysis.items || []" :key="item.questionId" class="list-item question-analysis-item">
          <div class="list-item-main">
            <p class="list-item-title">{{ item.content }}</p>
            <p class="list-item-meta">
              你的答案：{{ item.userAnswer }} · 正确答案：{{ item.correctAnswer }} · 结果：{{ item.isCorrect ? "正确" : "错误" }}
            </p>
            <p class="analysis-text">解析：{{ item.analysis || "暂无解析" }}</p>
            <p class="analysis-text" v-if="item.teacherSuggestion">教师建议：{{ item.teacherSuggestion }}</p>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.question-item {
  align-items: flex-start;
}

.result-block {
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  padding-top: 14px;
}

.result-block h3 {
  margin: 0 0 10px;
}

.result-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-analysis-item {
  align-items: flex-start;
}

.analysis-text {
  margin: 6px 0 0;
  color: #355b7d;
}
</style>
