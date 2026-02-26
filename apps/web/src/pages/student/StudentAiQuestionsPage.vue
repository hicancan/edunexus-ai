<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { aiGenerateSchema } from "../../schemas/student.schemas";
import { useAiqStore } from "../../stores/aiq";

const aiqStore = useAiqStore();

const form = reactive<{
  subject: string;
  count: number;
  difficulty: "" | "EASY" | "MEDIUM" | "HARD";
  conceptTagsText: string;
}>({
  subject: "",
  count: 5,
  difficulty: "MEDIUM",
  conceptTagsText: ""
});

const historyFilter = reactive({
  subject: "",
  page: 1,
  size: 10
});

const answerMap = ref<Record<string, string>>({});
const formError = ref("");
const selectedRecordId = ref("");

const currentAnalysis = computed(() => {
  const recordId = selectedRecordId.value || aiqStore.submitResult?.recordId || "";
  if (!recordId) {
    return null;
  }
  return aiqStore.analysisByRecord[recordId] || null;
});

function parseConceptTags(raw: string): string[] {
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

async function loadHistory(): Promise<void> {
  await aiqStore.loadHistory({
    subject: historyFilter.subject || undefined,
    page: historyFilter.page,
    size: historyFilter.size
  });
}

async function generateQuestions(): Promise<void> {
  formError.value = "";
  const parsed = aiGenerateSchema.safeParse({
    count: form.count,
    subject: form.subject,
    difficulty: form.difficulty || undefined,
    conceptTags: parseConceptTags(form.conceptTagsText)
  });

  if (!parsed.success) {
    formError.value = parsed.error.issues[0]?.message || "生成参数不合法";
    return;
  }

  await aiqStore.generate(parsed.data);
  answerMap.value = {};
  await loadHistory();
}

async function submitAnswers(): Promise<void> {
  formError.value = "";

  const payload = aiqStore.generatedQuestions
    .filter((question) => Boolean(question.id))
    .map((question) => ({
      questionId: question.id as string,
      userAnswer: answerMap.value[question.id as string] || ""
    }));

  if (payload.length === 0) {
    formError.value = "请先生成题目";
    return;
  }

  const hasBlank = payload.some((item) => !item.userAnswer.trim());
  if (hasBlank) {
    formError.value = "请完成所有 AI 题目后再提交";
    return;
  }

  const result = await aiqStore.submitAnswers(payload);
  if (result?.recordId) {
    selectedRecordId.value = result.recordId;
  }
  await loadHistory();
}

async function viewAnalysis(recordId: string): Promise<void> {
  selectedRecordId.value = recordId;
  await aiqStore.loadAnalysis(recordId);
}

async function updateHistoryPage(page: number): Promise<void> {
  historyFilter.page = page;
  await loadHistory();
}

async function updateHistorySize(size: number): Promise<void> {
  historyFilter.size = size;
  historyFilter.page = 1;
  await loadHistory();
}

onMounted(loadHistory);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">AI 个性化出题</h2>
        <p class="panel-note">按学科、难度和数量生成题目，提交后可查看解析与历史指标。</p>
      </div>
      <button class="btn" type="button" :disabled="aiqStore.generateLoading" @click="generateQuestions">
        {{ aiqStore.generateLoading ? "生成中..." : "生成题目" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="aiq-subject">学科</label>
        <input id="aiq-subject" v-model="form.subject" placeholder="例如：物理" />
      </div>
      <div class="field-block">
        <label for="aiq-count">题目数量</label>
        <input id="aiq-count" v-model.number="form.count" type="number" min="1" max="20" />
      </div>
      <div class="field-block">
        <label for="aiq-difficulty">难度</label>
        <select id="aiq-difficulty" v-model="form.difficulty">
          <option value="">自动</option>
          <option value="EASY">EASY</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HARD">HARD</option>
        </select>
      </div>
      <div class="field-block">
        <label for="aiq-tags">知识点（逗号分隔）</label>
        <input id="aiq-tags" v-model="form.conceptTagsText" placeholder="牛顿第二定律, 受力分析" />
      </div>
    </div>

    <p v-if="formError" class="status-box error" role="alert">{{ formError }}</p>
    <p v-if="aiqStore.error" class="status-box error" role="alert">{{ aiqStore.error }}</p>
    <p v-if="aiqStore.sessionId" class="status-box info">当前会话：{{ aiqStore.sessionId }}</p>

    <div v-if="aiqStore.generatedQuestions.length === 0" class="status-box empty">尚未生成题目。</div>
    <div v-else class="list-stack">
      <article v-for="question in aiqStore.generatedQuestions" :key="question.id" class="list-item question-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ question.content }}</p>
          <p class="list-item-meta">难度：{{ question.difficulty }} · 来源：{{ question.source }}</p>
          <div class="field-block" style="margin-top: 8px;">
            <label :for="`aiq-answer-${question.id}`">作答</label>
            <select :id="`aiq-answer-${question.id}`" v-model="answerMap[question.id as string]">
              <option value="">请选择答案</option>
              <option v-for="(value, key) in question.options || {}" :key="key" :value="key">{{ key }}. {{ value }}</option>
            </select>
          </div>
        </div>
      </article>
      <div class="list-item-actions">
        <button class="btn success" type="button" :disabled="aiqStore.submitLoading" @click="submitAnswers">
          {{ aiqStore.submitLoading ? "提交中..." : "提交 AI 答案" }}
        </button>
      </div>
    </div>

    <section v-if="aiqStore.submitResult" class="result-block">
      <h3>本次 AI 题成绩</h3>
      <div class="result-metrics">
        <span class="pill">总题数：{{ aiqStore.submitResult.totalQuestions }}</span>
        <span class="pill">正确数：{{ aiqStore.submitResult.correctCount }}</span>
        <span class="pill">总分：{{ aiqStore.submitResult.totalScore }}</span>
      </div>
      <div class="list-stack" style="margin-top: 10px;">
        <article v-for="item in aiqStore.submitResult.items || []" :key="item.questionId" class="list-item">
          <div class="list-item-main">
            <p class="list-item-title">题目 {{ item.questionId }}</p>
            <p class="list-item-meta">你的答案：{{ item.userAnswer }} · 正确答案：{{ item.correctAnswer }} · 得分：{{ item.score }}</p>
          </div>
          <button class="btn secondary small" type="button" @click="viewAnalysis(aiqStore.submitResult?.recordId || '')">查看解析</button>
        </article>
      </div>
    </section>

    <section v-if="currentAnalysis" class="result-block">
      <h3>AI 题解析</h3>
      <div class="list-stack">
        <article v-for="item in currentAnalysis.items || []" :key="item.questionId" class="list-item question-item">
          <div class="list-item-main">
            <p class="list-item-title">{{ item.content }}</p>
            <p class="list-item-meta">你的答案：{{ item.userAnswer }} · 正确答案：{{ item.correctAnswer }} · {{ item.isCorrect ? "正确" : "错误" }}</p>
            <p class="analysis-line">解析：{{ item.analysis || "暂无解析" }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="result-block">
      <header class="panel-head" style="margin-bottom: 8px;">
        <div>
          <h3 style="margin: 0;">历史记录</h3>
          <p class="panel-note">展示 completed / correctRate / score 三项关键指标。</p>
        </div>
        <button class="btn secondary" type="button" :disabled="aiqStore.historyLoading" @click="loadHistory">
          {{ aiqStore.historyLoading ? "加载中..." : "刷新历史" }}
        </button>
      </header>

      <div class="form-grid">
        <div class="field-block">
          <label for="history-subject">学科筛选</label>
          <input id="history-subject" v-model="historyFilter.subject" placeholder="可选" />
        </div>
      </div>

      <div class="list-item-actions" style="margin-top: 12px;">
        <button class="btn" type="button" :disabled="aiqStore.historyLoading" @click="loadHistory">查询历史</button>
      </div>

      <div v-if="aiqStore.historyLoading && !aiqStore.historyLoaded" class="status-box info">正在加载历史记录...</div>
      <div v-else-if="aiqStore.history.length === 0" class="status-box empty">暂无 AI 出题历史。</div>
      <div v-else class="list-stack">
        <article v-for="history in aiqStore.history" :key="history.id" class="list-item">
          <div class="list-item-main">
            <p class="list-item-title">{{ history.subject || "未分类" }}</p>
            <p class="list-item-meta">会话 ID：{{ history.id }} · 题目数：{{ history.questionCount }} · 生成时间：{{ history.generatedAt }}</p>
          </div>
          <div class="list-item-actions">
            <span class="pill">完成：{{ history.completed ? "是" : "否" }}</span>
            <span class="pill">正确率：{{ history.correctRate ?? "--" }}</span>
            <span class="pill">分数：{{ history.score ?? "--" }}</span>
          </div>
        </article>

        <PaginationBar
          :page="aiqStore.historyPage"
          :size="aiqStore.historySize"
          :total-pages="aiqStore.historyTotalPages"
          :total-elements="aiqStore.historyTotalElements"
          :disabled="aiqStore.historyLoading"
          @update:page="updateHistoryPage"
          @update:size="updateHistorySize"
        />
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

.analysis-line {
  margin: 6px 0 0;
  color: #365d7f;
}
</style>
