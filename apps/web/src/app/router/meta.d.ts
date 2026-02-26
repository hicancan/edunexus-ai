import "vue-router";

declare module "vue-router" {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: Array<"STUDENT" | "TEACHER" | "ADMIN">;
    section?: string;
    requirementId?: string;
    title?: string;
  }
}

export { };
