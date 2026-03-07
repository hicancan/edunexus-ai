import { defineStore } from "pinia";
import { clearSessionStorage } from "../../../services/api-client";
import type { LoginData, RefreshData, UserVO } from "../../../services/contracts";

export type AuthUser = UserVO;

interface AuthState {
  token: string;
  refreshToken: string;
  user: AuthUser | null;
  profileLoadedAt: number;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: "",
    refreshToken: "",
    user: null,
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
    },

    setTokens(payload: RefreshData) {
      this.token = payload.accessToken;
      this.refreshToken = payload.refreshToken;
    },

    setUser(user: AuthUser) {
      this.user = user;
      this.profileLoadedAt = Date.now();
    },

    clear() {
      this.token = "";
      this.refreshToken = "";
      this.user = null;
      this.profileLoadedAt = 0;
      clearSessionStorage();
    }
  },
  persist: {
    key: "auth",
    storage: localStorage
  }
});
