package com.edunexus.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edunexus.api.domain.Document;
import com.edunexus.api.repository.ClassroomRepository;
import com.edunexus.api.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private DocumentRepository documentRepo;

    @Mock private ClassroomRepository classroomRepo;

    @Mock private ObjectStorageService objectStorageService;

    @Mock private AiClient aiClient;

    @Mock private GovernanceService governanceService;

    @Captor private ArgumentCaptor<Map<String, Object>> deletePayloadCaptor;

    private final TaskExecutor directExecutor = Runnable::run;

    @Test
    void cleanupSupersededReadyDocuments_shouldRemoveOlderReadyDuplicates() {
        UUID currentDocumentId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        Document duplicate = document(duplicateId, "README.md", "s3://bucket/old-readme.md");
        KnowledgeService service =
                new KnowledgeService(
                        documentRepo,
                        classroomRepo,
                        objectStorageService,
                        aiClient,
                        governanceService,
                        true,
                        directExecutor);

        when(documentRepo.findReadyDuplicatesForDocument(currentDocumentId))
                .thenReturn(List.of(duplicate));
        when(aiClient.deleteKb(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("status", "ok"));

        service.cleanupSupersededReadyDocuments(currentDocumentId, "trace-cleanup");

        verify(aiClient).deleteKb(deletePayloadCaptor.capture());
        assertEquals(duplicateId.toString(), deletePayloadCaptor.getValue().get("documentId"));
        assertEquals("trace-cleanup", deletePayloadCaptor.getValue().get("traceId"));
        assertEquals(
                "kb-delete-" + duplicateId, deletePayloadCaptor.getValue().get("idempotencyKey"));
        verify(objectStorageService).delete("s3://bucket/old-readme.md");
        verify(documentRepo).softDelete(duplicateId);
    }

    @Test
    void cleanupSupersededReadyDocuments_shouldKeepDuplicateVisibleWhenKbDeleteFails() {
        UUID currentDocumentId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        Document duplicate = document(duplicateId, "README.md", "s3://bucket/old-readme.md");
        KnowledgeService service =
                new KnowledgeService(
                        documentRepo,
                        classroomRepo,
                        objectStorageService,
                        aiClient,
                        governanceService,
                        true,
                        directExecutor);

        when(documentRepo.findReadyDuplicatesForDocument(currentDocumentId))
                .thenReturn(List.of(duplicate));
        when(aiClient.deleteKb(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("qdrant unavailable"));

        service.cleanupSupersededReadyDocuments(currentDocumentId, "trace-cleanup");

        verify(objectStorageService, never()).delete(anyString());
        verify(documentRepo, never()).softDelete(duplicateId);
    }

    @Test
    void cleanupDuplicateReadyDocumentsOnStartup_shouldSweepExistingDuplicates() {
        UUID duplicateId = UUID.randomUUID();
        Document duplicate = document(duplicateId, "README.md", "s3://bucket/old-readme.md");
        KnowledgeService service =
                new KnowledgeService(
                        documentRepo,
                        classroomRepo,
                        objectStorageService,
                        aiClient,
                        governanceService,
                        true,
                        directExecutor);

        when(documentRepo.listSupersededReadyDocuments()).thenReturn(List.of(duplicate));
        when(aiClient.deleteKb(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("status", "ok"));

        service.cleanupDuplicateReadyDocumentsOnStartup();

        verify(aiClient).deleteKb(deletePayloadCaptor.capture());
        assertEquals(duplicateId.toString(), deletePayloadCaptor.getValue().get("documentId"));
        assertTrue(
                String.valueOf(deletePayloadCaptor.getValue().get("traceId"))
                        .startsWith("startup-document-dedupe-"));
        verify(documentRepo).softDelete(duplicateId);
    }

    @Test
    void cleanupDuplicateReadyDocumentsOnStartup_shouldPurgeSoftDeletedArtifacts() {
        UUID deletedId = UUID.randomUUID();
        Document deleted = document(deletedId, "README.md", "s3://bucket/deleted-readme.md");
        KnowledgeService service =
                new KnowledgeService(
                        documentRepo,
                        classroomRepo,
                        objectStorageService,
                        aiClient,
                        governanceService,
                        true,
                        directExecutor);

        when(documentRepo.listSupersededReadyDocuments()).thenReturn(List.of());
        when(documentRepo.listSoftDeletedDocuments()).thenReturn(List.of(deleted));
        when(aiClient.deleteKb(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("status", "ok"));

        service.cleanupDuplicateReadyDocumentsOnStartup();

        verify(aiClient).deleteKb(deletePayloadCaptor.capture());
        assertEquals(deletedId.toString(), deletePayloadCaptor.getValue().get("documentId"));
        verify(objectStorageService).delete("s3://bucket/deleted-readme.md");
    }

    private Document document(UUID id, String filename, String storagePath) {
        return new Document(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "高二(1)班",
                filename,
                "text/markdown",
                1024,
                storagePath,
                "READY",
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
                Instant.parse("2026-03-06T00:00:00Z"));
    }
}
