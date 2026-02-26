import type { RouteRecordRaw } from "vue-router";
import type { Role } from "../../services/contracts";

const LoginPage = () => import("../../pages/auth/LoginPage.vue");
const RegisterPage = () => import("../../pages/auth/RegisterPage.vue");
const ForbiddenPage = () => import("../../pages/common/ForbiddenPage.vue");
const NotFoundPage = () => import("../../pages/common/NotFoundPage.vue");

const StudentLayout = () => import("../../pages/student/StudentLayout.vue");
const StudentChatPage = () => import("../../pages/student/StudentChatPage.vue");
const StudentExercisePage = () => import("../../pages/student/StudentExercisePage.vue");
const StudentRecordsPage = () => import("../../pages/student/StudentRecordsPage.vue");
const StudentWrongBookPage = () => import("../../pages/student/StudentWrongBookPage.vue");
const StudentAiQuestionsPage = () => import("../../pages/student/StudentAiQuestionsPage.vue");
const StudentProfilePage = () => import("../../pages/student/StudentProfilePage.vue");

const TeacherLayout = () => import("../../pages/teacher/TeacherLayout.vue");
const TeacherKnowledgePage = () => import("../../pages/teacher/TeacherKnowledgePage.vue");
const TeacherPlansPage = () => import("../../pages/teacher/TeacherPlansPage.vue");
const TeacherAnalyticsPage = () => import("../../pages/teacher/TeacherAnalyticsPage.vue");
const TeacherSuggestionsPage = () => import("../../pages/teacher/TeacherSuggestionsPage.vue");

const AdminLayout = () => import("../../pages/admin/AdminLayout.vue");
const AdminUsersPage = () => import("../../pages/admin/AdminUsersPage.vue");
const AdminResourcesPage = () => import("../../pages/admin/AdminResourcesPage.vue");
const AdminDashboardPage = () => import("../../pages/admin/AdminDashboardPage.vue");
const AdminAuditsPage = () => import("../../pages/admin/AdminAuditsPage.vue");

export const roleHomePath: Record<Role, string> = {
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

  {
    path: "/student",
    component: StudentLayout,
    meta: { requiresAuth: true, roles: ["STUDENT"] },
    children: [
      { path: "", redirect: "/student/chat" },
      {
        path: "chat",
        component: StudentChatPage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "chat", requirementId: "R-CHAT-01" }
      },
      {
        path: "exercise",
        component: StudentExercisePage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "exercise", requirementId: "R-EX-01" }
      },
      {
        path: "exercise/records",
        component: StudentRecordsPage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "records", requirementId: "R-EX-08" }
      },
      {
        path: "wrong-book",
        component: StudentWrongBookPage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "wrong-book", requirementId: "R-EX-06" }
      },
      {
        path: "ai-questions",
        component: StudentAiQuestionsPage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "ai-questions", requirementId: "R-AIQ-01" }
      },
      {
        path: "profile",
        component: StudentProfilePage,
        meta: { requiresAuth: true, roles: ["STUDENT"], section: "profile", requirementId: "R-AUTH-07" }
      }
    ]
  },

  {
    path: "/teacher",
    component: TeacherLayout,
    meta: { requiresAuth: true, roles: ["TEACHER"] },
    children: [
      { path: "", redirect: "/teacher/knowledge" },
      {
        path: "knowledge",
        component: TeacherKnowledgePage,
        meta: { requiresAuth: true, roles: ["TEACHER"], section: "knowledge", requirementId: "R-TCH-01" }
      },
      {
        path: "plans",
        component: TeacherPlansPage,
        meta: { requiresAuth: true, roles: ["TEACHER"], section: "plans", requirementId: "R-TCH-05" }
      },
      {
        path: "analytics",
        component: TeacherAnalyticsPage,
        meta: { requiresAuth: true, roles: ["TEACHER"], section: "analytics", requirementId: "R-TCH-07" }
      },
      {
        path: "suggestions",
        component: TeacherSuggestionsPage,
        meta: { requiresAuth: true, roles: ["TEACHER"], section: "suggestions", requirementId: "R-TCH-08" }
      }
    ]
  },

  {
    path: "/admin",
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ["ADMIN"] },
    children: [
      { path: "", redirect: "/admin/users" },
      {
        path: "users",
        component: AdminUsersPage,
        meta: { requiresAuth: true, roles: ["ADMIN"], section: "users", requirementId: "R-ADM-01" }
      },
      {
        path: "resources",
        component: AdminResourcesPage,
        meta: { requiresAuth: true, roles: ["ADMIN"], section: "resources", requirementId: "R-ADM-03" }
      },
      {
        path: "dashboard",
        component: AdminDashboardPage,
        meta: { requiresAuth: true, roles: ["ADMIN"], section: "dashboard", requirementId: "R-ADM-04" }
      },
      {
        path: "audits",
        component: AdminAuditsPage,
        meta: { requiresAuth: true, roles: ["ADMIN"], section: "audits", requirementId: "R-ADM-02" }
      }
    ]
  },

  { path: "/:pathMatch(.*)*", redirect: "/404" }
];
