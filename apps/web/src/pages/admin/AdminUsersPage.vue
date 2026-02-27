<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from "vue";
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
  NDataTable,
  NTag,
  NModal,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import { Search, UserPlus, Settings, Save } from "lucide-vue-next";
import { adminCreateUserSchema } from "../../features/admin/model/admin.schemas";
import type { Role, UserStatus, UserVO } from "../../services/contracts";
import { useAdminStore } from "../../features/admin/model/admin";

const adminStore = useAdminStore();
const message = useMessage();
const showCreateModal = ref(false);

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

const roleOptions = [
  { label: "全部角色", value: "" },
  { label: "学生 STUDENT", value: "STUDENT" },
  { label: "教师 TEACHER", value: "TEACHER" },
  { label: "系统管理员 ADMIN", value: "ADMIN" }
];

const statusOptions = [
  { label: "全部状态", value: "" },
  { label: "正常 ACTIVE", value: "ACTIVE" },
  { label: "封禁 DISABLED", value: "DISABLED" }
];

const rowEdits = reactive<Record<string, { role: Role; status: UserStatus; saving: boolean }>>({});
const formError = ref("");
const success = ref("");

function syncRowEdits(): void {
  const activeKeys = new Set<string>();
  for (const user of adminStore.users) {
    if (!user.id) {
      continue;
    }
    activeKeys.add(user.id);
    if (!rowEdits[user.id]) {
      rowEdits[user.id] = {
        role: (user.role || "STUDENT") as Role,
        status: (user.status || "ACTIVE") as UserStatus,
        saving: false
      };
    }
  }

  for (const key of Object.keys(rowEdits)) {
    if (!activeKeys.has(key)) {
      delete rowEdits[key];
    }
  }
}

const pagination = reactive({
  page: filters.page,
  pageSize: filters.size,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page;
    filters.page = page;
    loadUsers();
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    filters.size = pageSize;
    filters.page = 1;
    loadUsers();
  },
  itemCount: 0
});

watch(
  () => adminStore.usersTotalElements,
  (total) => {
    pagination.itemCount = total;
  }
);

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
  pagination.page = 1;
  await loadUsers();
}

