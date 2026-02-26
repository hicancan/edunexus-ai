<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import RoleWorkspaceLayout from "../../components/common/RoleWorkspaceLayout.vue";
import { logout } from "../../services/auth.service";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const auth = useAuthStore();

const navItems = [
  { to: "/admin/users", label: "用户管理" },
  { to: "/admin/resources", label: "资源管理" },
  { to: "/admin/dashboard", label: "指标看板" },
  { to: "/admin/audits", label: "操作日志" }
];

const subtitle = computed(
  () => `${auth.user?.username || "管理员"}，统一执行用户治理、资源管理与平台审计。`
);

async function handleLogout(): Promise<void> {
  try {
    await logout();
  } finally {
    auth.clear();
    await router.replace("/login");
  }
}
</script>

<template>
  <RoleWorkspaceLayout
    title="平台管理中心"
    :subtitle="subtitle"
    :nav-items="navItems"
    @logout="handleLogout"
  >
    <router-view />
  </RoleWorkspaceLayout>
</template>
