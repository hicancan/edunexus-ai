<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NSelect,
  NButton,
  NSpace,
  NText,
  NAlert,
  NTag,
  NModal,
  NSpin,
  NEmpty,
  useDialog,
  useMessage
} from "naive-ui";
import { Sparkles, Edit3, Download, Share2, Trash2, BookOpen, Clock, Presentation, GraduationCap, Link, Library, Filter } from "lucide-vue-next";
import MarkdownPreview from "../../components/common/MarkdownPreview.vue";
import { teacherPlanSchema } from "../../features/teacher-workspace/model/teacher.schemas";
import { useTeacherStore } from "../../features/teacher-workspace/model/teacher";

const teacherStore = useTeacherStore();
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

const exportFormat = ref<"md" | "pdf">("md");
const formError = ref("");
const operationSuccess = ref("");
const showEditorModal = ref(false);

const showQuestionBank = ref(false);
const qbFilters = reactive({ chapter: "", difficulty: "" });
const mockBank = [
  { id: 1, content: "如何理解牛顿第一定律在惯性系中的适用条件？", difficulty: "HARD", chapter: "第一章" },
  { id: 2, content: "一个质量为m的物体在光滑水平面上受力为F，求加速度。", difficulty: "EASY", chapter: "第二章" },
  { id: 3, content: "简述动量守恒的条件及应用场景。", difficulty: "MEDIUM", chapter: "第三章" },
  { id: 4, content: "匀速圆周运动的向心力公式推导过程是？", difficulty: "HARD", chapter: "第四章" }
];
const selectedQbIds = ref<number[]>([]);

function addToCart(id: number) {
  if (!selectedQbIds.value.includes(id)) selectedQbIds.value.push(id);
}
function removeFromCart(id: number) {
  selectedQbIds.value = selectedQbIds.value.filter(i => i !== id);
}
function confirmQBCart() {
  const selectedDetails = mockBank.filter(q => selectedQbIds.value.includes(q.id));
  if (selectedDetails.length > 0) {
    const topics = selectedDetails.map(q => q.content).join("；");
    generateForm.topic = `结合以下核心题型出卷：${topics}`.substring(0, 100);
    message.success(`已挂载 ${selectedDetails.length} 道靶向题源！`);
  }
  showQuestionBank.value = false;
}

const latestShareText = computed(() => {
  if (!teacherStore.shareResult) {
    return "";
  }
  return `${teacherStore.shareResult.shareUrl || ""}`;
});

async function loadPlans(): Promise<void> {
  await teacherStore.loadPlans({
    page: pagination.page,
    size: pagination.pageSize
  });
}

function syncCartToForm() {
  if (teacherStore.examCart.length > 0 && !generateForm.topic) {
    const docNames = teacherStore.examCart.map(d => d.filename?.split('.')[0] || '未知文档').join('、');
    generateForm.topic = `高维组阵推演: ${docNames}`.slice(0, 60);
  }
}

async function createPlan(): Promise<void> {
  formError.value = "";
  operationSuccess.value = "";

  const parsed = teacherPlanSchema.safeParse(generateForm);
  if (!parsed.success) {
    message.warning(parsed.error.issues[0]?.message || "生成参数不符合拓扑规范");
    return;
  }

  // Prepend metadata about cart items if any
  let finalTopic = parsed.data.topic;
  if (teacherStore.examCart.length > 0 && !finalTopic.includes("关联底源:")) {
      const docIds = teacherStore.examCart.map(d => d.id).join(',');
      finalTopic = `${finalTopic} [关联底源:${docIds}]`;
  }

  const created = await teacherStore.createPlan({
    ...parsed.data,
    topic: finalTopic
  });
  if (!created) {
    return;
  }

  editor.planId = created.id || "";
  editor.topic = created.topic || "";
  editor.contentMd = created.contentMd || "";
  
  // Clear the cart after successful generation
  if (teacherStore.examCart.length > 0) {
    teacherStore.clearCart();
    message.success("组阵池消耗完毕，教案原型已坍缩生成");
  } else {
    message.success("AI 教学切片已火速生成，可继续编辑");
  }
  
  showEditorModal.value = true;
  generateForm.topic = ""; // Reset form
  await loadPlans();
}

