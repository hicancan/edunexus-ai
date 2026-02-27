<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NButton,
  NSpace,
  NText,
  NAlert,
  NSpin,
  NEmpty,
  NRadioGroup,
  NRadio,
  NTag,
  NDivider,
  NInputNumber,
  NDataTable,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import { Sparkles, Send, CheckCircle, AlertCircle, RefreshCw, Search, FileText } from "lucide-vue-next";
import { aiGenerateSchema } from "../../features/student/model/student.schemas";
import { useAiqStore } from "../../features/student/model/aiq";
// Import removed due to type constraints

const aiqStore = useAiqStore();
const message = useMessage();

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

const difficultyOptions = [
  { label: "自动匹配难度", value: "" },
  { label: "简单 EASY", value: "EASY" },
  { label: "中等 MEDIUM", value: "MEDIUM" },
  { label: "困难 HARD", value: "HARD" }
];

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

const historyPagination = reactive({
  page: historyFilter.page,
  pageSize: historyFilter.size,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    historyPagination.page = page;
    historyFilter.page = page;
    loadHistory();
  },
  onUpdatePageSize: (pageSize: number) => {
    historyPagination.pageSize = pageSize;
    historyPagination.page = 1;
    historyFilter.size = pageSize;
    historyFilter.page = 1;
    loadHistory();
  },
  itemCount: 0
});

watch(
  () => aiqStore.historyTotalElements,
  (total) => {
    historyPagination.itemCount = total;
  }
);

async function loadHistory(): Promise<void> {
  await aiqStore.loadHistory({
    subject: historyFilter.subject || undefined,
    page: historyFilter.page,
    size: historyFilter.size
  });
}

async function applyHistoryFilters(): Promise<void> {
  historyFilter.page = 1;
  historyPagination.page = 1;
  await loadHistory();
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
    message.warning(parsed.error.issues[0]?.message || "生成参数不合法");
    return;
  }

  try {
    await aiqStore.generate(parsed.data);
    answerMap.value = {};
    message.success("AI 题目生成成功");
    await loadHistory();
  } catch (error) {
    message.error("生成失败，请重试");
  }
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
    message.warning("请先生成题目");
    return;
  }

  const hasBlank = payload.some((item) => !item.userAnswer.trim());
  if (hasBlank) {
    message.warning("请完成所有 AI 题目后再提交");
    return;
  }

  const result = await aiqStore.submitAnswers(payload);
  if (result?.recordId) {
    selectedRecordId.value = result.recordId;
    message.success("提交成功，已加载分析与成绩");
  }
  await loadHistory();
}

async function viewAnalysis(recordId: string): Promise<void> {
  selectedRecordId.value = recordId;
  await aiqStore.loadAnalysis(recordId);
  message.success("分析数据已加载");
}

function getDifficultyType(diff: string): "success" | "warning" | "error" | "default" {
  if (diff === "EASY") return "success";
  if (diff === "MEDIUM") return "warning";
  if (diff === "HARD") return "error";
  return "default";
}

const historyColumns: DataTableColumns<any> = [
  {
    title: "学科",
    key: "subject",
    render(row) {
      return h(NTag, { type: "info", size: "small", bordered: false }, { default: () => row.subject || "未分类" });
    }
  },
  {
    title: "生成时间",
    key: "generatedAt",
    width: 200,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.generatedAt });
    }
  },
  {
    title: "题目数",
    key: "questionCount",
    align: "center",
  },
  {
    title: "完成状态",
    key: "completed",
    align: "center",
    render(row) {
      return h(
        NTag,
        { type: row.completed ? "success" : "warning", size: "small", bordered: false },
        { default: () => (row.completed ? "已完成" : "未完成") }
      );
    }
  },
  {
    title: "正确率 / 得分",
    key: "metrics",
    align: "center",
    render(row) {
      if (!row.completed) return h(NText, { depth: 3 }, { default: () => "--" });
      return h(
        NSpace,
        { align: "center", justify: "center", size: 8 },
        () => [
           h(NText, { type: "success", strong: true }, { default: () => `${(Number(row.correctRate || 0) * 100).toFixed(0)}%` }),
           h(NText, { depth: 3 }, { default: () => "|" }),
           h(NText, { type: "info", strong: true }, { default: () => `${row.score} 分` })
        ]
      );
    }
  },
];

onMounted(async () => {
  historyPagination.page = historyFilter.page;
  historyPagination.pageSize = historyFilter.size;
  await loadHistory();
});
</script>

