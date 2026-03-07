<script setup lang="ts">
import { computed } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";

// GFM is enabled by default in marked v5+; set explicitly to ensure tables work
marked.use({ gfm: true, breaks: false });

const props = defineProps<{
  content: string;
  /** Remove border/background so it blends into a parent container (e.g. chat bubble) */
  plain?: boolean;
}>();

/**
 * Preprocessing step that fixes two common LLM markdown formatting issues:
 *
 * 1. Headings missing a space: "###Title" → "### Title"
 *
 * 2. Table rows concatenated on one line by LLMs that omit newlines:
 *    "| :--- | :--- | | data1 | data2 |"  →  two proper table lines
 *
 *    Algorithm: track expected column count from the header row.
 *    When a subsequent line has MORE cells than colCount, split it into
 *    rows of colCount each (skipping the empty "junction" cells that appear
 *    between concatenated rows as `| |`).
 */
function preprocessMarkdown(raw: string): string {
  // 1. Fix headings: "###Text" → "### Text"
  let text = raw.replace(/^(#{1,6})([^#\s])/gm, "$1 $2");

  // 2. Fix table formatting
  const lines = text.split("\n");
  const out: string[] = [];
  let colCount = 0; // 0 = no active table

  for (const line of lines) {
    const t = line.trim();

    if (!t.includes("|")) {
      colCount = 0;
      out.push(line);
      continue;
    }

    // Handle "prefixText| col1 | col2 |" — LLM puts label before the table header
    if (!t.startsWith("|") && t.endsWith("|")) {
      const idx = t.indexOf("|");
      const before = t.slice(0, idx).trim();
      const table = t.slice(idx).trim();
      if (table.startsWith("|") && table.endsWith("|")) {
        colCount = table.slice(1, -1).split("|").length;
        if (before) {
          out.push(before);
          out.push(""); // blank line so GFM doesn't merge paragraph with table
        }
        out.push(table);
        continue;
      }
    }

    if (!t.startsWith("|") || !t.endsWith("|")) {
      colCount = 0;
      out.push(line);
      continue;
    }

    const cells = t
      .slice(1, -1)
      .split("|")
      .map((c) => c.trim());

    if (colCount === 0) {
      // First line of a table — establish column count
      colCount = cells.length;
      out.push(t);
      continue;
    }

    if (cells.length > colCount) {
      // Potentially multiple rows concatenated — try to split
      const rows: string[] = [];
      let i = 0;
      while (i < cells.length) {
        // Skip empty "junction" cell that appears between concatenated rows
        if (cells[i] === "" && rows.length > 0) {
          i++;
          continue;
        }
        const chunk = cells.slice(i, i + colCount);
        if (chunk.length === colCount) {
          rows.push("| " + chunk.join(" | ") + " |");
          i += colCount;
        } else if (chunk.length > 0) {
          rows.push("| " + chunk.join(" | ") + " |");
          break;
        } else {
          break;
        }
      }
      if (rows.length > 1) {
        out.push(...rows);
        continue;
      }
    }

    out.push(t);
  }

  return out.join("\n");
}

const renderedHtml = computed(() => {
  if (!props.content) return "";

  const preprocessed = preprocessMarkdown(props.content);

  // Append trailing newlines so incomplete streaming blocks (tables, code fences)
  // are flushed and parsed correctly
  const rawHtml = marked.parse(preprocessed + "\n\n") as string;
  return DOMPurify.sanitize(rawHtml);
});
</script>

<template>
  <div class="markdown-body" :class="{ 'markdown-plain': plain }" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-body {
  border: 1px solid #d0dfec;
  border-radius: var(--radius-md);
  background: #f8fcff;
  padding: 14px;
  color: #274766;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

/* Used inside containers that already provide the visual frame (e.g. chat bubbles) */
.markdown-body.markdown-plain {
  border: none;
  background: transparent;
  padding: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 14px 0 8px;
  font-weight: 700;
  line-height: 1.4;
}

.markdown-body :deep(h1) {
  font-size: 1.5em;
}
.markdown-body :deep(h2) {
  font-size: 1.3em;
}
.markdown-body :deep(h3) {
  font-size: 1.1em;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: #ecf4fb;
  border-radius: 4px;
  padding: 2px 5px;
  font-family: var(--font-code);
  font-size: 0.85em;
}

.markdown-body :deep(pre) {
  background: #1e2638;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 0 0 12px;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #e2e8f0;
  font-size: 0.875em;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 10px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

/* Table styles — required for GFM tables */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0 0 14px;
  font-size: 0.9em;
}

.markdown-body :deep(thead) {
  background: rgba(92, 101, 246, 0.08);
}

.markdown-body :deep(th) {
  border: 1px solid #c9d8e8;
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  color: #1a3a5c;
}

.markdown-body :deep(td) {
  border: 1px solid #d8e6f0;
  padding: 7px 12px;
  vertical-align: top;
}

.markdown-body :deep(tr:nth-child(even)) {
  background: rgba(208, 223, 236, 0.25);
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #5c65f6;
  margin: 0 0 12px;
  padding: 8px 16px;
  background: rgba(92, 101, 246, 0.05);
  color: #4a5568;
  border-radius: 0 6px 6px 0;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #d0dfec;
  margin: 14px 0;
}

.markdown-body :deep(a) {
  color: #5c65f6;
  text-decoration: underline;
}

.markdown-body :deep(strong) {
  font-weight: 700;
}
</style>