function openEditor(planId: string): void {
  const target = teacherStore.plans.find((plan) => plan.id === planId);
  if (!target) {
    return;
  }
  editor.planId = target.id || "";
  editor.topic = target.topic || "";
  editor.contentMd = target.contentMd || "";
  showEditorModal.value = true;
}

async function savePlan(): Promise<void> {
  if (!editor.planId) {
    message.warning("请锁定切片实体后再保存");
    return;
  }
  const updated = await teacherStore.savePlan(editor.planId, editor.contentMd);
  if (updated) {
    message.success("教学切片拓扑已重构并封存");
    showEditorModal.value = false;
    await loadPlans();
  }
}

function confirmRemovePlan(planId: string): void {
  dialog.warning({
    title: "抹除切片数据",
    content: "确认抹除该教案分身吗？其占用的向量态将不可逆注销。",
    positiveText: "强制湮灭",
    negativeText: "维持闭合",
    onPositiveClick: async () => {
       try {
         await teacherStore.removePlan(planId);
         if (editor.planId === planId) {
           editor.planId = "";
           editor.topic = "";
           editor.contentMd = "";
           showEditorModal.value = false;
         }
         message.success("切片分身已成功湮灭");
         await loadPlans();
       } catch {
         message.error("湮灭指令失效");
       }
    }
  });
}

function closeEditor(): void {
   dialog.info({
      title: "跃出编辑态",
      content: "系统检测到未封装的微扰，直接跃出将遗失数据。是否确认放弃？",
      positiveText: "放弃微扰",
      negativeText: "继续重排",
      onPositiveClick: () => {
         showEditorModal.value = false;
      }
   });
}

async function sharePlan(planId: string): Promise<void> {
  operationSuccess.value = "";
  const shareResult = await teacherStore.shareLessonPlan(planId);
  if (shareResult) {
    message.success("全息通讯锚点已生成");
  }
}

async function exportCurrentPlan(planId: string): Promise<void> {
  operationSuccess.value = "";
  const blob = await teacherStore.exportLessonPlan(planId, exportFormat.value);
  if (!blob) {
    return;
  }

  const fileName = `hologram-plan-${planId}.${exportFormat.value}`;
  const blobUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = blobUrl;
  anchor.download = fileName;
  anchor.click();
  window.URL.revokeObjectURL(blobUrl);
  message.success(`物质化导出完毕：${fileName}`);
}

const pagination = reactive({
  page: teacherStore.plansPage,
  pageSize: teacherStore.plansSize,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page;
    loadPlans();
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    loadPlans();
  }
});

onMounted(() => {
  syncCartToForm();
  loadPlans();
});

watch(() => teacherStore.examCart.length, syncCartToForm);
</script>

