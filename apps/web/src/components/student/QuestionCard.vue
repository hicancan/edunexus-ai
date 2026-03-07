<script setup lang="ts">
import { computed } from "vue";
import { NCheckbox, NCheckboxGroup, NInput, NRadio, NRadioGroup, NSpace } from "naive-ui";
import type { QuestionVO } from "../../services/contracts";
import { formatQuestionOption } from "../../features/student/model/question-options";

const props = defineProps<{
  question: QuestionVO;
  index: number;
  modelValue: string;
}>();

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();

const isShortAnswer = computed(() => props.question.questionType === "SHORT_ANSWER");
const isMultipleChoice = computed(() => props.question.questionType === "MULTIPLE_CHOICE");
const radioGroupName = computed(() => `answer-group-${props.question.id || props.index}`);
const questionTypeLabel = computed(() => {
  if (props.question.questionType === "SHORT_ANSWER") return "简答题";
  if (props.question.questionType === "MULTIPLE_CHOICE") return "多选题";
  return "单选题";
});
const multipleChoiceValue = computed(() =>
  Array.from(
    new Set(
      (props.modelValue || "")
        .toUpperCase()
        .replace(/[^A-Z]/g, "")
        .split("")
        .filter(Boolean)
    )
  ).sort()
);
const shortAnswerInputProps = computed(() => ({
  id: `short-answer-${props.question.id || props.index}`,
  name: `shortAnswer-${props.question.id || props.index}`,
  "aria-label": `第${props.index}题作答输入框`
}));

function updateMultipleChoice(values: Array<string | number>): void {
  const normalized = Array.from(
    new Set(values.map((item) => String(item).toUpperCase()).filter(Boolean))
  )
    .sort()
    .join("");
  emit("update:modelValue", normalized);
}
</script>

<template>
  <div class="panel glass-card question-card">
    <div class="q-header">
      <div class="q-title-area">
        <span class="q-index">{{ index }}</span>
        <span class="q-content">{{ question.content }}</span>
      </div>
      <div class="q-tags">
        <span class="glass-pill tag-type">{{ questionTypeLabel }}</span>
        <span class="glass-pill tag-diff" :class="'diff-' + question.difficulty?.toLowerCase()">{{
          question.difficulty
        }}</span>
        <span class="glass-pill tag-score">{{ question.score }} 分</span>
        <template v-if="question.knowledgePoints && question.knowledgePoints.length > 0">
          <span v-for="kp in question.knowledgePoints" :key="kp" class="glass-pill tag-kp"
            >🎯 {{ kp }}</span
          >
        </template>
      </div>
    </div>

    <div class="answer-area">
      <n-checkbox-group
        v-if="isMultipleChoice"
        :value="multipleChoiceValue"
        @update:value="updateMultipleChoice"
      >
        <n-space vertical :size="16" class="radio-options">
          <n-checkbox
            v-for="(val, key) in question.options || {}"
            :key="key"
            :value="key"
            class="ethereal-radio"
          >
            <span class="option-key">{{ key }}</span>
            <span class="option-val">{{ formatQuestionOption(val) }}</span>
          </n-checkbox>
        </n-space>
      </n-checkbox-group>

      <n-radio-group
        v-else-if="!isShortAnswer"
        :value="modelValue"
        :name="radioGroupName"
        @update:value="(v) => emit('update:modelValue', v)"
      >
        <n-space vertical :size="16" class="radio-options">
          <n-radio
            v-for="(val, key) in question.options || {}"
            :key="key"
            :value="key"
            class="ethereal-radio"
          >
            <span class="option-key">{{ key }}</span>
            <span class="option-val">{{ formatQuestionOption(val) }}</span>
          </n-radio>
        </n-space>
      </n-radio-group>

      <div v-else class="multimodal-answer-box">
        <n-input
          :value="modelValue"
          type="textarea"
          placeholder="请填写推导过程与最终结论"
          :autosize="{ minRows: 2, maxRows: 6 }"
          :input-props="shortAnswerInputProps"
          class="transparent-input"
          @update:value="(v) => emit('update:modelValue', v)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
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

.tag-diff.diff-easy {
  color: var(--color-success);
  border-color: rgba(16, 185, 129, 0.3);
}
.tag-diff.diff-medium {
  color: var(--color-warning);
  border-color: rgba(245, 158, 11, 0.3);
}
.tag-diff.diff-hard {
  color: var(--color-danger);
  border-color: rgba(239, 68, 68, 0.3);
}

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
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px);
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--color-border-glass);
  width: 100%;
  transition: var(--transition-smooth);
}

.ethereal-radio:hover {
  background: rgba(255, 255, 255, 0.6);
  border-color: rgba(92, 101, 246, 0.3);
}

:deep(.ethereal-radio .n-checkbox__label),
:deep(.ethereal-radio .n-radio__label) {
  width: 100%;
}

.option-key {
  font-weight: 700;
  color: var(--color-primary);
  margin-right: 8px;
}

.multimodal-answer-box {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid var(--color-border-glass);
  border-radius: 16px;
  transition: box-shadow 0.3s ease;
  padding: 0 16px;
}

.multimodal-answer-box:focus-within {
  border-color: rgba(92, 101, 246, 0.5);
  box-shadow: 0 0 0 3px rgba(92, 101, 246, 0.1);
}

.transparent-input {
  flex: 1;
  background: transparent;
}

:deep(.transparent-input .n-input__textarea-el) {
  padding: 16px 0;
}

@media (max-width: 768px) {
  .q-header {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
