import { createRouter, createWebHistory } from "vue-router";
import { registerAuthGuard } from "../guards/auth.guard";
import { routes } from "./routes";

export function createAppRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes
  });

  registerAuthGuard(router);

  router.afterEach((to) => {
    const pageTitle = to.meta.title ? `${to.meta.title} - EduNexus AI` : "EduNexus AI";
    document.title = pageTitle;
  });

  return router;
}