<template>
  <div class="ai-questions-page">
    <n-space vertical :size="24">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">AI 个性化出题</n-text>
          <n-text depth="3">按学科、难度和数量生成题目，提交后可查看解析与历史指标。</n-text>
        </div>
      </div>

      <!-- Generate Panel -->
      <n-card :bordered="true" title="配置出题要求" size="small">
        <template #header-extra>
          <n-button type="primary" :loading="aiqStore.generateLoading" @click="generateQuestions">
             <template #icon><Sparkles :size="16" /></template>
             神奇生成
          </n-button>
        </template>
        
        <n-form inline :model="form" label-placement="left" :show-feedback="false">
          <n-form-item label="学科">
             <n-input v-model:value="form.subject" placeholder="例如：物理" clearable />
          </n-form-item>
          <n-form-item label="题目数量">
             <n-input-number v-model:value="form.count" :min="1" :max="20" style="width: 120px" />
          </n-form-item>
          <n-form-item label="难度">
            <n-select v-model:value="form.difficulty" :options="difficultyOptions" style="width: 150px" />
          </n-form-item>
          <n-form-item label="知识点">
            <n-input v-model:value="form.conceptTagsText" placeholder="逗号分隔，可选" />
          </n-form-item>
        </n-form>
      </n-card>
      
      <n-alert v-if="aiqStore.error" type="error" :show-icon="true">{{ aiqStore.error }}</n-alert>
      <n-alert v-if="aiqStore.sessionId" type="info" :show-icon="true" class="session-alert">
        <template #icon>
          <FileText :size="18" />
        </template>
        当前答题会话：{{ aiqStore.sessionId }}
      </n-alert>

      <!-- Questions List -->
      <n-spin :show="aiqStore.generateLoading">
        <n-empty v-if="aiqStore.generatedQuestions.length === 0" description="尚未生成题目" style="margin: 40px 0" />
        
        <div v-else class="question-list">
          <n-card
            v-for="(question, index) in aiqStore.generatedQuestions"
            :key="question.id"
            class="question-card"
            :bordered="true"
            size="small"
          >
            <template #header>
              <n-text strong>{{ index + 1 }}. {{ question.content }}</n-text>
            </template>
            <template #header-extra>
              <n-space :size="8">
                 <n-tag size="small" :bordered="false">{{ question.questionType === "SHORT_ANSWER" ? "简答题" : "选择题" }}</n-tag>
                 <n-tag size="small" :type="getDifficultyType(question.difficulty || '')" :bordered="false">{{ question.difficulty }}</n-tag>
              </n-space>
            </template>

            <div class="answer-area">
              <n-radio-group
                v-if="question.questionType !== 'SHORT_ANSWER'"
                v-model:value="answerMap[question.id as string]"
                name="answer-group"
              >
                <n-space vertical :size="12">
                  <n-radio v-for="(value, key) in question.options || {}" :key="key" :value="key">
                    {{ key }}. {{ value }}
                  </n-radio>
                </n-space>
              </n-radio-group>
              
              <n-input
                v-else
                v-model:value="answerMap[question.id as string]"
                type="textarea"
                placeholder="请输入详细答案..."
                :autosize="{ minRows: 2, maxRows: 5 }"
              />
            </div>
          </n-card>

          <div v-if="aiqStore.generatedQuestions.length > 0" class="actions-footer">
            <n-button
              type="primary"
              size="large"
              :loading="aiqStore.submitLoading"
              @click="submitAnswers"
              style="width: 100%"
            >
              <template #icon>
                <Send :size="16" />
              </template>
              提交 AI 试卷
            </n-button>
          </div>
        </div>
      </n-spin>

      <!-- Result Card -->
      <n-card v-if="aiqStore.submitResult" title="判题结果分析" :bordered="true" class="result-card" size="small">
        <n-space :size="24" style="margin-bottom: 20px">
          <div class="metric-item">
            <n-text depth="3">总题数</n-text>
            <n-text class="metric-value">{{ aiqStore.submitResult.totalQuestions }}</n-text>
          </div>
          <div class="metric-item">
            <n-text depth="3">正确数</n-text>
            <n-text class="metric-value success">{{ aiqStore.submitResult.correctCount }}</n-text>
          </div>
          <div class="metric-item">
            <n-text depth="3">总得分</n-text>
            <n-text class="metric-value highlight">{{ aiqStore.submitResult.totalScore }}</n-text>
          </div>
        </n-space>
        
        <n-space vertical :size="12">
          <div v-for="item in aiqStore.submitResult.items || []" :key="item.questionId" class="result-detail-item">
            <n-space justify="space-between" align="center">
               <n-text strong>题目 {{ item.questionId }}</n-text>
               <component :is="item.isCorrect ? CheckCircle : AlertCircle" :size="20" :class="item.isCorrect ? 'text-success' : 'text-error'" />
            </n-space>
            <n-space :size="20">
              <n-text depth="2">你的答案：<n-text strong :type="item.isCorrect ? 'success' : 'error'">{{ item.userAnswer }}</n-text></n-text>
              <n-text depth="2">正确答案：<n-text strong type="success">{{ item.correctAnswer }}</n-text></n-text>
              <n-text depth="2">得分：<n-text strong>{{ item.score }}</n-text></n-text>
            </n-space>
          </div>
        </n-space>
        
        <template #action>
          <n-button
            ghost
            type="primary"
            class="view-analysis-btn"
            @click="viewAnalysis(aiqStore.submitResult?.recordId || '')"
          >
            查看权威 AI 解析
          </n-button>
        </template>
      </n-card>

      <!-- Analysis Card -->
      <n-card v-if="currentAnalysis" title="逐题解析与指引" :bordered="true" class="analysis-card" size="small">
        <n-space vertical :size="16">
          <div v-for="(item, index) in currentAnalysis.items || []" :key="item.questionId" class="analysis-item-box">
            <n-text strong class="analysis-title">题目: {{ item.content }}</n-text>
            <div class="analysis-badge-row">
              <n-tag :type="item.isCorrect ? 'success' : 'error'" size="small">{{ item.isCorrect ? "正确" : "错误" }}</n-tag>
              <n-text depth="3">| 你的答案：{{ item.userAnswer }} | 正确答案：{{ item.correctAnswer }}</n-text>
            </div>
            <div class="analysis-content">
               <n-text depth="2" class="analysis-label">【AI 深度解析】</n-text>
               <n-text>{{ item.analysis || "暂无解析" }}</n-text>
            </div>
            <div v-if="item.teacherSuggestion" class="teacher-suggestion">
               <n-text type="warning" class="analysis-label">【学习建议】</n-text>
               <n-text type="warning">{{ item.teacherSuggestion }}</n-text>
            </div>
          </div>
        </n-space>
      </n-card>

      <!-- History Section -->
      <n-card :bordered="true" title="生成历史跟踪" size="small" class="history-card">
        <template #header-extra>
           <n-form inline :model="historyFilter" label-placement="left" :show-feedback="false" size="small">
            <n-form-item label="学科">
              <n-input v-model:value="historyFilter.subject" placeholder="过滤学科" clearable @keydown.enter="applyHistoryFilters" style="width: 140px"/>
            </n-form-item>
            <n-button @click="applyHistoryFilters">
               <template #icon><RefreshCw :size="14" /></template>
               查询
            </n-button>
          </n-form>
        </template>

        <n-data-table
          remote
          :loading="aiqStore.historyLoading"
          :columns="historyColumns"
          :data="aiqStore.history"
          :pagination="historyPagination"
          :bordered="false"
          :bottom-bordered="false"
        />
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

