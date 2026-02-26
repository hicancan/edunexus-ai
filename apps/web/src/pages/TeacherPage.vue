<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const section = computed(() => route.meta.section || "knowledge");
const sectionTitle = {
  knowledge: "知识库管理",
  plans: "教案管理",
  analytics: "学情分析",
  suggestions: "教师建议"
};

const navItems = [
  { path: "/teacher/knowledge", label: "知识库管理" },
  { path: "/teacher/plans", label: "教案管理" },
  { path: "/teacher/analytics", label: "学情分析" },
  { path: "/teacher/suggestions", label: "教师建议" }
];

const docs = ref([]);
const plans = ref([]);
const analytics = ref(null);
const selectedFile = ref(null);
const loading = ref(false);
const error = ref("");

const planForm = ref({ topic: "牛顿定律", gradeLevel: "高一", durationMins: 45 });
const shareInfo = ref(null);
const exportFormat = ref("md");
const planEditorId = ref("");
const planEditorTopic = ref("");
const planEditorContent = ref("");
const analyticsStudentId = ref(localStorage.getItem("teacher_analytics_student_id") || "00000000-0000-0000-0000-000000000003");
const suggestion = ref({ studentId: analyticsStudentId.value, questionId: "", knowledgePoint: "牛顿第二定律", suggestion: "注意 F=ma 的单位换算" });
let docsTimer = null;

async function loadDocs() {
  try {
    const res = await api.get("/teacher/knowledge/documents");
    docs.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "加载文档失败";
  }
}

async function upload() {
  if (!selectedFile.value) {
    error.value = "请先选择文件";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const form = new FormData();
    form.append("file", selectedFile.value);
    await api.post("/teacher/knowledge/documents", form, { headers: { "Content-Type": "multipart/form-data" } });
    selectedFile.value = null;
    await loadDocs();
  } catch (e) {
    error.value = e?.response?.data?.message || "上传失败";
  } finally {
    loading.value = false;
  }
}

function onFileChange(event) {
  const file = event.target.files?.[0] || null;
  selectedFile.value = file;
}

async function removeDoc(id) {
  if (!window.confirm("确认删除该知识文档吗？")) return;
  try {
    await api.delete(`/teacher/knowledge/documents/${id}`);
    await loadDocs();
  } catch (e) {
    error.value = e?.response?.data?.message || "删除文档失败";
  }
}

async function genPlan() {
  loading.value = true;
  error.value = "";
  try {
    const created = await api.post("/teacher/plans/generate", planForm.value);
    planEditorId.value = created.data.data.id;
    planEditorTopic.value = created.data.data.topic || "";
    planEditorContent.value = created.data.data.content || "";
    const res = await api.get("/teacher/plans");
    plans.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "教案生成失败";
  } finally {
    loading.value = false;
  }
}

async function sharePlan(id) {
  try {
    const res = await api.post(`/teacher/plans/${id}/share`);
    shareInfo.value = res.data.data;
  } catch (e) {
    error.value = e?.response?.data?.message || "分享教案失败";
  }
}

