import { defineStore } from "pinia";
import { toErrorMessage } from "../services/error-message";
import {
  createChatSession,
  deleteChatSession,
  getChatSessionDetail,
  listChatSessions,
  sendChatMessage
} from "../services/student.service";
import type { ChatMessageVO, ChatSessionVO, PagedResult } from "../services/contracts";

interface ChatState {
  sessions: ChatSessionVO[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  sessionsLoaded: boolean;
  sessionsLoading: boolean;
  sessionsError: string;
  activeSessionId: string;
  messages: ChatMessageVO[];
  detailLoading: boolean;
  detailError: string;
  sending: boolean;
}

function toPagedMeta(meta: Pick<PagedResult<ChatSessionVO>, "page" | "size" | "totalPages" | "totalElements">) {
  return {
    page: meta.page,
    size: meta.size,
    totalPages: meta.totalPages,
    totalElements: meta.totalElements
  };
}

export const useChatStore = defineStore("chat", {
  state: (): ChatState => ({
    sessions: [],
    page: 1,
    size: 20,
    totalPages: 1,
    totalElements: 0,
    sessionsLoaded: false,
    sessionsLoading: false,
    sessionsError: "",
    activeSessionId: "",
    messages: [],
    detailLoading: false,
    detailError: "",
    sending: false
  }),
  actions: {
    async loadSessions(params?: { page?: number; size?: number }): Promise<void> {
      const nextPage = params?.page ?? this.page;
      const nextSize = params?.size ?? this.size;

      this.sessionsLoading = true;
      this.sessionsError = "";

      try {
        const paged = await listChatSessions({ page: nextPage, size: nextSize });
        this.sessions = paged.content;
        Object.assign(this, toPagedMeta(paged));
        this.sessionsLoaded = true;

        if (!this.activeSessionId && this.sessions.length > 0) {
          this.activeSessionId = this.sessions[0].id || "";
        }
      } catch (error) {
        this.sessionsError = toErrorMessage(error, "加载会话失败");
      } finally {
        this.sessionsLoading = false;
      }
    },

    async createSession(): Promise<string> {
      this.detailError = "";
      const session = await createChatSession();
      this.activeSessionId = session.id || "";
      this.messages = [];
      await this.loadSessions({ page: this.page, size: this.size });
      return this.activeSessionId;
    },

    async openSession(sessionId: string): Promise<void> {
      this.activeSessionId = sessionId;
      this.detailLoading = true;
      this.detailError = "";
      try {
        const detail = await getChatSessionDetail(sessionId);
        this.messages = detail.messages || [];
      } catch (error) {
        this.detailError = toErrorMessage(error, "读取会话详情失败");
      } finally {
        this.detailLoading = false;
      }
    },

    async removeSession(sessionId: string): Promise<void> {
      await deleteChatSession(sessionId);
      if (this.activeSessionId === sessionId) {
        this.activeSessionId = "";
        this.messages = [];
      }
      await this.loadSessions({ page: this.page, size: this.size });
      if (!this.activeSessionId && this.sessions.length > 0) {
        await this.openSession(this.sessions[0].id || "");
      }
    },

    async sendMessage(rawMessage: string): Promise<void> {
      const message = rawMessage.trim();
      if (!message || this.sending) {
        return;
      }

      if (!this.activeSessionId) {
        await this.createSession();
      }

      this.sending = true;
      this.detailError = "";

      const optimisticMessage: ChatMessageVO = {
        role: "USER",
        content: message,
        createdAt: new Date().toISOString()
      };
      this.messages = [...this.messages, optimisticMessage];

      try {
        const reply = await sendChatMessage(this.activeSessionId, message);
        if (reply.assistantMessage) {
          this.messages = [...this.messages, reply.assistantMessage];
        }
        await this.loadSessions({ page: this.page, size: this.size });
      } catch (error) {
        this.messages = this.messages.slice(0, Math.max(this.messages.length - 1, 0));
        this.detailError = toErrorMessage(error, "发送消息失败");
        throw error;
      } finally {
        this.sending = false;
      }
    }
  }
});
