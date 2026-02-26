import { createRouter, createWebHistory } from "vue-router";
import { registerRouterGuard } from "./guard";
import { routes } from "./routes";

export function createAppRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes
  });

  registerRouterGuard(router);

  return router;
}
