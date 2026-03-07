<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NButton,
  NAlert,
  NModal,
  NSpin,
  NEmpty,
  NSpace,
  NText,
  NDropdown,
  useDialog,
  useMessage
} from "naive-ui";
import { Sparkles, Edit3, Download, Share2, Trash2, Presentation, BookOpen } from "lucide-vue-next";
import MarkdownPreview from "../../components/common/MarkdownPreview.vue";
import { teacherPlanSchema } from "../../features/teacher-workspace/model/teacher.schemas";
import { usePlanStore } from "../../features/teacher-workspace/model/plans";

const teacherStore = usePlanStore();
const dialog = useDialog();
const message = useMessage();

const generateForm = reactive({
  topic: "",
  gradeLevel: "",
  durationMins: 45
});

const editor = reactive({
  planId: "",
  topic: "",
  contentMd: ""
});

const showEditorModal = ref(false);

const exportOptions = [
  { label: "Markdown (.md)", key: "md" },
  { label: "PDF (.pdf)", key: "pdf" }
];
const topicInputProps = {
  id: "plan-topic",
  name: "planTopic",
  "aria-label": "教案主题"
};
const gradeInputProps = {
  id: "plan-grade-level",
  name: "planGradeLevel",
  "aria-label": "适用年级"
};
const durationInputProps = {
  id: "plan-duration-mins",
  name: "planDurationMins",
  "aria-label": "课时分钟数"
};
const editorInputProps = {
  id: "plan-editor-content",
  name: "planEditorContent",
  "aria-label": "教案 Markdown 编辑器"
};

const latestShareText = computed(() => teacherStore.shareResult?.shareUrl || "");

async function loadPlans(): Promise<void> {
  await teacherStore.loadPlans({ page: teacherStore.plansPage, size: teacherStore.plansSize });
}

async function createPlan(): Promise<void> {
  const parsed = teacherPlanSchema.safeParse(generateForm);
  if (!parsed.success) {
    message.warning(parsed.error.issues[0]?.message || "参数不合法");
    return;
  }

  const created = await teacherStore.createPlan(parsed.data);
  if (!created) return;

  editor.planId = created.id || "";
  editor.topic = created.topic || "";
  editor.contentMd = created.contentMd || "";
  showEditorModal.value = true;

  generateForm.topic = "";
  await loadPlans();
}

function openEditor(planId: string): void {
  const target = teacherStore.plans.find((plan) => plan.id === planId);
  if (!target) return;
  editor.planId = target.id || "";
  editor.topic = target.topic || "";
  editor.contentMd = target.contentMd || "";
  showEditorModal.value = true;
}

async function savePlan(): Promise<void> {
  if (!editor.planId) return;
  const updated = await teacherStore.savePlan(editor.planId, editor.contentMd);
  if (!updated) return;
  message.success("教案已保存");
  showEditorModal.value = false;
  await loadPlans();
}

function confirmRemovePlan(planId: string): void {
  dialog.warning({
    title: "删除教案",
    content: "删除后不可恢复，确认继续吗？",
    positiveText: "删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      await teacherStore.removePlan(planId);
      if (teacherStore.operationError) {
        message.error(teacherStore.operationError);
        return;
      }
      if (editor.planId === planId) {
        editor.planId = "";
        editor.topic = "";
        editor.contentMd = "";
        showEditorModal.value = false;
      }
      message.success("教案已删除");
      await loadPlans();
    }
  });
}

async function sharePlan(planId: string): Promise<void> {
  const shareResult = await teacherStore.shareLessonPlan(planId);
  if (!shareResult) return;
  message.success("分享链接已生成");
}

async function exportCurrentPlan(planId: string, format: "md" | "pdf"): Promise<void> {
  const blob = await teacherStore.exportLessonPlan(planId, format);
  if (!blob) return;

  const fileName = `lesson-plan-${planId}.${format}`;
  const blobUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = blobUrl;
  anchor.download = fileName;
  anchor.click();
  window.URL.revokeObjectURL(blobUrl);
  message.success(`导出成功：${fileName}`);
}

onMounted(loadPlans);
</script>