<template>
  <div class="plans-page app-container">
    <div class="workspace-main">
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">智能教案孵化舱</h1>
          <p class="workspace-subtitle">定义推演参数，触发大模型进行知识图谱编织，秒级生成结构化与可视化的教学切片。</p>
        </div>
      </div>

      <n-alert v-if="teacherStore.examCart.length > 0" type="info" class="cart-alert">
         <template #icon><Sparkles /></template>
         发现高维组阵池挂载了 <b>{{ teacherStore.examCart.length }}</b> 个知识源！本次孵化将利用这些底源进行交叉推演，极速生成定制化考核/教学材料。
      </n-alert>

      <div class="panel glass-card generator-panel">
        <div class="generator-content">
          <div class="generator-banner">
             <div class="banner-icon-bg"><Sparkles :size="28" /></div>
             <div>
                <h3 style="margin: 0; font-size: 1.1rem; color: var(--color-primary);">敏捷跃迁式生成</h3>
                <span style="font-size: 0.85rem; color: var(--color-text-muted);">注入你的核心概念锚点，启动孵化引擎。</span>
             </div>
          </div>
          <n-form inline :model="generateForm" label-placement="left" :show-feedback="false" class="ethereal-form">
            <n-form-item label="教学课题中心" class="topic-item">
               <n-input v-model:value="generateForm.topic" placeholder="例：牛顿第一定律及其向量应用" style="width: 250px" class="transparent-input" />
            </n-form-item>
            <n-form-item label="适用层级">
               <n-input v-model:value="generateForm.gradeLevel" placeholder="例：高一" style="width: 120px" class="transparent-input" />
            </n-form-item>
            <n-form-item label="干涉时序(分钟)">
               <n-input-number v-model:value="generateForm.durationMins" :min="10" :max="180" style="width: 130px" />
            </n-form-item>
            <n-form-item label="物质化介质">
               <n-select v-model:value="exportFormat" :options="[{label:'Markdown 拓扑', value:'md'}, {label:'PDF 快照', value:'pdf'}]" style="width: 160px" />
            </n-form-item>
             <n-form-item>
               <n-button type="primary" :loading="teacherStore.operationLoading" @click="createPlan" class="animate-pop generate-btn">
                 <template #icon><Sparkles :size="16" /></template>
                 启动坍缩孵化
               </n-button>
               <n-button type="info" ghost class="animate-pop generate-btn" @click="showQuestionBank = true" style="margin-left: 8px">
                 <template #icon><Library :size="15" /></template>
                 题源中心注入
               </n-button>
             </n-form-item>
          </n-form>
        </div>
      </div>

      <n-alert v-if="teacherStore.plansError" type="error" :show-icon="true">{{ teacherStore.plansError }}</n-alert>
      <n-alert v-if="teacherStore.operationError" type="error" :show-icon="true">{{ teacherStore.operationError }}</n-alert>
      <n-alert v-if="latestShareText" type="success" :show-icon="true" class="share-alert">
         全息通讯锚点已接通：<a :href="latestShareText" target="_blank">{{ latestShareText }}</a>
      </n-alert>

      <n-spin :show="teacherStore.plansLoading">
         <n-empty v-if="teacherStore.plansLoaded && teacherStore.plans.length === 0" description="舱体空载中，未检索到教学切片" class="empty-layout">
             <template #icon><Presentation :size="48" style="color: var(--color-border-strong)"/></template>
         </n-empty>
         
         <div v-else class="plans-grid">
            <div 
              v-for="plan in teacherStore.plans" 
              :key="plan.id"
              class="panel glass-card plan-card"
            >
               <div class="plan-header">
                  <div class="plan-icon">
                     <Presentation :size="28" />
                  </div>
                  <div class="plan-info">
                    <h3 class="plan-topic" :title="plan.topic">{{ plan.topic }}</h3>
                    <span class="plan-id">{{ plan.id }}</span>
                  </div>
               </div>
               
               <div class="plan-meta-grid">
                  <div class="meta-tag">
                     <GraduationCap :size="14" />
                     <span>{{ plan.gradeLevel || '未指定' }}</span>
                  </div>
                  <div class="meta-tag">
                     <Clock :size="14" />
                     <span>{{ plan.durationMins }} min</span>
                  </div>
               </div>

               <div class="plan-footer">
                  <div class="plan-date">{{ plan.updatedAt?.substring(0, 16) }}</div>
                  <div class="plan-actions">
                     <n-button circle size="small" type="primary" quaternary ghost @click="openEditor(plan.id || '')" class="animate-pop tool-btn">
                       <template #icon><Edit3 :size="16" /></template>
                     </n-button>
                     <n-button circle size="small" type="warning" quaternary ghost @click="sharePlan(plan.id || '')" class="animate-pop tool-btn">
                       <template #icon><Share2 :size="16" /></template>
                     </n-button>
                     <n-button circle size="small" type="info" quaternary ghost @click="exportCurrentPlan(plan.id || '')" class="animate-pop tool-btn">
                       <template #icon><Download :size="16" /></template>
                     </n-button>
                     <n-button circle size="small" type="error" quaternary ghost @click="confirmRemovePlan(plan.id || '')" class="animate-pop tool-btn dust-btn">
                       <template #icon><Trash2 :size="16" /></template>
                     </n-button>
                  </div>
               </div>
            </div>
         </div>
      </n-spin>
    </div>

    <!-- Editor Modal -->
    <n-modal
      v-model:show="showEditorModal"
      preset="card"
      :title="`高维干涉中：${editor.topic || editor.planId}`"
      class="editor-modal eth-modal"
      :mask-closable="false"
      size="huge"
      :style="{ width: '1200px', maxWidth: '95vw', height: '85vh', backgroundColor: 'rgba(255,255,255,0.95)', backdropFilter: 'blur(20px)' }"
      :on-close="closeEditor"
    >
       <div class="editor-layout">
          <div class="editor-pane">
             <n-input
                v-model:value="editor.contentMd"
                type="textarea"
                placeholder="在此编写 Markdown 拓扑结构..."
                class="full-height-input transparent-input"
             />
          </div>
          <div class="preview-pane">
             <div class="preview-header">
                <n-space align="center" :size="8">
                   <BookOpen :size="16" style="color: var(--color-primary)" />
                   <n-text strong>安全净化视界 (XSS-Safe)</n-text>
                </n-space>
             </div>
             <div class="preview-content">
                <MarkdownPreview :content="editor.contentMd" />
             </div>
          </div>
       </div>

       <template #action>
          <n-space justify="end" style="padding-top: 12px; border-top: 1px solid var(--color-border-glass)">
             <n-button @click="closeEditor" round class="animate-pop">终止干涉</n-button>
             <n-button type="primary" :loading="teacherStore.operationLoading" @click="savePlan" round class="hover-glow animate-pop">
                固化拓扑修订
             </n-button>
          </n-space>
       </template>
    </n-modal>

     <!-- Question Bank Modal -->
     <n-modal v-model:show="showQuestionBank" preset="card" title="教案题源筛选中枢" style="width: 900px; max-width: 90vw;">
      <div class="qb-layout">
        <div class="qb-main">
           <div class="qb-filters" style="margin-bottom: 16px;">
             <n-space>
               <n-select v-model:value="qbFilters.chapter" style="width: 140px" :options="[{label:'全部章节', value:''}, {label:'第一章', value:'第一章'}, {label:'第二章', value:'第二章'}, {label:'第三章', value:'第三章'}, {label:'第四章', value:'第四章'}]" placeholder="按章节筛选" />
               <n-select v-model:value="qbFilters.difficulty" style="width: 140px" :options="[{label:'全维度', value:''}, {label:'EASY', value:'EASY'}, {label:'MEDIUM', value:'MEDIUM'}, {label:'HARD', value:'HARD'}]" placeholder="按难度筛选" />
             </n-space>
           </div>
           <div class="qb-list">
             <div v-for="q in mockBank" :key="q.id" class="qb-item" v-show="(qbFilters.chapter === '' || q.chapter === qbFilters.chapter) && (qbFilters.difficulty === '' || q.difficulty === qbFilters.difficulty)">
                <div class="qb-item-body">
                  <span style="display:block; margin-bottom: 8px;">{{ q.content }}</span>
                  <n-space size="small">
                    <n-tag size="small" type="info" :bordered="false">{{ q.chapter }}</n-tag>
                    <n-tag size="small" :type="q.difficulty === 'HARD' ? 'error' : 'success'" :bordered="false">{{ q.difficulty }}</n-tag>
                  </n-space>
                </div>
                <div class="qb-item-actions">
                   <n-button v-if="!selectedQbIds.includes(q.id)" type="primary" size="small" secondary @click="addToCart(q.id)">加入轨标</n-button>
                   <n-button v-else type="default" size="small" disabled>已加入</n-button>
                </div>
             </div>
           </div>
        </div>
        <div class="qb-cart glass-card">
           <h4 style="margin-top:0; color: var(--color-primary);">已选轨标池 ({{ selectedQbIds.length }})</h4>
           <div v-if="selectedQbIds.length === 0" style="color:var(--color-text-muted); font-size: 0.9rem">暂无靶向试卷预组装数据</div>
           <div v-for="id in selectedQbIds" :key="id" class="qb-cart-item">
             <span class="text-truncate" style="flex:1" :title="mockBank.find(q=>q.id===id)?.content">{{ mockBank.find(q=>q.id===id)?.content }}</span>
             <n-button type="error" size="tiny" circle quaternary @click="removeFromCart(id)"><template #icon><Trash2 :size="14"/></template></n-button>
           </div>
        </div>
      </div>
      <template #action>
        <n-space justify="end">
          <n-button @click="showQuestionBank = false">取消</n-button>
          <n-button type="primary" @click="confirmQBCart">锁定并挂载题源</n-button>
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

