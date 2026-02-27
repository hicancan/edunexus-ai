import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import {
  createUser,
  downloadResource,
  getDashboardMetrics,
  listAudits,
  listResources,
  listUsers,
  patchUser
} from "../api/admin.service";
import type {
  AdminResourceVO,
  AdminUserCreateRequest,
  AdminUserPatchRequest,
  AuditLogVO,
  DashboardMetricsVO,
  PagedResult,
  ResourceType,
  Role,
  UserStatus,
  UserVO
} from "../../../services/contracts";

interface AdminState {
  users: UserVO[];
  usersPage: number;
  usersSize: number;
  usersTotalPages: number;
  usersTotalElements: number;
  usersLoading: boolean;
  usersLoaded: boolean;
  usersError: string;

  resources: AdminResourceVO[];
  resourcesPage: number;
  resourcesSize: number;
  resourcesTotalPages: number;
  resourcesTotalElements: number;
  resourcesLoading: boolean;
  resourcesLoaded: boolean;
  resourcesError: string;

  audits: AuditLogVO[];
  auditsPage: number;
  auditsSize: number;
  auditsTotalPages: number;
  auditsTotalElements: number;
  auditsLoading: boolean;
  auditsLoaded: boolean;
  auditsError: string;

  metrics: DashboardMetricsVO | null;
  metricsLoading: boolean;
  metricsError: string;

  operationLoading: boolean;
  operationError: string;
}

function assignMeta<T>(
  state: AdminState,
  key: "users" | "resources" | "audits",
  paged: PagedResult<T>
): void {
  if (key === "users") {
    state.usersPage = paged.page;
    state.usersSize = paged.size;
    state.usersTotalPages = paged.totalPages;
    state.usersTotalElements = paged.totalElements;
    return;
  }

  if (key === "resources") {
    state.resourcesPage = paged.page;
    state.resourcesSize = paged.size;
    state.resourcesTotalPages = paged.totalPages;
    state.resourcesTotalElements = paged.totalElements;
    return;
  }

  state.auditsPage = paged.page;
  state.auditsSize = paged.size;
  state.auditsTotalPages = paged.totalPages;
  state.auditsTotalElements = paged.totalElements;
}

export const useAdminStore = defineStore("admin", {
  state: (): AdminState => ({
    users: [],
    usersPage: 1,
    usersSize: 20,
    usersTotalPages: 1,
    usersTotalElements: 0,
    usersLoading: false,
    usersLoaded: false,
    usersError: "",

    resources: [],
    resourcesPage: 1,
    resourcesSize: 20,
    resourcesTotalPages: 1,
    resourcesTotalElements: 0,
    resourcesLoading: false,
    resourcesLoaded: false,
    resourcesError: "",

    audits: [],
    auditsPage: 1,
    auditsSize: 20,
    auditsTotalPages: 1,
    auditsTotalElements: 0,
    auditsLoading: false,
    auditsLoaded: false,
    auditsError: "",

    metrics: null,
    metricsLoading: false,
    metricsError: "",

    operationLoading: false,
    operationError: ""
  }),
  actions: {
    async loadUsers(params: { page?: number; size?: number; role?: Role; status?: UserStatus } = {}): Promise<void> {
      this.usersLoading = true;
      this.usersError = "";
      try {
        const paged = await listUsers(params);
        this.users = paged.content;
        assignMeta(this, "users", paged);
        this.usersLoaded = true;
      } catch (error) {
        this.usersError = toErrorMessage(error, "加载用户列表失败");
      } finally {
        this.usersLoading = false;
      }
    },

    async createUser(payload: AdminUserCreateRequest): Promise<UserVO | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        return await createUser(payload);
      } catch (error) {
        this.operationError = toErrorMessage(error, "创建用户失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async updateUser(userId: string, payload: AdminUserPatchRequest): Promise<UserVO | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        return await patchUser(userId, payload);
      } catch (error) {
        this.operationError = toErrorMessage(error, "更新用户失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async loadResources(params: {
      page?: number;
      size?: number;
      resourceType?: ResourceType;
    } = {}): Promise<void> {
      this.resourcesLoading = true;
      this.resourcesError = "";
      try {
        const paged = await listResources(params);
        this.resources = paged.content;
        assignMeta(this, "resources", paged);
        this.resourcesLoaded = true;
      } catch (error) {
        this.resourcesError = toErrorMessage(error, "加载资源列表失败");
      } finally {
        this.resourcesLoading = false;
      }
    },

    async downloadResource(resourceId: string): Promise<Blob | null> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        return await downloadResource(resourceId);
      } catch (error) {
        this.operationError = toErrorMessage(error, "下载资源失败");
        return null;
      } finally {
        this.operationLoading = false;
      }
    },

    async loadAudits(params: { page?: number; size?: number } = {}): Promise<void> {
      this.auditsLoading = true;
      this.auditsError = "";
      try {
        const paged = await listAudits(params);
        this.audits = paged.content;
        assignMeta(this, "audits", paged);
        this.auditsLoaded = true;
      } catch (error) {
        this.auditsError = toErrorMessage(error, "加载审计日志失败");
      } finally {
        this.auditsLoading = false;
      }
    },

    async loadMetrics(): Promise<void> {
      this.metricsLoading = true;
      this.metricsError = "";
      try {
        this.metrics = await getDashboardMetrics();
      } catch (error) {
        this.metricsError = toErrorMessage(error, "加载看板指标失败");
      } finally {
        this.metricsLoading = false;
      }
    }
  }
});