<template>
  <div class="plans-page app-container">
    <div class="workspace-main">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">教案管理</h1>
          <p class="workspace-subtitle">创建、编辑、导出并分享结构化教学教案。</p>
        </div>
      </div>

      <div class="panel glass-card generator-panel">
        <n-form
          inline
          :model="generateForm"
          label-placement="left"
          :show-feedback="false"
          class="ethereal-form"
        >
          <n-form-item label="教案主题" :label-props="{ for: 'plan-topic' }">
            <n-input
              v-model:value="generateForm.topic"
              placeholder="例：牛顿第一定律"
              :input-props="topicInputProps"
              style="width: 250px"
            />
          </n-form-item>
          <n-form-item label="适用年级" :label-props="{ for: 'plan-grade-level' }">
            <n-input
              v-model:value="generateForm.gradeLevel"
              placeholder="例：高一"
              :input-props="gradeInputProps"
              style="width: 120px"
            />
          </n-form-item>
          <n-form-item label="课时(分钟)" :label-props="{ for: 'plan-duration-mins' }">
            <n-input-number
              v-model:value="generateForm.durationMins"
              :min="10"
              :max="180"
              :input-props="durationInputProps"
              style="width: 130px"
            />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" :loading="teacherStore.operationLoading" @click="createPlan">
              <template #icon><Sparkles :size="16" /></template>
              生成教案
            </n-button>
          </n-form-item>
        </n-form>
      </div>

      <n-alert v-if="teacherStore.plansError" type="error" :show-icon="true">{{
        teacherStore.plansError
      }}</n-alert>
      <n-alert v-if="teacherStore.operationError" type="error" :show-icon="true">{{
        teacherStore.operationError
      }}</n-alert>
      <n-alert v-if="latestShareText" type="success" :show-icon="true">
        分享链接：<a :href="latestShareText" target="_blank" rel="noopener noreferrer">{{
          latestShareText
        }}</a>
      </n-alert>

      <n-spin :show="teacherStore.plansLoading">
        <n-empty
          v-if="teacherStore.plansLoaded && teacherStore.plans.length === 0"
          description="暂无教案"
          class="empty-layout"
        >
          <template #icon
            ><Presentation :size="48" style="color: var(--color-border-strong)"
          /></template>
        </n-empty>

        <div v-else class="plans-grid">
          <div v-for="plan in teacherStore.plans" :key="plan.id" class="panel glass-card plan-card">
            <div class="plan-header">
              <div class="plan-icon"><Presentation :size="24" /></div>
              <div class="plan-info">
                <h3 class="plan-topic" :title="plan.topic">{{ plan.topic }}</h3>
                <span class="plan-id">{{ plan.id }}</span>
              </div>
            </div>

            <div class="plan-meta-grid">
              <span class="meta-tag">{{ plan.gradeLevel || "未指定" }}</span>
              <span class="meta-tag">{{ plan.durationMins }} min</span>
            </div>

            <div class="plan-footer">
              <div class="plan-date">{{ plan.updatedAt?.substring(0, 16) }}</div>
              <div class="plan-actions">
                <n-button
                  circle
                  size="small"
                  type="primary"
                  quaternary
                  aria-label="编辑教案"
                  title="编辑教案"
                  @click="openEditor(plan.id || '')"
                >
                  <template #icon><Edit3 :size="16" /></template>
                </n-button>
                <n-button
                  circle
                  size="small"
                  type="warning"
                  quaternary
                  aria-label="分享教案"
                  title="分享教案"
                  @click="sharePlan(plan.id || '')"
                >
                  <template #icon><Share2 :size="16" /></template>
                </n-button>
                <n-dropdown
                  :options="exportOptions"
                  @select="(fmt) => exportCurrentPlan(plan.id || '', fmt as 'md' | 'pdf')"
                >
                  <n-button
                    circle
                    size="small"
                    type="info"
                    quaternary
                    aria-label="导出教案"
                    title="导出教案"
                  >
                    <template #icon><Download :size="16" /></template>
                  </n-button>
                </n-dropdown>
                <n-button
                  circle
                  size="small"
                  type="error"
                  quaternary
                  aria-label="删除教案"
                  title="删除教案"
                  @click="confirmRemovePlan(plan.id || '')"
                >
                  <template #icon><Trash2 :size="16" /></template>
                </n-button>
              </div>
            </div>
          </div>
        </div>
      </n-spin>
    </div>

    <n-modal
      v-model:show="showEditorModal"
      preset="card"
      :title="`编辑教案：${editor.topic || editor.planId}`"
      class="editor-modal"
      :mask-closable="false"
      size="huge"
      :style="{ width: '1200px', maxWidth: '95vw', height: '85vh' }"
    >
      <div class="editor-layout">
        <div class="editor-pane">
          <label class="sr-only" for="plan-editor-content">教案 Markdown 编辑器</label>
          <n-input
            v-model:value="editor.contentMd"
            type="textarea"
            placeholder="在此编辑 Markdown"
            :input-props="editorInputProps"
            class="full-height-input"
          />
        </div>
        <div class="preview-pane">
          <div class="preview-header">
            <n-space align="center" :size="8">
              <BookOpen :size="16" style="color: var(--color-primary)" />
              <n-text strong>预览</n-text>
            </n-space>
          </div>
          <div class="preview-content">
            <MarkdownPreview :content="editor.contentMd" />
          </div>
        </div>
      </div>

      <template #action>
        <n-space
          justify="end"
          style="padding-top: 12px; border-top: 1px solid var(--color-border-glass)"
        >
          <n-button @click="showEditorModal = false">取消</n-button>
          <n-button type="primary" :loading="teacherStore.operationLoading" @click="savePlan"
            >保存</n-button
          >
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.workspace-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.generator-panel {
  padding: 20px;
}

.empty-layout {
  padding: 80px 0;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-5);
  margin-top: 16px;
}

.plan-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 1px solid var(--color-border-glass);
}

.plan-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.plan-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(92, 101, 246, 0.08);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.plan-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.plan-topic {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.plan-id {
  font-family: var(--font-code);
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.plan-meta-grid {
  display: flex;
  gap: 12px;
}

.meta-tag {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.7);
  padding: 4px 10px;
  border-radius: 12px;
}

.plan-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border-glass);
}

.plan-date {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.plan-actions {
  display: flex;
  gap: 4px;
}

.editor-layout {
  display: flex;
  height: calc(85vh - 160px);
  gap: 16px;
}

.editor-pane,
.preview-pane {
  flex: 1;
  height: 100%;
  border-radius: 12px;
  border: 1px solid var(--color-border-glass);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.full-height-input {
  flex: 1;
  height: 100%;
}

:deep(.full-height-input .n-input-wrapper) {
  height: 100%;
  padding: 0;
}

:deep(.full-height-input textarea) {
  height: 100% !important;
  font-family: var(--font-code);
  font-size: 14px;
  line-height: 1.6;
  padding: 20px;
  resize: none;
}

.preview-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border-glass);
}

.preview-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
</style>