.cart-alert {
  border-radius: var(--radius-md);
  border: 1px solid rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.05);
  box-shadow: 0 4px 20px rgba(16, 185, 129, 0.05);
}
.cart-alert :deep(.n-alert-body__title) {
  color: #059669;
}

.generator-panel {
  padding: 24px;
}

.generator-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.banner-icon-bg {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(92,101,246,0.1), rgba(92,101,246,0.2));
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.generate-btn {
  border-radius: 20px;
}

.share-alert {
  border-radius: var(--radius-md);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.empty-layout {
  padding: 80px 0;
}

/* Plans Grid */
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
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
  border: 1px solid var(--color-border-glass);
}

.plan-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-float);
  border-color: rgba(92, 101, 246, 0.3);
}

.plan-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.plan-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(92,101,246,0.08);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.plan-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  justify-content: center;
  min-height: 44px;
}

.plan-topic {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-text-main);
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
  background: rgba(0,0,0,0.02);
  padding: 10px;
  border-radius: 8px;
}

.meta-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  background: rgba(255,255,255,0.6);
  padding: 4px 10px;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
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

.tool-btn {
  background: transparent !important;
}

.tool-btn:hover {
  background: rgba(92, 101, 246, 0.1) !important;
}

.dust-btn:hover {
  background: rgba(239, 68, 68, 0.1) !important;
  color: var(--color-danger) !important;
}

