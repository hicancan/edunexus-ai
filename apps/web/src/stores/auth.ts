import { defineStore } from "pinia";
import { clearSessionStorage, storageKeys } from "../services/api-client";
import type { LoginData, RefreshData, UserVO } from "../services/contracts";

const legacyRefreshTokenKey = "refresh_token";

export type AuthUser = UserVO;

interface AuthState {
  token: string;
  refreshToken: string;
  user: AuthUser | null;
  profileLoadedAt: number;
}

function readUser(): AuthUser | null {
  const serialized = localStorage.getItem(storageKeys.user);
  if (!serialized) {
    return null;
  }

  try {
    return JSON.parse(serialized) as AuthUser;
  } catch {
    localStorage.removeItem(storageKeys.user);
    return null;
  }
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: localStorage.getItem(storageKeys.accessToken) || "",
    refreshToken: localStorage.getItem(storageKeys.refreshToken) || localStorage.getItem(legacyRefreshTokenKey) || "",
    user: readUser(),
    profileLoadedAt: 0
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    role: (state) => state.user?.role || null
  },
  actions: {
    setSession(payload: LoginData) {
      this.token = payload.accessToken;
      this.refreshToken = payload.refreshToken;
      this.user = payload.user;
      this.profileLoadedAt = Date.now();

      localStorage.setItem(storageKeys.accessToken, payload.accessToken);
      localStorage.setItem(storageKeys.refreshToken, payload.refreshToken);
      localStorage.removeItem(legacyRefreshTokenKey);
      localStorage.setItem(storageKeys.user, JSON.stringify(payload.user));
    },

    setTokens(payload: RefreshData) {
      this.token = payload.accessToken;
      this.refreshToken = payload.refreshToken;
      localStorage.setItem(storageKeys.accessToken, payload.accessToken);
      localStorage.setItem(storageKeys.refreshToken, payload.refreshToken);
      localStorage.removeItem(legacyRefreshTokenKey);
    },

    setUser(user: AuthUser) {
      this.user = user;
      this.profileLoadedAt = Date.now();
      localStorage.setItem(storageKeys.user, JSON.stringify(user));
    },

    clear() {
      this.token = "";
      this.refreshToken = "";
      this.user = null;
      this.profileLoadedAt = 0;
      clearSessionStorage();
    }
  }
});
