<script setup lang="ts">
interface NavItem {
  to: string;
  label: string;
}

defineProps<{
  title: string;
  subtitle: string;
  navItems: NavItem[];
  logoutText?: string;
}>();

const emit = defineEmits<{
  logout: [];
}>();

function onLogout(): void {
  emit("logout");
}
</script>

<template>
  <div class="app-container workspace-stack">
    <header class="workspace-header">
      <div>
        <h1 class="workspace-title">{{ title }}</h1>
        <p class="workspace-subtitle">{{ subtitle }}</p>
      </div>
      <button class="btn secondary" type="button" @click="onLogout">{{ logoutText || "退出登录" }}</button>
    </header>

    <nav class="panel" aria-label="工作区导航">
      <div class="nav-row">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          class="nav-link"
          :to="item.to"
        >
          {{ item.label }}
        </router-link>
      </div>
    </nav>

    <slot />
  </div>
</template>