/* Editor Styles */
.editor-layout {
  display: flex;
  height: calc(85vh - 160px);
  gap: 16px;
}

.editor-pane, .preview-pane {
  flex: 1;
  height: 100%;
  border-radius: 12px;
  border: 1px solid var(--color-border-glass);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 2px 10px rgba(0,0,0,0.02);
}

.preview-pane {
  background-color: rgba(255,255,255,0.6);
}

.full-height-input {
  flex: 1;
  height: 100%;
}

:deep(.full-height-input .n-input-wrapper) {
  height: 100%;
  padding: 0;
  background: transparent;
}

:deep(.full-height-input textarea) {
  height: 100% !important;
  font-family: var(--font-code);
  font-size: 14px;
  line-height: 1.6;
  padding: 20px;
  resize: none;
  background: rgba(255,255,255,0.4);
}

.preview-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border-glass);
  background: rgba(92,101,246,0.05);
}

.preview-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* Question Bank Modal */
.qb-layout {
  display: flex;
  gap: 20px;
  min-height: 400px;
  max-height: 60vh;
  overflow: hidden;
}
.qb-main {
  flex: 2;
  border-right: 1px dashed var(--color-border-glass);
  padding-right: 20px;
  display: flex;
  flex-direction: column;
}
.qb-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
}
.qb-cart {
  flex: 1;
  background: rgba(92,101,246,0.03);
  padding: 16px;
  border-radius: 8px;
  overflow-y: auto;
}
.qb-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--color-border-glass);
  margin-bottom: 12px;
  border-radius: 8px;
  background: rgba(255,255,255,0.4);
  transition: all 0.2s ease;
}
.qb-item:hover {
  border-color: rgba(92,101,246,0.4);
  background: rgba(255,255,255,0.8);
}
.qb-item-body {
  flex: 1;
  padding-right: 16px;
}
.qb-cart-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border-glass);
}
.text-truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
