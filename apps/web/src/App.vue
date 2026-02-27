<template>
  <NConfigProvider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <AppErrorBoundary>
          <router-view />
        </AppErrorBoundary>
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<script setup lang="ts">
import { ptBR, datePtBR, NConfigProvider, NMessageProvider, NDialogProvider, zhCN, dateZhCN, type GlobalThemeOverrides } from "naive-ui";
import AppErrorBoundary from "./components/common/AppErrorBoundary.vue";

// Top-tier 2026 Ethereal Glass Theme Overrides
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "hsl(237, 90%, 66%)",
    primaryColorHover: "hsl(237, 90%, 72%)",
    primaryColorPressed: "hsl(237, 90%, 56%)",
    primaryColorSuppl: "hsl(237, 90%, 66%)",
    infoColor: "hsl(180, 80%, 45%)",
    infoColorHover: "hsl(180, 80%, 55%)",
    infoColorPressed: "hsl(180, 80%, 35%)",
    successColor: "#10b981",
    warningColor: "#f59e0b",
    errorColor: "#ef4444",
    borderRadius: "12px",
    fontFamily: "'Outfit', 'Inter', system-ui, -apple-system, sans-serif",
    boxShadow1: "0 8px 32px 0 rgba(31, 38, 135, 0.07)",
    boxShadow2: "0 20px 40px -20px rgba(0,0,0,0.15)",
    boxShadow3: "0 30px 60px -30px rgba(0,0,0,0.2)"
  },
  Card: {
    borderRadius: "16px",
    borderColor: "rgba(255, 255, 255, 0.4)",
    color: "rgba(255, 255, 255, 0.65)",
    titleFontSizeLarge: "1.25rem",
    boxShadow: "0 8px 32px 0 rgba(31, 38, 135, 0.05)"
  },
  Button: {
    borderRadius: "12px",
    fontWeight: "600",
    heightMedium: "40px"
  },
  Input: {
    borderRadius: "12px",
    color: "rgba(255, 255, 255, 0.7)",
    colorFocus: "rgba(255, 255, 255, 0.95)",
    boxShadowFocus: "0 0 0 3px rgba(92, 101, 246, 0.2)"
  },
  Modal: {
    borderRadius: "24px",
    color: "rgba(255, 255, 255, 0.85)",
    boxShadow: "0 20px 40px -20px rgba(0,0,0,0.2)"
  },
  Dialog: {
    borderRadius: "20px",
    color: "rgba(255, 255, 255, 0.85)"
  },
  Menu: {
    borderRadius: "12px",
    itemColorActive: "rgba(92, 101, 246, 0.1)",
    itemTextColorActive: "hsl(237, 90%, 66%)",
    itemIconColorActive: "hsl(237, 90%, 66%)"
  }
};
</script>

<style>
/* Global overrides to support Native UI blur effects */
.n-card {
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.n-card:hover {
  box-shadow: 0 12px 40px -12px rgba(31, 38, 135, 0.15) !important;
}
.n-button {
  transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275), box-shadow 0.3s ease, background-color 0.3s ease;
}
.n-button:active {
  transform: scale(0.96);
}
/* Ensure Modals and Popovers carry the glass effect */
.n-modal, .n-dialog {
  backdrop-filter: blur(24px) !important;
  background-color: rgba(255, 255, 255, 0.85) !important;
  border: 1px solid rgba(255, 255, 255, 0.5);
}
</style>
