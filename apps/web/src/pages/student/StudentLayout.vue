<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import RoleWorkspaceLayout from "../../components/common/RoleWorkspaceLayout.vue";
import { logout } from "../../services/auth.service";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const auth = useAuthStore();

const navItems = [
  { to: "/student/chat", label: "智能问答" },
  { to: "/student/exercise", label: "练习做题" },
  { to: "/student/exercise/records", label: "做题记录" },
  { to: "/student/wrong-book", label: "错题本" },
  { to: "/student/ai-questions", label: "AI 出题" },
  { to: "/student/profile", label: "个人信息" }
];

const subtitle = computed(
  () => `${auth.user?.username || "学生"}，保持学习节奏，系统会自动记录问答、练习、错题和 AI 出题数据。`
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
    title="学生学习空间"
    :subtitle="subtitle"
    :nav-items="navItems"
    @logout="handleLogout"
  >
    <router-view />
  </RoleWorkspaceLayout>
</template>
