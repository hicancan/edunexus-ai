<script setup lang="ts">
import { computed } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";

const props = defineProps<{
  content: string;
}>();

const renderedHtml = computed(() => {
  if (!props.content) return "";
  
  // Parse markdown to HTML
  const rawHtml = marked.parse(props.content, { async: false }) as string;
  
  // Sanitize the HTML output
  return DOMPurify.sanitize(rawHtml);
});
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

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 10px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(pre) {
  margin: 0 0 10px;
}
</style>
