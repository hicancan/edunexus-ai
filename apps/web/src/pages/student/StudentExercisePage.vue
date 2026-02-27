<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
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
  NPagination,
  NRadioGroup,
  NRadio,
  NTag,
  NDivider,
  NUpload,
  NImage,
  NTooltip,
  useMessage
} from "naive-ui";
import { CheckCircle, AlertCircle, RefreshCw, Send, Search, Image as ImageIcon, Trash2, ShieldQuestion } from "lucide-vue-next";
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
  { label: "全维度", value: "" },
  { label: "基础认知 (EASY)", value: "EASY" },
  { label: "进阶推演 (MEDIUM)", value: "MEDIUM" },
  { label: "极限挑战 (HARD)", value: "HARD" }
];

const answers = ref<Record<string, string>>({});
const uploadedImages = ref<Record<string, string[]>>({});
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
  uploadedImages.value = {};
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

function handleImageUpload(questionId: string, options: { fileList: any[] }): void {
  if (!uploadedImages.value[questionId]) {
    uploadedImages.value[questionId] = [];
  }
  
  for (const item of options.fileList) {
    const file = item.file;
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        if (e.target?.result && typeof e.target.result === "string") {
          uploadedImages.value[questionId].push(e.target.result);
        }
      };
      reader.readAsDataURL(file);
    }
  }
}

function removeImage(questionId: string, index: number): void {
  if (uploadedImages.value[questionId]) {
    uploadedImages.value[questionId].splice(index, 1);
  }
}

