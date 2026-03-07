const TEXT_KEYS = ["text", "label", "content", "value"] as const;
const STRUCTURED_TEXT_STRING =
  /^\{\s*['"]?(?:text|label|content|value)['"]?\s*:\s*['"](.*?)['"]\s*\}$/s;

export function formatQuestionOption(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    const matched = value.trim().match(STRUCTURED_TEXT_STRING);
    if (matched) {
      return matched[1]?.trim() || "";
    }
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatQuestionOption(item)).find((item) => item.length > 0) || "";
  }
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    for (const key of TEXT_KEYS) {
      if (key in record) {
        return formatQuestionOption(record[key]);
      }
    }
    const firstValue = Object.values(record).find((item) => item != null);
    return firstValue == null ? "" : formatQuestionOption(firstValue);
  }
  return String(value);
}
