<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const section = computed(() => route.meta.section || "users");

const users = ref([]);
const metrics = ref({});
const resources = ref([]);
const audits = ref([]);
const createForm = ref({ username: "", password: "12345678", role: "STUDENT" });
const downloadResult = ref(null);
const loading = ref(false);
const error = ref("");

async function loadAll() {
  loading.value = true;
  error.value = "";
  try {
    users.value = (await api.get("/admin/users")).data.data.list || [];
    metrics.value = (await api.get("/admin/dashboard/metrics")).data.data || {};
    resources.value = (await api.get("/admin/resources", { params: { resourceType: "LESSON_PLAN" } })).data.data.list || [];
    audits.value = (await api.get("/admin/audits")).data.data.list || [];
  } catch (e) {
    error.value = e?.response?.data?.message || "加载失败";
  } finally {
    loading.value = false;
  }
}

async function createUser() {
  await api.post("/admin/users", createForm.value);
  createForm.value.username = "";
  await loadAll();
}

async function toggleStatus(u) {
  const next = u.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  await api.patch(`/admin/users/${u.id}`, { status: next });
  await loadAll();
}

async function download(resourceId) {
  const res = await api.get(`/admin/resources/${resourceId}/download`, { responseType: "blob" });
  const blob = new Blob([res.data], { type: res.headers["content-type"] || "application/octet-stream" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  const filenameHeader = res.headers["content-disposition"] || "";
  const match = filenameHeader.match(/filename="?([^";]+)"?/);
  a.href = url;
  a.download = match?.[1] || `resource-${resourceId}`;
  a.click();
  URL.revokeObjectURL(url);
  downloadResult.value = { filename: a.download, size: blob.size };
}

function logout() {
  auth.clear();
  router.push("/login");
}

onMounted(loadAll);
</script>

<template>
  <div class="container">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h2>管理端</h2>
      <button @click="logout">退出</button>
    </div>

    <div class="card" style="display:flex;gap:10px;flex-wrap:wrap">
      <router-link to="/admin/users">用户管理</router-link>
      <router-link to="/admin/resources">资源管理</router-link>
      <router-link to="/admin/dashboard">指标看板</router-link>
      <router-link to="/admin/audits">操作日志</router-link>
    </div>

    <p v-if="error" style="color:#e03131">{{ error }}</p>

    <div v-if="section === 'users'" class="card">
      <h3>用户管理</h3>
      <p v-if="loading">加载中...</p>
      <div class="row">
        <div><input v-model="createForm.username" placeholder="新用户名" /></div>
        <div>
          <select v-model="createForm.role">
            <option value="STUDENT">STUDENT</option>
            <option value="TEACHER">TEACHER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
      </div>
      <button @click="createUser">创建用户</button>
      <p v-if="!users.length">暂无用户。</p>
      <ul>
        <li v-for="u in users" :key="u.id">
          {{ u.username }} - {{ u.role }} - {{ u.status }}
          <button style="margin-left:6px;background:#2f9e44" @click="toggleStatus(u)">切换状态</button>
        </li>
      </ul>
    </div>

    <div v-if="section === 'resources'" class="card">
      <h3>资源管理</h3>
      <p v-if="!resources.length">暂无资源。</p>
      <ul>
        <li v-for="r in resources" :key="r.id">
          {{ r.name }} - {{ r.type }}
          <button style="margin-left:6px" @click="download(r.id)">下载</button>
        </li>
      </ul>
      <pre v-if="downloadResult" style="white-space:pre-wrap">{{ downloadResult }}</pre>
    </div>

    <div v-if="section === 'dashboard'" class="card">
      <h3>平台指标</h3>
      <pre style="white-space:pre-wrap">{{ metrics }}</pre>
    </div>

    <div v-if="section === 'audits'" class="card">
      <h3>操作日志</h3>
      <p v-if="!audits.length">暂无日志。</p>
      <ul>
        <li v-for="a in audits" :key="a.id">{{ a.action }} - {{ a.resourceType }} - {{ a.createdAt }}</li>
      </ul>
    </div>
  </div>
</template>
