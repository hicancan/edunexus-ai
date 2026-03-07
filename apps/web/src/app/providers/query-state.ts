import type { LocationQuery, Router } from "vue-router";

export function readQueryString(query: LocationQuery, key: string): string {
  const value = query[key];
  return typeof value === "string" ? value : "";
}

export function readQueryInt(query: LocationQuery, key: string, fallback: number): number {
  const value = query[key];
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return Math.floor(parsed);
}

export async function replaceQuery(
  router: Router,
  currentQuery: LocationQuery,
  patch: Record<string, string | undefined>
): Promise<void> {
  await router.replace({
    query: {
      ...currentQuery,
      ...patch
    }
  });
}
