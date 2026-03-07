import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import {
  deleteKnowledgeDocument,
  listKnowledgeDocuments,
  uploadKnowledgeDocument
} from "../api/teacher.service";
import type { DocumentStatus, DocumentVO } from "../../../services/contracts";

interface DocumentState {
  documents: DocumentVO[];
  documentsLoading: boolean;
  documentsLoaded: boolean;
  documentsError: string;

  operationLoading: boolean;
  operationError: string;
}

export const useDocumentStore = defineStore("teacher-documents", {
  state: (): DocumentState => ({
    documents: [],
    documentsLoading: false,
    documentsLoaded: false,
    documentsError: "",

    operationLoading: false,
    operationError: ""
  }),
  actions: {
    async loadDocuments(status?: DocumentStatus): Promise<void> {
      this.documentsLoading = true;
      this.documentsError = "";
      try {
        this.documents = await listKnowledgeDocuments(status);
        this.documentsLoaded = true;
      } catch (error) {
        this.documentsError = toErrorMessage(error, "加载知识文档失败");
      } finally {
        this.documentsLoading = false;
      }
    },

    async uploadDocument(file: File, classId: string): Promise<void> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        await uploadKnowledgeDocument(file, classId);
      } catch (error) {
        this.operationError = toErrorMessage(error, "上传文档失败");
      } finally {
        this.operationLoading = false;
      }
    },

    async removeDocument(documentId: string): Promise<void> {
      this.operationLoading = true;
      this.operationError = "";
      try {
        await deleteKnowledgeDocument(documentId);
      } catch (error) {
        this.operationError = toErrorMessage(error, "删除文档失败");
      } finally {
        this.operationLoading = false;
      }
    }
  }
});
