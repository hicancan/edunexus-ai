<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import PaginationBar from "../../components/common/PaginationBar.vue";
import { adminCreateUserSchema } from "../../schemas/admin.schemas";
import type { Role, UserStatus } from "../../services/contracts";
import { useAdminStore } from "../../stores/admin";

const adminStore = useAdminStore();

const filters = reactive<{
  role: "" | Role;
  status: "" | UserStatus;
  page: number;
  size: number;
}>({
  role: "",
  status: "",
  page: 1,
  size: 20
});

const createForm = reactive({
  username: "",
  password: "",
  role: "STUDENT" as Role,
  email: "",
  phone: ""
});

const rowEdits = reactive<Record<string, { role: Role; status: UserStatus }>>({});
const formError = ref("");
const success = ref("");

function syncRowEdits(): void {
  const activeKeys = new Set<string>();
  for (const user of adminStore.users) {
    if (!user.id) {
      continue;
    }
    activeKeys.add(user.id);
    rowEdits[user.id] = {
      role: (user.role || "STUDENT") as Role,
      status: (user.status || "ACTIVE") as UserStatus
    };
  }

  for (const key of Object.keys(rowEdits)) {
    if (!activeKeys.has(key)) {
      delete rowEdits[key];
    }
  }
}

async function loadUsers(): Promise<void> {
  await adminStore.loadUsers({
    role: filters.role || undefined,
    status: filters.status || undefined,
    page: filters.page,
    size: filters.size
  });
  syncRowEdits();
}

async function applyFilters(): Promise<void> {
  filters.page = 1;
  await loadUsers();
}

async function createUser(): Promise<void> {
  formError.value = "";
  success.value = "";

  const parsed = adminCreateUserSchema.safeParse(createForm);
  if (!parsed.success) {
    formError.value = parsed.error.issues[0]?.message || "创建用户参数不合法";
    return;
  }

  const result = await adminStore.createUser({
    username: parsed.data.username,
    password: parsed.data.password,
    role: parsed.data.role,
    email: parsed.data.email || undefined,
    phone: parsed.data.phone || undefined
  });

  if (!result) {
    return;
  }

  success.value = `用户 ${result.username} 创建成功`;
  createForm.username = "";
  createForm.password = "";
  createForm.email = "";
  createForm.phone = "";
  await loadUsers();
}

async function saveUser(userId: string): Promise<void> {
  const edit = rowEdits[userId];
  if (!edit) {
    return;
  }

  const updated = await adminStore.updateUser(userId, {
    role: edit.role,
    status: edit.status
  });

  if (updated) {
    success.value = `用户 ${updated.username} 更新成功`;
    await loadUsers();
  }
}

async function updatePage(page: number): Promise<void> {
  filters.page = page;
  await loadUsers();
}

async function updateSize(size: number): Promise<void> {
  filters.size = size;
  filters.page = 1;
  await loadUsers();
}

onMounted(loadUsers);

watch(
  () => adminStore.users,
  () => {
    syncRowEdits();
  },
  { immediate: true }
);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">用户管理</h2>
        <p class="panel-note">支持用户查询、新建，以及角色/状态更新。</p>
      </div>
      <button class="btn" type="button" :disabled="adminStore.operationLoading" @click="createUser">
        {{ adminStore.operationLoading ? "处理中..." : "创建用户" }}
      </button>
    </header>

    <div class="form-grid">
      <div class="field-block">
        <label for="users-filter-role">筛选角色</label>
        <select id="users-filter-role" v-model="filters.role">
          <option value="">全部</option>
          <option value="STUDENT">STUDENT</option>
          <option value="TEACHER">TEACHER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
      </div>
      <div class="field-block">
        <label for="users-filter-status">筛选状态</label>
        <select id="users-filter-status" v-model="filters.status">
          <option value="">全部</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="DISABLED">DISABLED</option>
        </select>
      </div>
      <div class="field-block">
        <label for="create-username">新用户名</label>
        <input id="create-username" v-model="createForm.username" placeholder="3-50 位字母数字下划线" />
      </div>
      <div class="field-block">
        <label for="create-password">密码</label>
        <input id="create-password" v-model="createForm.password" type="password" placeholder="8-64 位" />
      </div>
      <div class="field-block">
        <label for="create-role">创建角色</label>
        <select id="create-role" v-model="createForm.role">
          <option value="STUDENT">STUDENT</option>
          <option value="TEACHER">TEACHER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
      </div>
      <div class="field-block">
        <label for="create-email">邮箱（可选）</label>
        <input id="create-email" v-model="createForm.email" placeholder="name@school.edu.cn" />
      </div>
    </div>

    <div class="list-item-actions" style="margin-top: 12px;">
      <button class="btn secondary" type="button" :disabled="adminStore.usersLoading" @click="applyFilters">
        {{ adminStore.usersLoading ? "加载中..." : "按条件查询" }}
      </button>
    </div>

    <p v-if="formError" class="status-box error" role="alert">{{ formError }}</p>
    <p v-if="adminStore.usersError" class="status-box error" role="alert">{{ adminStore.usersError }}</p>
    <p v-if="adminStore.operationError" class="status-box error" role="alert">{{ adminStore.operationError }}</p>
    <p v-if="success" class="status-box success">{{ success }}</p>

    <div v-if="adminStore.usersLoading && !adminStore.usersLoaded" class="status-box info">正在加载用户...</div>
    <div v-else-if="adminStore.users.length === 0" class="status-box empty">暂无用户数据。</div>
    <div v-else class="list-stack">
      <article v-for="user in adminStore.users" :key="user.id" class="list-item">
        <div class="list-item-main">
          <p class="list-item-title">{{ user.username }}</p>
          <p class="list-item-meta">用户 ID：{{ user.id }} · 创建时间：{{ user.createdAt }} · 邮箱：{{ user.email || "--" }}</p>
        </div>
        <div v-if="user.id && rowEdits[user.id]" class="list-item-actions user-edit-actions">
          <label>
            角色
            <select v-model="rowEdits[user.id].role">
              <option value="STUDENT">STUDENT</option>
              <option value="TEACHER">TEACHER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </label>
          <label>
            状态
            <select v-model="rowEdits[user.id].status">
              <option value="ACTIVE">ACTIVE</option>
              <option value="DISABLED">DISABLED</option>
            </select>
          </label>
          <button class="btn success small" type="button" :disabled="adminStore.operationLoading" @click="saveUser(user.id)">保存</button>
        </div>
        <div v-else class="list-item-actions user-edit-actions">
          <span class="list-item-meta">编辑状态初始化中...</span>
        </div>
      </article>

      <PaginationBar
        :page="adminStore.usersPage"
        :size="adminStore.usersSize"
        :total-pages="adminStore.usersTotalPages"
        :total-elements="adminStore.usersTotalElements"
        :disabled="adminStore.usersLoading"
        @update:page="updatePage"
        @update:size="updateSize"
      />
    </div>
  </section>
</template>

<style scoped>
.user-edit-actions label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.user-edit-actions select {
  width: auto;
  min-height: 32px;
}

@media (max-width: 767px) {
  .user-edit-actions {
    width: 100%;
  }
}
</style>
