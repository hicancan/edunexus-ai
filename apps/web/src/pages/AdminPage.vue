<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  createUser as createAdminUser,
  downloadResource,
  getDashboardMetrics,
  listAudits,
  listResources,
  listUsers,
  patchUser
} from "../services/admin.service";
import { logout as logoutApi } from "../services/auth.service";
import { adminCreateUserSchema } from "../schemas/admin.schemas";
import { toErrorMessage } from "../services/error-message";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const section = computed(() => route.meta.section || "users");
const sectionTitle = {
  users: "用户管理",
  resources: "资源管理",
  dashboard: "指标看板",
  audits: "操作日志"
};

const navItems = [
  { path: "/admin/users", label: "用户管理" },
  { path: "/admin/resources", label: "资源管理" },
  { path: "/admin/dashboard", label: "指标看板" },
  { path: "/admin/audits", label: "操作日志" }
];

const users = ref([]);
const metrics = ref({});
const resources = ref([]);
const audits = ref([]);
const resourceType = ref(localStorage.getItem("admin_resource_type") || "LESSON_PLAN");
const createForm = ref({ username: "", password: "12345678", role: "STUDENT" });
const downloadResult = ref(null);
const loading = ref(false);
const error = ref("");
const metricEntries = computed(() => Object.entries(metrics.value || {}));

async function loadAll() {
  loading.value = true;
  error.value = "";
  try {
    users.value = (await listUsers()).content || [];
    metrics.value = await getDashboardMetrics();
    resources.value = (await listResources({ resourceType: resourceType.value })).content || [];
    audits.value = (await listAudits()).content || [];
  } catch (e) {
    error.value = toErrorMessage(e, "加载失败");
  } finally {
    loading.value = false;
  }
}

async function reloadResources() {
  localStorage.setItem("admin_resource_type", resourceType.value);
  try {
    resources.value = (await listResources({ resourceType: resourceType.value })).content || [];
  } catch (e) {
    error.value = toErrorMessage(e, "加载资源失败");
  }
}

async function createUser() {
  const parsed = adminCreateUserSchema.safeParse(createForm.value);
  if (!parsed.success) {
    error.value = parsed.error.issues[0]?.message || "创建用户参数不合法";
    return;
  }

  try {
    await createAdminUser(parsed.data);
    createForm.value.username = "";
    await loadAll();
  } catch (e) {
    error.value = toErrorMessage(e, "创建用户失败");
  }
}

async function toggleStatus(u) {
  const next = u.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await patchUser(u.id, { status: next });
    await loadAll();
  } catch (e) {
    error.value = toErrorMessage(e, "更新用户失败");
  }
}

async function download(resourceId) {
  try {
    const response = await downloadResource(resourceId);
    const blob = new Blob([response.data], { type: response.headers["content-type"] || "application/octet-stream" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const disposition = response.headers["content-disposition"] || "";
    const fileNameMatch = disposition.match(/filename="?([^";]+)"?/);
    a.href = url;
    a.download = fileNameMatch?.[1] || `resource-${resourceId}`;
    a.click();
    URL.revokeObjectURL(url);
    downloadResult.value = { filename: a.download, size: blob.size };
  } catch (e) {
    error.value = toErrorMessage(e, "下载资源失败");
  }
}

async function logout() {
  try {
    await logoutApi();
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

onMounted(loadAll);
</script>

<template>
  <div class="container admin-shell">
    <header class="page-header">
      <div>
        <h1 class="page-title">平台管理中心</h1>
        <p class="page-subtitle">{{ sectionTitle[section] }} · {{ auth.user?.username || "管理员" }}，统一治理用户、资源和审计流程。</p>
      </div>
      <button class="btn-secondary" @click="logout">退出登录</button>
    </header>

    <section class="card fade-up">
      <div class="subnav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path">{{ item.label }}</router-link>
      </div>
    </section>

    <p v-if="error" class="status-error">{{ error }}</p>

    <section v-if="section === 'users'" class="card fade-up">
      <div class="section-head">
        <h3>用户管理</h3>
        <button @click="createUser">创建用户</button>
      </div>

      <p v-if="loading" class="muted">数据加载中...</p>
      <div class="row">
        <div>
          <label>新用户名</label>
          <input v-model="createForm.username" placeholder="请输入用户名" />
        </div>
        <div>
          <label>角色</label>
          <select v-model="createForm.role">
            <option value="STUDENT">STUDENT</option>
            <option value="TEACHER">TEACHER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
      </div>

      <p v-if="!users.length" class="muted">暂无用户。</p>
      <div class="list-table" v-else>
        <article v-for="u in users" :key="u.id" class="list-row">
          <div>
            <p class="row-title">{{ u.username }}</p>
            <p class="muted row-meta">{{ u.role }} · 当前状态：{{ u.status }}</p>
          </div>
          <button class="btn-success btn-sm" @click="toggleStatus(u)">切换状态</button>
        </article>
      </div>
    </section>

    <section v-if="section === 'resources'" class="card fade-up">
      <div class="section-head">
        <h3>资源管理</h3>
      </div>

      <div class="row">
        <div>
          <label>资源类型</label>
          <select v-model="resourceType" @change="reloadResources">
            <option value="LESSON_PLAN">LESSON_PLAN</option>
            <option value="QUESTION">QUESTION</option>
            <option value="DOCUMENT">DOCUMENT</option>
          </select>
        </div>
      </div>

      <p v-if="!resources.length" class="muted">暂无资源。</p>
      <div class="list-table" v-else>
        <article v-for="r in resources" :key="r.resourceId" class="list-row">
          <div>
            <p class="row-title">{{ r.title }}</p>
            <p class="muted row-meta">{{ r.resourceType }} · 资源 ID：{{ r.resourceId }}</p>
          </div>
          <button class="btn-secondary btn-sm" @click="download(r.resourceId)">下载</button>
        </article>
      </div>

      <div class="result-card" v-if="downloadResult">
        <h4>最近一次下载</h4>
        <pre class="code-block">{{ formatJson(downloadResult) }}</pre>
      </div>
    </section>

    <section v-if="section === 'dashboard'" class="card fade-up">
      <h3>平台指标</h3>
      <p v-if="!metricEntries.length" class="muted">暂无指标数据。</p>
      <div class="metrics-grid" v-else>
        <article v-for="entry in metricEntries" :key="entry[0]" class="metric-card">
          <p class="metric-name">{{ entry[0] }}</p>
          <p class="metric-value">{{ entry[1] }}</p>
        </article>
      </div>
      <pre class="code-block">{{ formatJson(metrics) }}</pre>
    </section>

    <section v-if="section === 'audits'" class="card fade-up">
      <h3>操作日志</h3>
      <p v-if="!audits.length" class="muted">暂无日志。</p>
      <div class="list-table" v-else>
        <article v-for="a in audits" :key="a.id" class="list-row">
          <div>
            <p class="row-title">{{ a.action }}</p>
            <p class="muted row-meta">{{ a.resourceType }} · {{ a.createdAt }}</p>
          </div>
          <span class="badge">{{ a.actorId || "系统" }}</span>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-shell {
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

.result-card {
  margin-top: 12px;
  border: 1px solid #d8e7f5;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.result-card h4 {
  margin: 0 0 8px;
}

.metrics-grid {
  margin: 10px 0 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  border: 1px solid #d7e6f4;
  border-radius: 12px;
  padding: 12px;
  background: #f8fcff;
}

.metric-name {
  margin: 0;
  color: #50708f;
  font-size: 13px;
}

.metric-value {
  margin: 5px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #20476e;
}

@media (max-width: 900px) {
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .list-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
