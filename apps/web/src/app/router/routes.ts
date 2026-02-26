import type { RouteRecordRaw } from "vue-router";
import AdminPage from "../../pages/AdminPage.vue";
import ForbiddenPage from "../../pages/ForbiddenPage.vue";
import LoginPage from "../../pages/LoginPage.vue";
import NotFoundPage from "../../pages/NotFoundPage.vue";
import RegisterPage from "../../pages/RegisterPage.vue";
import StudentPage from "../../pages/StudentPage.vue";
import TeacherPage from "../../pages/TeacherPage.vue";

export const roleHomePath: Record<"STUDENT" | "TEACHER" | "ADMIN", string> = {
  STUDENT: "/student/chat",
  TEACHER: "/teacher/knowledge",
  ADMIN: "/admin/users"
};

export const routes: RouteRecordRaw[] = [
  { path: "/", redirect: "/login" },
  { path: "/login", component: LoginPage, meta: { requiresAuth: false, requirementId: "R-AUTH-03" } },
  { path: "/register", component: RegisterPage, meta: { requiresAuth: false, requirementId: "R-AUTH-01" } },
  { path: "/403", component: ForbiddenPage, meta: { requiresAuth: false } },
  { path: "/404", component: NotFoundPage, meta: { requiresAuth: false } },

  { path: "/student", redirect: "/student/chat" },
  {
    path: "/student/chat",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "chat", requirementId: "R-CHAT-01" }
  },
  {
    path: "/student/exercise",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "exercise", requirementId: "R-EX-01" }
  },
  {
    path: "/student/exercise/records",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "records", requirementId: "R-EX-08" }
  },
  {
    path: "/student/wrong-book",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "wrong-book", requirementId: "R-EX-06" }
  },
  {
    path: "/student/ai-questions",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "ai-questions", requirementId: "R-AIQ-01" }
  },
  {
    path: "/student/profile",
    component: StudentPage,
    meta: { requiresAuth: true, roles: ["STUDENT"], section: "profile", requirementId: "R-AUTH-07" }
  },

  { path: "/teacher", redirect: "/teacher/knowledge" },
  {
    path: "/teacher/knowledge",
    component: TeacherPage,
    meta: { requiresAuth: true, roles: ["TEACHER"], section: "knowledge", requirementId: "R-TCH-01" }
  },
  {
    path: "/teacher/plans",
    component: TeacherPage,
    meta: { requiresAuth: true, roles: ["TEACHER"], section: "plans", requirementId: "R-TCH-05" }
  },
  {
    path: "/teacher/analytics",
    component: TeacherPage,
    meta: { requiresAuth: true, roles: ["TEACHER"], section: "analytics", requirementId: "R-TCH-07" }
  },
  {
    path: "/teacher/suggestions",
    component: TeacherPage,
    meta: { requiresAuth: true, roles: ["TEACHER"], section: "suggestions", requirementId: "R-TCH-08" }
  },

  { path: "/admin", redirect: "/admin/users" },
  {
    path: "/admin/users",
    component: AdminPage,
    meta: { requiresAuth: true, roles: ["ADMIN"], section: "users", requirementId: "R-ADM-01" }
  },
  {
    path: "/admin/resources",
    component: AdminPage,
    meta: { requiresAuth: true, roles: ["ADMIN"], section: "resources", requirementId: "R-ADM-03" }
  },
  {
    path: "/admin/dashboard",
    component: AdminPage,
    meta: { requiresAuth: true, roles: ["ADMIN"], section: "dashboard", requirementId: "R-ADM-04" }
  },
  {
    path: "/admin/audits",
    component: AdminPage,
    meta: { requiresAuth: true, roles: ["ADMIN"], section: "audits", requirementId: "R-ADM-02" }
  },

  { path: "/:pathMatch(.*)*", redirect: "/404" }
];
