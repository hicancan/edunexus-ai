package com.edunexus.api.service;

import com.edunexus.api.common.CryptoUtil;
import com.edunexus.api.domain.Classroom;
import com.edunexus.api.domain.Document;
import com.edunexus.api.repository.ClassroomRepository;
import com.edunexus.api.repository.DocumentRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final DocumentRepository documentRepo;
    private final ClassroomRepository classroomRepo;
    private final ObjectStorageService objectStorageService;
    private final AiClient aiClient;
    private final GovernanceService governance;
    private final TaskExecutor documentIngestExecutor;
    private final boolean startupCleanupEnabled;

    public KnowledgeService(
            DocumentRepository documentRepo,
            ClassroomRepository classroomRepo,
            ObjectStorageService objectStorageService,
            AiClient aiClient,
            GovernanceService governance,
            @Value("${app.document-dedupe-on-startup-enabled:true}") boolean startupCleanupEnabled,
            @Qualifier("documentIngestExecutor") TaskExecutor documentIngestExecutor) {
        this.documentRepo = documentRepo;
        this.classroomRepo = classroomRepo;
        this.objectStorageService = objectStorageService;
        this.aiClient = aiClient;
        this.governance = governance;
        this.startupCleanupEnabled = startupCleanupEnabled;
        this.documentIngestExecutor = documentIngestExecutor;
    }

    public record UploadResult(UUID documentId, Document document) {}

    public UploadResult uploadDocument(
            UUID teacherId,
            UUID classId,
            String filename,
            String fileType,
            long fileSize,
            byte[] fileBytes,
            String traceId,
            String idempotencyKey) {
        Classroom classroom = classroomRepo.ensureOwner(classId, teacherId);

        String storagePath = objectStorageService.upload(filename, fileType, fileBytes);
        UUID documentId =
                documentRepo.create(teacherId, classId, filename, fileType, fileSize, storagePath);

        UUID jobId =
                governance.createJobRun(
                        "DOCUMENT_INGEST",
                        documentId,
                        Map.of(
                                "documentId", documentId.toString(),
                                "teacherId", teacherId.toString(),
                                "classId", classId.toString(),
                                "className", classroom.name(),
                                "filename", filename,
                                "storagePath", storagePath));

        documentIngestExecutor.execute(
                () ->
                        processInBackground(
                                documentId,
                                teacherId,
                                classId,
                                filename,
                                fileType,
                                fileBytes,
                                traceId,
                                idempotencyKey,
                                jobId));

        return new UploadResult(documentId, documentRepo.findById(documentId));
    }

    public List<Document> listDocuments(UUID teacherId, String status) {
        return documentRepo.list(teacherId, status);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupDuplicateReadyDocumentsOnStartup() {
        if (!startupCleanupEnabled) {
            return;
        }
        documentIngestExecutor.execute(
                () -> {
                    String traceId = "startup-document-dedupe-" + UUID.randomUUID();
                    List<Document> duplicates = documentRepo.listSupersededReadyDocuments();
                    if (!duplicates.isEmpty()) {
                        log.info(
                                "cleanup_duplicate_documents_start count={} traceId={}",
                                duplicates.size(),
                                traceId);
                        retireSupersededDocuments(duplicates, traceId);
                    }

                    List<Document> deletedDocuments = documentRepo.listSoftDeletedDocuments();
                    if (!deletedDocuments.isEmpty()) {
                        log.info(
                                "cleanup_deleted_document_vectors_start count={} traceId={}",
                                deletedDocuments.size(),
                                traceId);
                        purgeDeletedDocumentArtifacts(deletedDocuments, traceId);
                    }
                });
    }

    public void deleteDocument(UUID documentId, UUID teacherId, String traceId) {
        Document doc = documentRepo.ensureOwner(documentId, teacherId);
        objectStorageService.delete(doc.storagePath());
        documentRepo.softDelete(documentId);

        String docIdStr = documentId.toString();
        documentIngestExecutor.execute(
                () -> {
                    try {
                        aiClient.deleteKb(
                                Map.of(
                                        "traceId", traceId,
                                        "documentId", docIdStr,
                                        "idempotencyKey", "kb-delete-" + docIdStr));
                    } catch (Exception ex) {
                        log.error(
                                "async_kb_delete_failed documentId={} traceId={}",
                                docIdStr,
                                traceId,
                                ex);
                    }
                });
    }

    public void cleanupSupersededReadyDocuments(UUID documentId, String traceId) {
        List<Document> duplicates = documentRepo.findReadyDuplicatesForDocument(documentId);
        if (duplicates.isEmpty()) {
            return;
        }
        retireSupersededDocuments(
                duplicates,
                traceId == null || traceId.isBlank() ? "document-dedupe-" + documentId : traceId);
    }

    public void cleanupSupersededReadyDocumentsForJob(UUID jobId, String traceId) {
        UUID documentId = documentRepo.findDocumentIdByJobId(jobId);
        if (documentId == null) {
            return;
        }
        cleanupSupersededReadyDocuments(documentId, traceId);
    }

    private void processInBackground(
            UUID documentId,
            UUID teacherId,
            UUID classId,
            String filename,
            String fileType,
            byte[] fileBytes,
            String traceId,
            String idempotencyKey,
            UUID jobId) {
        try {
            governance.markJobRunning(jobId);
            documentRepo.updateStatus(documentId, "PARSING", null);
            documentRepo.updateStatus(documentId, "EMBEDDING", null);

            Map<String, Object> ingestResult =
                    aiClient.ingestKb(
                            Map.of(
                                    "traceId",
                                    traceId,
                                    "jobId",
                                    jobId.toString(),
                                    "documentId",
                                    documentId.toString(),
                                    "teacherId",
                                    teacherId.toString(),
                                    "classId",
                                    classId.toString(),
                                    "filename",
                                    filename,
                                    "fileType",
                                    fileType,
                                    "fileContent",
                                    fileBytes,
                                    "idempotencyKey",
                                    idempotencyKey == null || idempotencyKey.isBlank()
                                            ? "kb-ingest-" + documentId
                                            : idempotencyKey));

            Boolean isBackground = (Boolean) ingestResult.getOrDefault("background", false);
            if (!isBackground) {
                documentRepo.updateStatus(documentId, "READY", null);
                governance.markJobSucceeded(
                        jobId,
                        Map.of(
                                "documentId", documentId.toString(),
                                "classId", classId.toString(),
                                "chunks", ingestResult.getOrDefault("chunks", 0)));
                cleanupSupersededReadyDocuments(documentId, traceId);
            }
        } catch (Exception ex) {
            documentRepo.updateStatus(documentId, "FAILED", ex.getMessage());
            governance.markJobDeadLetter(jobId, ex.getMessage());
        }
    }

    public List<com.edunexus.api.domain.Classroom> listClassrooms(UUID teacherId) {
        return classroomRepo.listByTeacher(teacherId);
    }

    public static String computeHash(byte[] fileBytes) {
        return CryptoUtil.sha256(fileBytes);
    }

    private void retireSupersededDocuments(List<Document> duplicates, String traceId) {
        for (Document duplicate : duplicates) {
            try {
                aiClient.deleteKb(
                        Map.of(
                                "traceId",
                                traceId,
                                "documentId",
                                duplicate.id().toString(),
                                "idempotencyKey",
                                "kb-delete-" + duplicate.id()));
            } catch (Exception ex) {
                log.error(
                        "cleanup_duplicate_document_kb_delete_failed documentId={} traceId={}",
                        duplicate.id(),
                        traceId,
                        ex);
                continue;
            }

            try {
                objectStorageService.delete(duplicate.storagePath());
            } catch (Exception ex) {
                log.warn(
                        "cleanup_duplicate_document_storage_delete_failed documentId={} storagePath={} traceId={}",
                        duplicate.id(),
                        duplicate.storagePath(),
                        traceId,
                        ex);
            }

            documentRepo.softDelete(duplicate.id());
            log.info(
                    "cleanup_duplicate_document_success documentId={} filename={} traceId={}",
                    duplicate.id(),
                    duplicate.filename(),
                    traceId);
        }
    }

    private void purgeDeletedDocumentArtifacts(List<Document> deletedDocuments, String traceId) {
        for (Document document : deletedDocuments) {
            try {
                aiClient.deleteKb(
                        Map.of(
                                "traceId",
                                traceId,
                                "documentId",
                                document.id().toString(),
                                "idempotencyKey",
                                "kb-delete-" + document.id()));
            } catch (Exception ex) {
                log.error(
                        "cleanup_deleted_document_kb_delete_failed documentId={} traceId={}",
                        document.id(),
                        traceId,
                        ex);
                continue;
            }

            try {
                objectStorageService.delete(document.storagePath());
            } catch (Exception ex) {
                log.warn(
                        "cleanup_deleted_document_storage_delete_failed documentId={} storagePath={} traceId={}",
                        document.id(),
                        document.storagePath(),
                        traceId,
                        ex);
            }

            log.info(
                    "cleanup_deleted_document_success documentId={} filename={} traceId={}",
                    document.id(),
                    document.filename(),
                    traceId);
        }
    }
}
