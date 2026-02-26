import { defineStore } from "pinia";

export interface AuthUser {
  id: string;
  username: string;
  role: "STUDENT" | "TEACHER" | "ADMIN";
}

interface AuthState {
  token: string;
  user: AuthUser | null;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: localStorage.getItem("token") || "",
    user: JSON.parse(localStorage.getItem("user") || "null")
  }),
  actions: {
    setAuth(token: string, user: AuthUser) {
      this.token = token;
      this.user = user;
      localStorage.setItem("token", token);
      localStorage.setItem("user", JSON.stringify(user));
    },
    clear() {
      this.token = "";
      this.user = null;
      localStorage.removeItem("token");
      localStorage.removeItem("user");
    }
  }
});