async function createUser(): Promise<void> {
  formError.value = "";
  success.value = "";

  const parsed = adminCreateUserSchema.safeParse(createForm);
  if (!parsed.success) {
    message.warning(parsed.error.issues[0]?.message || "参数校验未通过");
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

  message.success(`安全身份 ${result.username} 已签发入网`);
  createForm.username = "";
  createForm.password = "";
  createForm.email = "";
  createForm.phone = "";
  showCreateModal.value = false;
  await loadUsers();
}

async function saveUser(userId: string): Promise<void> {
  const edit = rowEdits[userId];
  if (!edit) {
    return;
  }

  edit.saving = true;
  try {
     const updated = await adminStore.updateUser(userId, {
       role: edit.role,
       status: edit.status
     });

     if (updated) {
       message.success(`租户网络策略 ${updated.username} 变更生效`);
       await loadUsers();
     }
  } finally {
     edit.saving = false;
  }
}

const columns: DataTableColumns<UserVO> = [
  {
    title: "访问账号",
    key: "username",
    minWidth: 150,
    render(row) {
      return h(NText, { strong: true }, { default: () => row.username });
    }
  },
  {
    title: "底层指针 (UUID)",
    key: "id",
    width: 250,
    render(row) {
      return h(NText, { depth: 3, code: true, style: "font-size: 12px" }, { default: () => row.id });
    }
  },
  {
    title: "权限级",
    key: "role",
    width: 140,
    render(row) {
       const edit = rowEdits[row.id || ""];
       if (!edit) return null;
       return h(
         NSelect,
         {
            value: edit.role,
            options: [
              { label: "学生", value: "STUDENT" },
              { label: "教师", value: "TEACHER" },
              { label: "管理员", value: "ADMIN" }
            ],
            size: "small",
            onUpdateValue: (v: any) => edit.role = v
         }
       );
    }
  },
  {
    title: "入网状态",
    key: "status",
    width: 120,
    render(row) {
       const edit = rowEdits[row.id || ""];
       if (!edit) return null;
       return h(
         NSelect,
         {
            value: edit.status,
            options: [
              { label: "正常", value: "ACTIVE" },
              { label: "封禁", value: "DISABLED" }
            ],
            size: "small",
            onUpdateValue: (v: any) => edit.status = v
         }
       );
    }
  },
  {
    title: "配置下发",
    key: "actions",
    align: "center",
    width: 100,
    render(row) {
       const edit = rowEdits[row.id || ""];
       if (!edit) return null;
       return h(
         NButton,
         {
            size: "small",
            type: "warning",
            quaternary: true,
            loading: edit.saving,
            onClick: () => saveUser(row.id || "")
         },
         { default: () => "生效", icon: () => h(Save, { size: 14 }) }
       );
    }
  },
  {
    title: "注册时间",
    key: "createdAt",
    width: 180,
    render(row) {
      return h(NText, { depth: 3 }, { default: () => row.createdAt });
    }
  }
];

onMounted(loadUsers);
</script>

<template>
  <div class="admin-users-page">
    <n-space vertical :size="16">
      <div class="page-header">
        <div>
          <n-text tag="h2" class="page-title">全域访问控制中心</n-text>
          <n-text depth="3">负责底层身份注册、策略封禁、越权隔离及权限级调配。</n-text>
        </div>
      </div>

      <n-card class="glass-card" :bordered="false" size="small">
        <n-space justify="space-between" align="center">
          <n-form inline :model="filters" label-placement="left" :show-feedback="false">
             <n-form-item label="身份沙箱约束">
               <n-select v-model:value="filters.role" :options="roleOptions" style="width: 140px" @update:value="applyFilters" />
             </n-form-item>
             <n-form-item label="网关状态拦截">
               <n-select v-model:value="filters.status" :options="statusOptions" style="width: 140px" @update:value="applyFilters" />
             </n-form-item>
             <n-form-item>
               <n-button @click="applyFilters" :loading="adminStore.usersLoading" class="animate-pop glass-pill">
                 <template #icon><Search :size="16" /></template>
                 执行扫描
               </n-button>
             </n-form-item>
          </n-form>
          
          <n-button type="primary" @click="showCreateModal = true" class="animate-pop">
             <template #icon><UserPlus :size="16" /></template>
             注入安全身份
          </n-button>
        </n-space>
      </n-card>

      <n-alert v-if="adminStore.usersError" type="error" :show-icon="true">{{ adminStore.usersError }}</n-alert>
      <n-alert v-if="adminStore.operationError" type="error" :show-icon="true">{{ adminStore.operationError }}</n-alert>

      <n-card :bordered="false" class="table-card glass-card" content-style="padding: 0;">
        <n-data-table
          remote
          :loading="adminStore.usersLoading"
          :columns="columns"
          :data="adminStore.users"
          :pagination="pagination"
          :bordered="false"
          :bottom-bordered="false"
        />
      </n-card>
    </n-space>

    <!-- Create User Modal -->
    <n-modal v-model:show="showCreateModal" preset="card" title="底层签发安全身份" :style="{ width: '500px' }">
      <n-form :model="createForm" label-placement="left" label-width="80" require-mark-placement="right-hanging">
         <n-form-item label="访问句柄" required>
            <n-input v-model:value="createForm.username" placeholder="3-50 位安全字符" />
         </n-form-item>
         <n-form-item label="密钥环" required>
            <n-input v-model:value="createForm.password" type="password" show-password-on="click" placeholder="强化密码 (8-64位)" />
         </n-form-item>
         <n-form-item label="最高权限级" required>
            <n-select v-model:value="createForm.role" :options="roleOptions.slice(1)" />
         </n-form-item>
         <n-form-item label="网关邮箱">
            <n-input v-model:value="createForm.email" placeholder="name@school.edu.cn (可选)" />
         </n-form-item>
         <n-form-item label="物理频段">
            <n-input v-model:value="createForm.phone" placeholder="手机校验位 (可选)" />
         </n-form-item>
      </n-form>
      <template #action>
     <n-space justify="end">
            <n-button @click="showCreateModal = false" class="animate-pop">中断挂起</n-button>
            <n-button type="primary" :loading="adminStore.operationLoading" class="animate-pop" @click="createUser">验证并下发链路</n-button>
         </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 1.6rem;
  font-weight: 800;
  background: linear-gradient(135deg, var(--color-primary) 0%, #60a5fa 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.table-card {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-glass);
}

:deep(.n-data-table) {
  background: transparent;
  --n-merged-th-color: rgba(255,255,255,0.4);
  --n-merged-td-color: rgba(255,255,255,0.1);
  --n-merged-td-color-hover: rgba(255,255,255,0.3);
  --n-merged-border-color: var(--color-border-glass);
}

:deep(.n-data-table-th) {
  font-weight: 700;
  backdrop-filter: blur(8px);
}
</style>
