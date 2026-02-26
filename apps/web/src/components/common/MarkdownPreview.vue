<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  content: string;
}>();

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderSafeMarkdown(input: string): string {
  const escaped = escapeHtml(input);
  const withHeadings = escaped
    .replace(/^###\s+(.+)$/gm, "<h3>$1</h3>")
    .replace(/^##\s+(.+)$/gm, "<h2>$1</h2>")
    .replace(/^#\s+(.+)$/gm, "<h1>$1</h1>");
  const withInline = withHeadings
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");

  return withInline
    .split(/\n{2,}/)
    .map((block) => {
      const trimmed = block.trim();
      if (trimmed.startsWith("<h1>") || trimmed.startsWith("<h2>") || trimmed.startsWith("<h3>")) {
        return trimmed;
      }
      return `<p>${trimmed.replace(/\n/g, "<br />")}</p>`;
    })
    .join("");
}

const renderedHtml = computed(() => renderSafeMarkdown(props.content || ""));
</script>

<template>
  <div class="markdown-body" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-body {
  border: 1px solid #d0dfec;
  border-radius: var(--radius-md);
  background: #f8fcff;
  padding: 14px;
  color: #274766;
  overflow-wrap: anywhere;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 0 0 8px;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: #ecf4fb;
  border-radius: 6px;
  padding: 2px 5px;
  font-family: var(--font-code);
  font-size: 0.85em;
}
</style>
