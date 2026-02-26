import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { useAuthStore } from "./stores/auth";
import LoginPage from "./pages/LoginPage.vue";
import RegisterPage from "./pages/RegisterPage.vue";
import StudentPage from "./pages/StudentPage.vue";
import TeacherPage from "./pages/TeacherPage.vue";
import AdminPage from "./pages/AdminPage.vue";
import ForbiddenPage from "./pages/ForbiddenPage.vue";
import NotFoundPage from "./pages/NotFoundPage.vue";

const routes = [
  { path: "/", redirect: "/login" },
  { path: "/login", component: LoginPage },
  { path: "/register", component: RegisterPage },
  { path: "/403", component: ForbiddenPage },
  { path: "/404", component: NotFoundPage },

  { path: "/student", redirect: "/student/chat" },
  { path: "/student/chat", component: StudentPage, meta: { role: "STUDENT", section: "chat" } },
  { path: "/student/exercise", component: StudentPage, meta: { role: "STUDENT", section: "exercise" } },
  { path: "/student/exercise/records", component: StudentPage, meta: { role: "STUDENT", section: "records" } },
  { path: "/student/wrong-book", component: StudentPage, meta: { role: "STUDENT", section: "wrong-book" } },
  { path: "/student/ai-questions", component: StudentPage, meta: { role: "STUDENT", section: "ai-questions" } },
  { path: "/student/profile", component: StudentPage, meta: { role: "STUDENT", section: "profile" } },

  { path: "/teacher", redirect: "/teacher/knowledge" },
  { path: "/teacher/knowledge", component: TeacherPage, meta: { role: "TEACHER", section: "knowledge" } },
  { path: "/teacher/plans", component: TeacherPage, meta: { role: "TEACHER", section: "plans" } },
  { path: "/teacher/analytics", component: TeacherPage, meta: { role: "TEACHER", section: "analytics" } },
  { path: "/teacher/suggestions", component: TeacherPage, meta: { role: "TEACHER", section: "suggestions" } },

  { path: "/admin", redirect: "/admin/users" },
  { path: "/admin/users", component: AdminPage, meta: { role: "ADMIN", section: "users" } },
  { path: "/admin/resources", component: AdminPage, meta: { role: "ADMIN", section: "resources" } },
  { path: "/admin/dashboard", component: AdminPage, meta: { role: "ADMIN", section: "dashboard" } },
  { path: "/admin/audits", component: AdminPage, meta: { role: "ADMIN", section: "audits" } },

  { path: "/:pathMatch(.*)*", redirect: "/404" }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);

router.beforeEach((to) => {
  const auth = useAuthStore();
  const publicPaths = new Set(["/login", "/register", "/403", "/404"]);
  const roleHome = {
    STUDENT: "/student/chat",
    TEACHER: "/teacher/knowledge",
    ADMIN: "/admin/users"
  };

  if (publicPaths.has(to.path)) {
    if ((to.path === "/login" || to.path === "/register") && auth.token && auth.user?.role) {
      return roleHome[auth.user.role] || "/login";
    }
    return true;
  }

  if (!auth.token) return "/login";
  if (to.meta.role && auth.user?.role !== to.meta.role) return "/403";
  return true;
});

app.use(router);
app.mount("#app");