async function exportPlan(id) {
  try {
    const res = await api.get(`/teacher/plans/${id}/export`, {
      params: { format: exportFormat.value },
      responseType: "blob"
    });
    const blob = new Blob([res.data], { type: res.headers["content-type"] || "application/octet-stream" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const filenameHeader = res.headers["content-disposition"] || "";
    const match = filenameHeader.match(/filename="?([^";]+)"?/);
    a.href = url;
    a.download = match?.[1] || `lesson-plan-${id}.${exportFormat.value}`;
    a.click();
    URL.revokeObjectURL(url);
    shareInfo.value = { exportedFile: a.download };
  } catch (e) {
    error.value = e?.response?.data?.message || "导出教案失败";
  }
}

function openPlanEditor(plan) {
  planEditorId.value = plan.id;
  planEditorTopic.value = plan.topic || "";
  planEditorContent.value = plan.content || "";
}

async function savePlan() {
  if (!planEditorId.value) return;
  loading.value = true;
  error.value = "";
  try {
    await api.put(`/teacher/plans/${planEditorId.value}`, { content: planEditorContent.value });
    shareInfo.value = { savedPlanId: planEditorId.value };
    const res = await api.get("/teacher/plans");
    plans.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "保存教案失败";
  } finally {
    loading.value = false;
  }
}

async function removePlan(id) {
  if (!window.confirm("确认删除该教案吗？")) return;
  loading.value = true;
  error.value = "";
  try {
    await api.delete(`/teacher/plans/${id}`);
    if (planEditorId.value === id) {
      planEditorId.value = "";
      planEditorTopic.value = "";
      planEditorContent.value = "";
    }
    const res = await api.get("/teacher/plans");
    plans.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "删除教案失败";
  } finally {
    loading.value = false;
  }
}

async function createSuggestion() {
  loading.value = true;
  error.value = "";
  try {
    await api.post("/teacher/suggestions", suggestion.value);
    await loadAnalytics();
  } catch (e) {
    error.value = e?.response?.data?.message || "提交建议失败";
  } finally {
    loading.value = false;
  }
}

async function loadAnalytics() {
  try {
    localStorage.setItem("teacher_analytics_student_id", analyticsStudentId.value);
    const res = await api.get(`/teacher/students/${analyticsStudentId.value}/analytics`);
    analytics.value = res.data.data;
  } catch (e) {
    error.value = e?.response?.data?.message || "加载学情失败";
  }
}

async function logout() {
  try {
    await api.post("/auth/logout");
  } catch (_) {
    // ignore and clear local session
  }
  auth.clear();
  router.push("/login");
}

function formatJson(value) {
  if (value == null) return "";
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

function docStatusClass(status) {
  if (status === "READY") return "status-ready";
  if (status === "FAILED") return "status-failed";
  return "status-pending";
}

onMounted(async () => {
  await loadDocs();
  try {
    const res = await api.get("/teacher/plans");
    plans.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "加载教案失败";
  }
  await loadAnalytics();
  docsTimer = setInterval(() => {
    if (section.value === "knowledge") {
      loadDocs();
    }
  }, 5000);
});

onUnmounted(() => {
  if (docsTimer) clearInterval(docsTimer);
});
</script>

<template>
  <div class="container teacher-shell">
    <header class="page-header">
      <div>
        <h1 class="page-title">教师工作台</h1>
        <p class="page-subtitle">{{ sectionTitle[section] }} · {{ auth.user?.username || "教师" }}，围绕课程资产、学情洞察与教学反馈持续迭代。</p>
      </div>
      <button class="btn-secondary" @click="logout">退出登录</button>
    </header>

    <section class="card fade-up">
      <div class="subnav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path">{{ item.label }}</router-link>
      </div>
    </section>

    <p v-if="error" class="status-error">{{ error }}</p>

    <section v-if="section === 'knowledge'" class="card fade-up">
      <div class="section-head">
        <h3>知识库文档上传</h3>
        <div class="row-right">
          <button class="btn-secondary" :disabled="loading" @click="loadDocs">刷新状态</button>
        </div>
      </div>

      <div class="upload-row">
        <input class="file-input" accept=".pdf,.doc,.docx" type="file" @change="onFileChange" />
        <button :disabled="loading" @click="upload">{{ loading ? "上传中..." : "上传并处理" }}</button>
      </div>

      <p v-if="!docs.length" class="muted">暂无文档。</p>
      <div class="list-table" v-else>
        <article v-for="d in docs" :key="d.id" class="list-row">
          <div>
            <p class="row-title">{{ d.filename }}</p>
            <p class="muted row-meta">文档 ID：{{ d.id }}</p>
          </div>
          <div class="row-right">
            <span class="badge" :class="docStatusClass(d.status)">{{ d.status }}</span>
            <button class="btn-danger btn-sm" @click="removeDoc(d.id)">删除</button>
          </div>
        </article>
      </div>
    </section>

    <section v-if="section === 'plans'" class="card fade-up">
      <div class="section-head">
        <h3>AI 教案生成</h3>
        <button :disabled="loading" @click="genPlan">{{ loading ? "生成中..." : "生成教案" }}</button>
      </div>

      <div class="row">
        <div>
          <label>主题</label>
          <input v-model="planForm.topic" placeholder="如：牛顿定律" />
        </div>
        <div>
          <label>年级</label>
          <input v-model="planForm.gradeLevel" placeholder="如：高一" />
        </div>
      </div>

      <div class="row">
        <div>
          <label>课时时长（分钟）</label>
          <input v-model.number="planForm.durationMins" type="number" placeholder="45" />
        </div>
        <div>
          <label>导出格式</label>
          <select v-model="exportFormat">
            <option value="md">Markdown</option>
            <option value="pdf">PDF</option>
          </select>
        </div>
      </div>

      <p v-if="!plans.length" class="muted">暂无教案。</p>
      <div class="list-table" v-else>
        <article v-for="p in plans" :key="p.id" class="list-row">
          <div>
            <p class="row-title">{{ p.topic }}</p>
            <p class="muted row-meta">{{ p.gradeLevel }} · 课时 {{ p.durationMins || "--" }} 分钟</p>
          </div>
          <div class="row-right">
            <button class="btn-ghost btn-sm" @click="openPlanEditor(p)">编辑</button>
            <button class="btn-secondary btn-sm" @click="exportPlan(p.id)">导出</button>
            <button class="btn-warning btn-sm" @click="sharePlan(p.id)">分享</button>
            <button class="btn-danger btn-sm" @click="removePlan(p.id)">删除</button>
          </div>
        </article>
      </div>

      <div class="plan-editor" v-if="planEditorId">
        <div class="section-head mini">
          <h4>编辑教案：{{ planEditorTopic || planEditorId }}</h4>
          <button :disabled="loading" @click="savePlan">{{ loading ? "保存中..." : "保存教案" }}</button>
        </div>
        <textarea v-model="planEditorContent" rows="12" placeholder="请输入教案 Markdown 内容"></textarea>
      </div>
    </section>

    <section v-if="section === 'analytics'" class="card fade-up">
      <div class="section-head">
        <h3>学情分析</h3>
        <button @click="loadAnalytics">查询</button>
      </div>
      <div class="row">
        <div>
          <label>学生 ID</label>
          <input v-model="analyticsStudentId" />
        </div>
      </div>
      <pre class="code-block">{{ formatJson(analytics) }}</pre>
    </section>

    <section v-if="section === 'suggestions'" class="card fade-up">
      <div class="section-head">
        <h3>教师建议</h3>
        <button @click="createSuggestion">提交建议</button>
      </div>

      <div class="row">
        <div>
          <label>学生 ID</label>
          <input v-model="suggestion.studentId" />
        </div>
        <div>
          <label>知识点</label>
          <input v-model="suggestion.knowledgePoint" />
        </div>
      </div>

      <div>
        <label>建议内容</label>
        <textarea v-model="suggestion.suggestion" rows="4"></textarea>
      </div>
    </section>

    <section v-if="shareInfo" class="card fade-up">
      <h3>导出/分享结果</h3>
      <pre class="code-block">{{ formatJson(shareInfo) }}</pre>
    </section>
  </div>
</template>

<style scoped>
.teacher-shell {
  padding-bottom: 24px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
}

.section-head.mini {
  margin-bottom: 8px;
}

.upload-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
}

.file-input {
  padding: 8px;
  background: #fff;
}

.list-table {
  margin-top: 10px;
  display: grid;
  gap: 9px;
}

.list-row {
  border: 1px solid #d6e6f4;
  border-radius: 12px;
  padding: 11px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  background: #fff;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.row-title {
  margin: 0;
  font-weight: 600;
}

.row-meta {
  margin: 3px 0 0;
  font-size: 12px;
}

.badge {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid #d3e4f4;
  background: #f6fbff;
  color: #406283;
  font-size: 12px;
}

.status-ready {
  background: #ebfbee;
  color: #2b8a3e;
  border-color: #b2f2bb;
}

.status-failed {
  background: #fff1f6;
  color: #d6336c;
  border-color: #ffc9de;
}

.status-pending {
  background: #fff9db;
  color: #e67700;
  border-color: #ffec99;
}

.plan-editor {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #d0dfec;
}

@media (max-width: 900px) {
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .upload-row {
    grid-template-columns: 1fr;
  }

  .list-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
