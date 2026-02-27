import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useAuthStore } from "./auth";
import { storageKeys } from "../../../services/api-client";

describe("auth store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("persists auth to localStorage", () => {
    const store = useAuthStore();
    store.setSession({
      accessToken: "token-123",
      refreshToken: "refresh-456",
      user: { id: "u1", username: "student01", role: "STUDENT" }
    });

    expect(store.token).toBe("token-123");
    expect(store.refreshToken).toBe("refresh-456");
    expect(store.user?.username).toBe("student01");
    expect(localStorage.getItem(storageKeys.accessToken)).toBe("token-123");
    expect(localStorage.getItem(storageKeys.refreshToken)).toBe("refresh-456");
  });

  it("clears auth and localStorage", () => {
    const store = useAuthStore();
    store.setSession({
      accessToken: "token-123",
      refreshToken: "refresh-456",
      user: { id: "u1", username: "student01", role: "STUDENT" }
    });
    store.clear();

    expect(store.token).toBe("");
    expect(store.refreshToken).toBe("");
    expect(store.user).toBeNull();
    expect(localStorage.getItem(storageKeys.accessToken)).toBeNull();
    expect(localStorage.getItem(storageKeys.refreshToken)).toBeNull();
  });
});