.session-alert {
  margin-top: 8px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.question-card, .history-card {
  border-radius: 8px;
}

.answer-area {
  margin-top: 12px;
  padding: 16px;
  background-color: var(--color-bg-soft);
  border-radius: 6px;
}

.actions-footer {
  margin-top: 16px;
}

.result-card, .analysis-card {
  margin-top: 24px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-value {
  font-size: 1.5rem;
  font-weight: 700;
  font-family: var(--font-code);
}

.metric-value.success {
  color: #18a058;
}

.metric-value.highlight {
  color: #2080f0;
}

.result-detail-item {
  padding: 12px 16px;
  background-color: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.text-success { color: #18a058; }
.text-error { color: #d03050; }

.view-analysis-btn {
  margin-top: 12px;
}

.analysis-item-box {
  padding: 16px;
  background-color: #f8fafc;
  border-radius: 8px;
  border-left: 4px solid #bae0ff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-title {
  font-size: 1.05rem;
}

.analysis-badge-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.analysis-content {
  margin-top: 4px;
  padding: 12px;
  background-color: #fff;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.teacher-suggestion {
  margin-top: 4px;
  padding: 12px;
  background-color: #fffbdf;
  border-radius: 6px;
  border: 1px solid #fce8a1;
}

.analysis-label {
  font-weight: 600;
  margin-right: 4px;
}
</style>
