<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import RoleWorkspaceLayout from "../../components/common/RoleWorkspaceLayout.vue";
import { logout } from "../../features/auth/api/auth.service";
import { useAuthStore } from "../../features/auth/model/auth";

const router = useRouter();
const auth = useAuthStore();

const navItems = [
  { to: "/teacher/knowledge", label: "知识库管理" },
  { to: "/teacher/plans", label: "教案管理" },
  { to: "/teacher/analytics", label: "学情分析" },
  { to: "/teacher/suggestions", label: "教师建议" }
];

const subtitle = computed(
  () => `${auth.user?.username || "教师"}，持续维护知识库、教案与建议，服务学生学习主链路。`
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
    title="教师工作台"
    :subtitle="subtitle"
    :nav-items="navItems"
    @logout="handleLogout"
  >
    <router-view />
  </RoleWorkspaceLayout>
</template>
