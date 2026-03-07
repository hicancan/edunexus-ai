import type { Router } from "vue-router";
import { getMe } from "../../features/auth/api/auth.service";
import { toErrorMessage } from "../../services/error-message";
import { useAuthStore } from "../../features/auth/model/auth";
import { roleHomePath } from "../router/routes";

const PROFILE_CACHE_MS = 2 * 60 * 1000;

async function ensureUserLoaded(): Promise<boolean> {
  const auth = useAuthStore();
  if (!auth.token) {
    return false;
  }

  const cacheFresh = auth.user && Date.now() - auth.profileLoadedAt < PROFILE_CACHE_MS;
  if (cacheFresh) {
    return true;
  }

  try {
    const me = await getMe();
    auth.setUser(me);
    if (me.status === "DISABLED") {
      auth.clear();
      return false;
    }
    return true;
  } catch (error) {
    console.warn("[auth-guard]", toErrorMessage(error, "登录状态已失效"));
    auth.clear();
    return false;
  }
}

export function registerAuthGuard(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore();
    const requiresAuth = Boolean(to.meta.requiresAuth);

    if (!requiresAuth) {
      if ((to.path === "/login" || to.path === "/register") && auth.token) {
        const loaded = await ensureUserLoaded();
        if (loaded && auth.user?.role) {
          return roleHomePath[auth.user.role];
        }
      }
      return true;
    }

    const authed = await ensureUserLoaded();
    if (!authed) {
      return { path: "/login", query: { redirect: to.fullPath } };
    }

    const allowedRoles = to.meta.roles || [];
    const currentRole = auth.user?.role;

    if (allowedRoles.length > 0 && (!currentRole || !allowedRoles.includes(currentRole))) {
      return { path: "/403", query: { from: to.fullPath } };
    }

    return true;
  });
}