async function submitExercise(): Promise<void> {
  formError.value = "";

  const payload = exerciseStore.questions
    .filter((question) => Boolean(question.id))
    .map((question) => {
      const qId = question.id as string;
      const textAnswer = answers.value[qId] || "";
      const images = uploadedImages.value[qId] || [];
      
      const finalAnswer = images.length > 0 
        ? `[附带 ${images.length} 张图片解题步骤]\n${textAnswer}`
        : textAnswer;
        
      return {
        questionId: qId,
        userAnswer: finalAnswer
      };
    });

  if (payload.length === 0) {
    message.warning("虚空中没有发现任何试题");
    return;
  }

  const hasBlank = payload.some((item) => !item.userAnswer.trim());
  if (hasBlank) {
    message.warning("协议中断：请完成所有试题推演后再提交链接");
    return;
  }

  const result = await exerciseStore.submitAnswers(payload);
  if (result?.recordId) {
    selectedAnalysisRecordId.value = result.recordId;
    message.success("跨模态提交成功，Nexus Agent 解析已就绪");
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

onMounted(loadQuestions);

function getDifficultyType(diff: string): "success" | "warning" | "error" | "info" {
  if (diff === "EASY") return "success";
  if (diff === "MEDIUM") return "warning";
  if (diff === "HARD") return "error";
  return "info";
}
</script>

<template>
  <div class="exercise-page app-container">
    <div class="workspace-stack">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">多模态试压场</h1>
          <p class="workspace-subtitle">按核心知识流形抽取题目，支持公式图片上传与 Qwen3-VL 智能判卷。</p>
        </div>
      </div>

      <div class="panel glass-card search-panel">
        <n-form inline :model="filters" label-placement="left" :show-feedback="false" class="ethereal-form">
          <n-form-item label="学科流形">
             <n-input v-model:value="filters.subject" placeholder="例如：物理、几何" clearable @keydown.enter="loadQuestions" />
          </n-form-item>
          <n-form-item label="推演难度">
            <n-select v-model:value="filters.difficulty" :options="difficultyOptions" style="width: 180px" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" class="animate-pop glass-pill-btn" :loading="exerciseStore.questionsLoading" @click="loadQuestions">
               <template #icon>
                <Search :size="16" />
              </template>
              检索题海矩阵
            </n-button>
          </n-form-item>
        </n-form>
      </div>

      <n-alert v-if="exerciseStore.questionsError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ exerciseStore.questionsError }}</n-alert>
      <n-alert v-if="exerciseStore.submitError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ exerciseStore.submitError }}</n-alert>

      <n-spin :show="exerciseStore.questionsLoading">
        <div v-if="exerciseStore.questionsLoaded && exerciseStore.questions.length === 0" class="empty-state">
          <ShieldQuestion :size="48" class="empty-icon" />
          <n-text depth="3">当前坐标系未锚定任何试题，请调整检索流形参量。</n-text>
        </div>
        
        <div v-else class="question-list">
          <div
            v-for="(question, index) in exerciseStore.questions"
            :key="question.id"
            class="panel glass-card question-card"
          >
            <div class="q-header">
              <div class="q-title-area">
                <span class="q-index">{{ (filters.page - 1) * filters.size + index + 1 }}</span>
                <span class="q-content">{{ question.content }}</span>
              </div>
              <div class="q-tags">
                 <span class="glass-pill tag-type">{{ question.questionType === "SHORT_ANSWER" ? "推演题" : "定轨题" }}</span>
                 <span class="glass-pill tag-diff" :class="'diff-' + question.difficulty?.toLowerCase()">{{ question.difficulty }}</span>
                 <span class="glass-pill tag-score">{{ question.score }} 分能盘</span>
                 <template v-if="question.knowledgePoints && question.knowledgePoints.length > 0">
                   <span class="glass-pill tag-kp" v-for="kp in question.knowledgePoints" :key="kp">🎯 {{ kp }}</span>
                 </template>
              </div>
            </div>

            <div class="answer-area">
              <n-radio-group
                v-if="question.questionType !== 'SHORT_ANSWER'"
                v-model:value="answers[question.id as string]"
                name="answer-group"
              >
                <n-space vertical :size="16" class="radio-options">
                  <n-radio v-for="(value, key) in question.options || {}" :key="key" :value="key" class="ethereal-radio">
                    <span class="option-key">{{ key }}</span> <span class="option-val">{{ value }}</span>
                  </n-radio>
                </n-space>
              </n-radio-group>
              
              <div v-else class="multimodal-answer-box">
                 <!-- Image Upload Tray -->
                 <div class="image-preview-tray" v-if="uploadedImages[question.id as string]?.length > 0">
                    <div class="preview-item animate-pop" v-for="(imgSrc, idx) in uploadedImages[question.id as string]" :key="idx">
                      <n-image :src="imgSrc" object-fit="cover" class="preview-img" />
                      <button class="remove-img-btn" @click="removeImage(question.id as string, idx)">
                        <Trash2 :size="12" />
                      </button>
                    </div>
                 </div>

                 <div class="input-actions-row">
                    <div class="upload-trigger-container">
                      <n-upload
                        abstract
                        accept="image/*"
                        :default-upload="false"
                        :show-file-list="false"
                        multiple
                        @change="(opts) => handleImageUpload(question.id as string, opts)"
                      >
                        <n-upload-trigger #="{ handleClick }" abstract>
                           <n-tooltip placement="top">
                             <template #trigger>
                               <n-button quaternary circle class="upload-btn animate-pop" @click="handleClick">
                                 <template #icon><ImageIcon :size="20" style="color: var(--color-primary)" /></template>
                               </n-button>
                             </template>
                             上传解题步骤照片 (多模态 Qwen3-VL 识别)
                           </n-tooltip>
                        </n-upload-trigger>
                      </n-upload>
                    </div>

                    <n-input
                      v-model:value="answers[question.id as string]"
                      type="textarea"
                      placeholder="在此构建逻辑推演，或直接投喂解题图片..."
                      :autosize="{ minRows: 2, maxRows: 6 }"
                      class="transparent-input"
                    />
                 </div>
              </div>
            </div>
          </div>

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
              <template #icon>
                <Send :size="18" />
              </template>
              提交本页推演至 Nexus Core
            </n-button>
          </div>
        </div>
      </n-spin>

      <div v-if="exerciseStore.latestResult" class="panel glass-card result-card">
        <h3 class="panel-title" style="margin-bottom: 24px">AI 量子共鸣分析报告</h3>
        <div class="metric-grid">
          <div class="metric-glass">
            <span class="metric-label">采样总轨</span>
            <span class="metric-value">{{ exerciseStore.latestResult.totalQuestions }}</span>
          </div>
          <div class="metric-glass success-glass">
            <span class="metric-label">完美拟合</span>
            <span class="metric-value">{{ exerciseStore.latestResult.correctCount }}</span>
          </div>
          <div class="metric-glass highlight-glass">
            <span class="metric-label">获取能盘</span>
            <span class="metric-value">{{ exerciseStore.latestResult.totalScore }}</span>
          </div>
        </div>
        
        <div class="result-details-stack">
          <div v-for="item in exerciseStore.latestResult.items || []" :key="item.questionId" class="result-detail-item glass-pill-box">
             <div class="detail-header">
                <span class="detail-name">推演节点 {{ item.questionId?.substring(0,6) }}...</span>
                <component :is="item.isCorrect ? CheckCircle : AlertCircle" :size="20" :class="item.isCorrect ? 'text-success' : 'text-danger'" />
             </div>
             <div class="detail-body">
                <div class="detail-row">
                  <span class="detail-label">观测坍缩态(你的答案):</span> 
                  <span class="detail-value" :class="item.isCorrect ? 'text-success' : 'text-danger'">{{ item.userAnswer }}</span>
                </div>
                <div class="detail-row" v-if="!item.isCorrect">
                  <span class="detail-label">真理域值:</span> 
                  <span class="detail-value text-success">{{ item.correctAnswer }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">本次吸收能盘:</span> 
                  <span class="detail-value font-code">{{ item.score }} Pt</span>
                </div>
             </div>
          </div>
        </div>
        
        <div class="result-actions">
          <n-button
            ghost
            type="primary"
            class="animate-pop"
            @click="viewAnalysis(exerciseStore.latestResult?.recordId || '')"
          >
            开启深层脑暴解析
          </n-button>
        </div>
      </div>

      <n-alert v-if="exerciseStore.analysisError" type="error" :show-icon="true" style="border-radius: var(--radius-md)">{{ exerciseStore.analysisError }}</n-alert>
      
      <div v-if="currentAnalysis" class="panel glass-card analysis-card">
        <h3 class="panel-title">Nexus Agent 深度解析场</h3>
        <div class="analysis-stack">
          <div v-for="(item, index) in currentAnalysis.items || []" :key="item.questionId" class="analysis-item-box">
            <div class="a-header">
               <span class="a-title">节点 {{ index + 1 }}: {{ item.content }}</span>
               <span class="glass-pill a-badge" :class="item.isCorrect ? 'a-success' : 'a-error'">{{ item.isCorrect ? "拟合成功" : "轨迹偏航" }}</span>
            </div>
            
            <div class="a-body">
              <div class="a-content-box">
                 <h4 class="a-label">【思维网络解析模型】</h4>
                 <p class="a-text">{{ item.analysis || "信息缺失：维度坍缩" }}</p>
              </div>
              <div v-if="item.teacherSuggestion" class="a-content-box suggestion-box">
                 <h4 class="a-label warn-color">【导师人工干预】</h4>
                 <p class="a-text warn-color">{{ item.teacherSuggestion }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
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

.question-card {
  padding: 0;
  overflow: hidden;
}

.q-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.3);
  border-bottom: 1px solid var(--color-border-glass);
}

.q-title-area {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--color-text-main);
  line-height: 1.5;
  display: flex;
  gap: 12px;
}

.q-index {
  color: var(--color-primary);
  font-family: var(--font-code);
}

.q-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.q-tags .glass-pill {
  padding: 4px 12px;
  font-size: 0.85rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.tag-diff.diff-easy { color: var(--color-success); border-color: rgba(16, 185, 129, 0.3); }
.tag-diff.diff-medium { color: var(--color-warning); border-color: rgba(245, 158, 11, 0.3); }
.tag-diff.diff-hard { color: var(--color-danger); border-color: rgba(239, 68, 68, 0.3); }

.tag-score {
  background: linear-gradient(135deg, var(--color-primary), #60a5fa);
  color: white;
  border: none;
}

.tag-kp {
  color: #8b5cf6;
  border-color: rgba(139, 92, 246, 0.3);
  background: rgba(139, 92, 246, 0.05);
}

.answer-area {
  padding: 24px;
}

.ethereal-radio {
  background: rgba(255,255,255,0.4);
  backdrop-filter: blur(8px);
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--color-border-glass);
  width: 100%;
  transition: var(--transition-smooth);
}

.ethereal-radio:hover {
  background: rgba(255,255,255,0.6);
  border-color: rgba(92, 101, 246, 0.3);
}

.option-key {
  font-weight: 700;
  color: var(--color-primary);
  margin-right: 8px;
}

/* Multimodal Input */
.multimodal-answer-box {
  background: rgba(255,255,255,0.6);
  border: 1px solid var(--color-border-glass);
  border-radius: 16px;
  overflow: hidden;
  transition: box-shadow 0.3s ease;
}

.multimodal-answer-box:focus-within {
  border-color: rgba(92, 101, 246, 0.5);
  box-shadow: 0 0 0 3px rgba(92, 101, 246, 0.1);
}

.image-preview-tray {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(0,0,0,0.02);
  border-bottom: 1px dashed var(--color-border-glass);
}

.preview-item {
  position: relative;
  width: 70px;
  height: 70px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-glass);
}

.preview-img {
  width: 100%;
  height: 100%;
}

.remove-img-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(239, 68, 68, 0.9);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.input-actions-row {
  display: flex;
  align-items: flex-start;
}

.upload-trigger-container {
  padding: 12px;
}

.upload-btn {
  background: rgba(255,255,255,0.5);
}

.transparent-input {
  flex: 1;
  background: transparent;
}

:deep(.transparent-input .n-input__textarea-el) {
  padding: 16px 16px 16px 0;
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

/* Results Grid */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.metric-glass {
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid var(--color-border-glass);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.metric-label {
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 0.9rem;
}

.metric-value {
  font-size: 2.2rem;
  font-weight: 800;
  font-family: var(--font-code);
  color: var(--color-text-main);
  line-height: 1;
}

.success-glass { background: rgba(16, 185, 129, 0.1); border-color: rgba(16, 185, 129, 0.2); }
.success-glass .metric-value { color: var(--color-success); }

.highlight-glass { background: rgba(92, 101, 246, 0.1); border-color: rgba(92, 101, 246, 0.2); }
.highlight-glass .metric-value { color: var(--color-primary); }

.result-details-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-detail-item {
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--color-border-glass);
  border-radius: 12px;
  padding: 16px 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-name {
  font-weight: 700;
  font-size: 1.1rem;
}

.detail-body {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.95rem;
}

.detail-label {
  color: var(--color-text-muted);
}

.detail-value {
  font-weight: 600;
}

.font-code { font-family: var(--font-code); }

.text-success { color: var(--color-success); }
.text-danger { color: var(--color-danger); }

.result-actions {
  margin-top: 24px;
  text-align: right;
}

/* Analysis Section */
.analysis-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
}

.analysis-item-box {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid var(--color-border-glass);
  border-left: 4px solid var(--color-primary);
  border-radius: 16px;
  padding: 20px;
}

.a-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.a-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-text-main);
}

.a-badge.a-success { background: rgba(16, 185, 129, 0.15); color: var(--color-success); border: none; }
.a-badge.a-error { background: rgba(239, 68, 68, 0.15); color: var(--color-danger); border: none; }

.a-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.a-content-box {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  padding: 16px;
}

.a-label {
  margin: 0 0 8px 0;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.a-text {
  margin: 0;
  line-height: 1.6;
}

.suggestion-box {
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.warn-color {
  color: var(--color-warning);
}

@media (max-width: 768px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
  
  .q-header {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
