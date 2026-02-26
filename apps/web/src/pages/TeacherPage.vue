<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const section = computed(() => route.meta.section || "knowledge");

const docs = ref([]);
const plans = ref([]);
const analytics = ref(null);
const selectedFile = ref(null);
const loading = ref(false);
const error = ref("");

const planForm = ref({ topic: "牛顿定律", gradeLevel: "高一", durationMins: 45 });
const shareInfo = ref(null);
const suggestion = ref({ studentId: "00000000-0000-0000-0000-000000000003", questionId: "", knowledgePoint: "牛顿第二定律", suggestion: "注意 F=ma 的单位换算" });

async function loadDocs() {
  const res = await api.get("/teacher/knowledge/documents");
  docs.value = res.data.data.list || [];
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
  await api.delete(`/teacher/knowledge/documents/${id}`);
  await loadDocs();
}

async function genPlan() {
  loading.value = true;
  error.value = "";
  try {
    await api.post("/teacher/plans/generate", planForm.value);
    const res = await api.get("/teacher/plans");
    plans.value = res.data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "教案生成失败";
  } finally {
    loading.value = false;
  }
}

async function sharePlan(id) {
  const res = await api.post(`/teacher/plans/${id}/share`);
  shareInfo.value = res.data.data;
}

async function exportPlan(id) {
  const res = await api.get(`/teacher/plans/${id}/export`, {
    params: { format: "md" },
    responseType: "blob"
  });
  const blob = new Blob([res.data], { type: res.headers["content-type"] || "application/octet-stream" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  const filenameHeader = res.headers["content-disposition"] || "";
  const match = filenameHeader.match(/filename="?([^";]+)"?/);
  a.href = url;
  a.download = match?.[1] || `lesson-plan-${id}.md`;
  a.click();
  URL.revokeObjectURL(url);
  shareInfo.value = { exportedFile: a.download };
}

async function createSuggestion() {
  await api.post("/teacher/suggestions", suggestion.value);
  await loadAnalytics();
}

async function loadAnalytics() {
  const res = await api.get("/teacher/students/00000000-0000-0000-0000-000000000003/analytics");
  analytics.value = res.data.data;
}

function logout() {
  auth.clear();
  router.push("/login");
}

onMounted(async () => {
  await loadDocs();
  const res = await api.get("/teacher/plans");
  plans.value = res.data.data.list || [];
  await loadAnalytics();
});
</script>

<template>
  <div class="container">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h2>教师端</h2>
      <button @click="logout">退出</button>
    </div>

    <div class="card" style="display:flex;gap:10px;flex-wrap:wrap">
      <router-link to="/teacher/knowledge">知识库管理</router-link>
      <router-link to="/teacher/plans">教案管理</router-link>
      <router-link to="/teacher/analytics">学情分析</router-link>
      <router-link to="/teacher/suggestions">教师建议</router-link>
    </div>

    <p v-if="error" style="color:#e03131">{{ error }}</p>

    <div v-if="section === 'knowledge'" class="card">
      <h3>知识库上传</h3>
      <input accept=".pdf,.doc,.docx,.txt" type="file" @change="onFileChange" />
      <button :disabled="loading" @click="upload">{{ loading ? "上传中..." : "上传并处理" }}</button>
      <p v-if="!docs.length">暂无文档。</p>
      <ul>
        <li v-for="d in docs" :key="d.id">
          {{ d.filename }} - {{ d.status }}
          <button style="margin-left:6px;background:#e03131" @click="removeDoc(d.id)">删除</button>
        </li>
      </ul>
    </div>

    <div v-if="section === 'plans'" class="card">
      <h3>AI 教案生成</h3>
      <input v-model="planForm.topic" placeholder="主题" />
      <input v-model="planForm.gradeLevel" placeholder="年级" />
      <input v-model.number="planForm.durationMins" type="number" placeholder="时长" />
      <button :disabled="loading" @click="genPlan">{{ loading ? "生成中..." : "生成教案" }}</button>
      <p v-if="!plans.length">暂无教案。</p>
      <ul>
        <li v-for="p in plans" :key="p.id">
          {{ p.topic }}（{{ p.gradeLevel }}）
          <button style="margin-left:6px" @click="exportPlan(p.id)">导出</button>
          <button style="margin-left:6px;background:#f59f00" @click="sharePlan(p.id)">分享</button>
        </li>
      </ul>
    </div>

    <div v-if="section === 'analytics'" class="card">
      <h3>学情分析</h3>
      <pre style="white-space:pre-wrap">{{ analytics }}</pre>
    </div>

    <div v-if="section === 'suggestions'" class="card">
      <h3>教师建议</h3>
      <div class="row">
        <div><label>学生ID</label><input v-model="suggestion.studentId" /></div>
        <div><label>知识点</label><input v-model="suggestion.knowledgePoint" /></div>
      </div>
      <textarea v-model="suggestion.suggestion" rows="3"></textarea>
      <button @click="createSuggestion">提交建议</button>
    </div>

    <div v-if="shareInfo" class="card">
      <h3>导出/分享结果</h3>
      <pre style="white-space:pre-wrap">{{ shareInfo }}</pre>
    </div>
  </div>
</template>
