import { createRouter, createWebHistory } from "vue-router";
import { registerAuthGuard } from "../guards/auth.guard";
import { routes } from "./routes";

export function createAppRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes
  });

  registerAuthGuard(router);

  return router;
}
