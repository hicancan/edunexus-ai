<script setup lang="ts">
import { computed, h, ref, onMounted, onUnmounted } from "vue";
import { useRoute, RouterLink } from "vue-router";
import { NLayout, NLayoutHeader, NLayoutContent, NMenu, NButton, NSpace, NText } from "naive-ui";
import { LogOut, Sparkle } from "lucide-vue-next";

interface NavItem {
  to: string;
  label: string;
}

const props = defineProps<{
  title: string;
  subtitle: string;
  navItems: NavItem[];
  logoutText?: string;
}>();

const emit = defineEmits<{
  logout: [];
}>();

const route = useRoute();
const activeKey = computed(() => route.path);
const isScrolled = ref(false);

const menuOptions = computed(() => {
  return props.navItems.map((item) => ({
    label: () =>
      h(
        RouterLink,
        {
          to: item.to,
          class: "workspace-nav-link"
        },
        { default: () => item.label }
      ),
    key: item.to
  }));
});

function onLogout(): void {
  emit("logout");
}

function handleScroll(): void {
  isScrolled.value = window.scrollY > 10;
}

onMounted(() => {
  window.addEventListener("scroll", handleScroll, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
});
</script>

<template>
  <n-layout position="absolute" class="workspace-layout">
    <!-- Ethereal Background Glow Orbs -->
    <div class="ethereal-orb orb-1"></div>
    <div class="ethereal-orb orb-2"></div>
    <div class="ethereal-orb orb-3"></div>

    <n-layout-header :class="['workspace-header', { 'is-scrolled': isScrolled }]">
      <div class="header-container glass-pill-box">
        <div class="header-titles">
          <div class="title-with-icon">
             <Sparkle class="title-icon animate-pulse" :size="20" />
             <n-text tag="h1" class="main-title">{{ title }}</n-text>
          </div>
          <n-text depth="3" class="sub-title">{{ subtitle }}</n-text>
        </div>
        
        <div class="header-actions">
           <n-menu
             mode="horizontal"
             :value="activeKey"
             :options="menuOptions"
             class="workspace-nav-menu"
           />
           <div class="divider"></div>
           <n-button type="error" quaternary circle class="logout-btn animate-pop" @click="onLogout">
             <template #icon>
                <LogOut :size="18" />
             </template>
           </n-button>
        </div>
      </div>
    </n-layout-header>

    <n-layout-content class="workspace-content transparent-content" :native-scrollbar="false">
      <div class="main-viewport">
        <slot />
      </div>
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.workspace-layout {
  background-color: var(--color-bg-base);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

/* Background Orbs */
.ethereal-orb {
  position: fixed;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
  opacity: 0.6;
  pointer-events: none;
  animation: floatOrb 20s infinite alternate ease-in-out;
}

.orb-1 {
  width: 40vw;
  height: 40vw;
  background: radial-gradient(circle, rgba(92,101,246,0.15) 0%, rgba(92,101,246,0) 70%);
  top: -10vw;
  left: -10vw;
  animation-delay: 0s;
}

.orb-2 {
  width: 35vw;
  height: 35vw;
  background: radial-gradient(circle, rgba(16,185,129,0.12) 0%, rgba(16,185,129,0) 70%);
  bottom: -5vw;
  right: -5vw;
  animation-delay: -5s;
}

.orb-3 {
  width: 25vw;
  height: 25vw;
  background: radial-gradient(circle, rgba(239,68,68,0.08) 0%, rgba(239,68,68,0) 70%);
  top: 40%;
  left: 60%;
  animation-delay: -10s;
}

@keyframes floatOrb {
  0% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(3vw, -5vh) scale(1.1); }
  66% { transform: translate(-2vw, 4vh) scale(0.95); }
  100% { transform: translate(5vw, 2vh) scale(1.05); }
}

/* Header */
.workspace-header {
  position: sticky;
  top: 0;
  z-index: 100;
  padding: 16px 24px;
  background-color: transparent;
  transition: padding 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-header.is-scrolled {
  padding: 8px 24px;
}

.header-container {
  max-width: 1360px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.05), inset 0 1px 0 rgba(255,255,255,0.8);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.header-titles {
  display: flex;
  flex-direction: column;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  color: var(--color-primary);
}

.animate-pulse {
  animation: pulse 3s infinite ease-in-out;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; filter: drop-shadow(0 0 0px var(--color-primary)); }
  50% { transform: scale(1.05); opacity: 0.8; filter: drop-shadow(0 0 8px var(--color-primary)); }
  100% { transform: scale(1); opacity: 1; filter: drop-shadow(0 0 0px var(--color-primary)); }
}

.main-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-text-main);
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.sub-title {
  margin-top: 2px;
  font-size: 0.85rem;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* Navigation Menu Override */
.workspace-nav-menu {
  background-color: transparent;
}

:deep(.n-menu-item-content) {
  padding: 0 16px;
  border-radius: 12px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.n-menu-item-content:hover) {
  background-color: rgba(92, 101, 246, 0.08); /* Primary color low opacity */
}

:deep(.n-menu-item-content--selected) {
  background-color: rgba(92, 101, 246, 0.1) !important;
}

:deep(.n-menu-item-content--selected .workspace-nav-link) {
  color: var(--color-primary) !important;
  font-weight: 700;
}

.workspace-nav-link {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--color-text-main);
  transition: color 0.3s;
  text-decoration: none;
}

.divider {
  width: 1px;
  height: 24px;
  background: rgba(0,0,0,0.1);
  margin: 0 8px;
}

.logout-btn {
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.1) !important;
  transform: scale(1.1) rotate(5deg);
}

/* Main Content Area */
.transparent-content {
  background-color: transparent;
  z-index: 1;
  position: relative;
}

.main-viewport {
  padding: 16px 24px 40px;
  max-width: 1360px;
  margin: 0 auto;
}
</style>
