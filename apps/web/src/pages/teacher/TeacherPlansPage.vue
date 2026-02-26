<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import MarkdownPreview from "../../components/common/MarkdownPreview.vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { teacherPlanSchema } from "../../schemas/teacher.schemas";
import { useTeacherStore } from "../../stores/teacher";

const teacherStore = useTeacherStore();

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

const exportFormat = ref<"md" | "pdf">("md");
const formError = ref("");
const operationSuccess = ref("");

const latestShareText = computed(() => {
  if (!teacherStore.shareResult) {
    return "";
  }
  return `${teacherStore.shareResult.shareUrl || ""}`;
});

async function loadPlans(): Promise<void> {
  await teacherStore.loadPlans({
    page: teacherStore.plansPage,
    size: teacherStore.plansSize
  });
}

async function createPlan(): Promise<void> {
  formError.value = "";
  operationSuccess.value = "";

  const parsed = teacherPlanSchema.safeParse(generateForm);
  if (!parsed.success) {
    formError.value = parsed.error.issues[0]?.message || "教案参数不合法";
    return;
  }

  const created = await teacherStore.createPlan(parsed.data);
  if (!created) {
    return;
  }

  editor.planId = created.id || "";
  editor.topic = created.topic || "";
  editor.contentMd = created.contentMd || "";
  operationSuccess.value = "教案已生成，可继续编辑后保存。";
  await teacherStore.loadPlans({ page: 1, size: teacherStore.plansSize });
}

function openEditor(planId: string): void {
  const target = teacherStore.plans.find((plan) => plan.id === planId);
  if (!target) {
    return;
  }
  editor.planId = target.id || "";
  editor.topic = target.topic || "";
  editor.contentMd = target.contentMd || "";
}

async function savePlan(): Promise<void> {
  if (!editor.planId) {
    formError.value = "请先选择教案后再保存";
    return;
  }
  operationSuccess.value = "";
  const updated = await teacherStore.savePlan(editor.planId, editor.contentMd);
  if (updated) {
    operationSuccess.value = "教案保存成功";
    await loadPlans();
  }
}

async function removePlan(planId: string): Promise<void> {
  if (!planId || !window.confirm("确认删除该教案吗？")) {
    return;
  }

  await teacherStore.removePlan(planId);
  if (editor.planId === planId) {
    editor.planId = "";
    editor.topic = "";
    editor.contentMd = "";
  }
  await loadPlans();
}

async function sharePlan(planId: string): Promise<void> {
  operationSuccess.value = "";
  const shareResult = await teacherStore.shareLessonPlan(planId);
  if (shareResult) {
    operationSuccess.value = "教案分享链接已生成";
  }
}

async function exportCurrentPlan(planId: string): Promise<void> {
  operationSuccess.value = "";
  const blob = await teacherStore.exportLessonPlan(planId, exportFormat.value);
  if (!blob) {
    return;
  }

  const fileName = `lesson-plan-${planId}.${exportFormat.value}`;
  const blobUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = blobUrl;
  anchor.download = fileName;
  anchor.click();
  window.URL.revokeObjectURL(blobUrl);
  operationSuccess.value = `导出成功：${fileName}`;
}

async function updatePage(page: number): Promise<void> {
  await teacherStore.loadPlans({ page, size: teacherStore.plansSize });
}

async function updateSize(size: number): Promise<void> {
  await teacherStore.loadPlans({ page: 1, size });
}

onMounted(loadPlans);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">教案管理</h2>
        <p class="panel-note">支持教案生成、编辑、保存、导出与分享。</p>
      </div>
      <button class="btn" type="button" :disabled="teacherStore.operationLoading" @click="createPlan">
        {{ teacherStore.operationLoading ? "处理中..." : "生成教案" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="plan-topic">主题</label>
        <input id="plan-topic" v-model="generateForm.topic" placeholder="例如：牛顿定律" />
      </div>
      <div class="field-block">
        <label for="plan-grade">年级</label>
        <input id="plan-grade" v-model="generateForm.gradeLevel" placeholder="例如：高一" />
      </div>
      <div class="field-block">
        <label for="plan-duration">课时（分钟）</label>
        <input id="plan-duration" v-model.number="generateForm.durationMins" type="number" min="10" max="180" />
      </div>
      <div class="field-block">
        <label for="plan-export-format">导出格式</label>
        <select id="plan-export-format" v-model="exportFormat">
          <option value="md">Markdown</option>
          <option value="pdf">PDF</option>
        </select>
      </div>
    </div>

    <p v-if="formError" class="status-box error" role="alert">{{ formError }}</p>
    <p v-if="teacherStore.plansError" class="status-box error" role="alert">{{ teacherStore.plansError }}</p>
    <p v-if="teacherStore.operationError" class="status-box error" role="alert">{{ teacherStore.operationError }}</p>
    <p v-if="operationSuccess" class="status-box success">{{ operationSuccess }}</p>
    <p v-if="latestShareText" class="status-box info">分享链接：{{ latestShareText }}</p>

    <div v-if="teacherStore.plansLoading && !teacherStore.plansLoaded" class="status-box info">正在加载教案...</div>
    <div v-else-if="teacherStore.plans.length === 0" class="status-box empty">暂无教案记录。</div>
    <div v-else class="list-stack">
      <article v-for="plan in teacherStore.plans" :key="plan.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ plan.topic }}</p>
          <p class="list-item-meta">教案 ID：{{ plan.id }} · 年级：{{ plan.gradeLevel }} · 时长：{{ plan.durationMins }} 分钟 · 更新：{{ plan.updatedAt }}</p>
        </div>
        <div class="list-item-actions">
          <button class="btn ghost small" type="button" @click="openEditor(plan.id || '')">编辑</button>
          <button class="btn secondary small" type="button" @click="exportCurrentPlan(plan.id || '')">导出</button>
          <button class="btn warning small" type="button" @click="sharePlan(plan.id || '')">分享</button>
          <button class="btn danger small" type="button" @click="removePlan(plan.id || '')">删除</button>
        </div>
      </article>

      <PaginationBar
        :page="teacherStore.plansPage"
        :size="teacherStore.plansSize"
        :total-pages="teacherStore.plansTotalPages"
        :total-elements="teacherStore.plansTotalElements"
        :disabled="teacherStore.plansLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>

    <section v-if="editor.planId" class="editor-block">
      <header class="panel-head">
        <div>
          <h3 class="panel-title">编辑教案：{{ editor.topic || editor.planId }}</h3>
          <p class="panel-note">Markdown 内容会先净化再渲染预览，避免 XSS 注入。</p>
        </div>
        <button class="btn success" type="button" :disabled="teacherStore.operationLoading" @click="savePlan">
          {{ teacherStore.operationLoading ? "保存中..." : "保存教案" }}
        </button>
      </header>
      <textarea v-model="editor.contentMd" rows="12" aria-label="教案内容编辑器" />
      <div class="preview-block">
        <h4>Markdown 预览</h4>
        <MarkdownPreview :content="editor.contentMd" />
      </div>
    </section>
  </section>
</template>

<style scoped>
.editor-block {
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  padding-top: 14px;
}

.preview-block {
  margin-top: 12px;
}

.preview-block h4 {
  margin: 0 0 8px;
}
</style>
