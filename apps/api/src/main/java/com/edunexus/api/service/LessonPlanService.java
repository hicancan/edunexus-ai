package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.DependencyException;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.domain.LessonPlan;
import com.edunexus.api.repository.LessonPlanRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LessonPlanService {

    private final LessonPlanRepository planRepo;
    private final AiClient aiClient;
    private final VoMapper voMapper;
    private final PdfExportService pdfExportService;

    public LessonPlanService(
            LessonPlanRepository planRepo,
            AiClient aiClient,
            VoMapper voMapper,
            PdfExportService pdfExportService) {
        this.planRepo = planRepo;
        this.aiClient = aiClient;
        this.voMapper = voMapper;
        this.pdfExportService = pdfExportService;
    }

    public LessonPlan generateAndSave(
            UUID teacherId,
            String topic,
            String gradeLevel,
            int durationMins,
            String traceId,
            String idempotencyKey) {
        Map<String, Object> aiResp =
                aiClient.generatePlan(
                        Map.of(
                                "traceId", traceId,
                                "topic", topic,
                                "gradeLevel", gradeLevel,
                                "durationMins", durationMins,
                                "teacherId", teacherId.toString(),
                                "idempotencyKey", idempotencyKey == null ? "" : idempotencyKey));

        String contentMd = ApiDataMapper.asString(aiResp.get("contentMd"));
        if (contentMd == null || contentMd.isBlank()) {
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 教案内容为空");
        }
        UUID planId = planRepo.create(teacherId, topic, gradeLevel, durationMins, contentMd);
        return planRepo.findById(planId);
    }

    public List<LessonPlan> list(UUID teacherId, int page, int size) {
        return planRepo.list(teacherId, size, (page - 1) * size);
    }

    public long count(UUID teacherId) {
        return planRepo.count(teacherId);
    }

    public LessonPlan update(UUID planId, UUID teacherId, String contentMd) {
        planRepo.ensureOwner(planId, teacherId);
        planRepo.update(planId, contentMd);
        return planRepo.findById(planId);
    }

    public void delete(UUID planId, UUID teacherId) {
        planRepo.ensureOwner(planId, teacherId);
        planRepo.softDelete(planId);
    }

    public byte[] export(UUID planId, UUID teacherId, String format) {
        planRepo.ensureOwner(planId, teacherId);
        LessonPlan plan = planRepo.findById(planId);
        if ("pdf".equals(format)) {
            return pdfExportService.renderPdf(plan.topic(), plan.contentMd());
        }
        return plan.contentMd().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getTopicForExport(UUID planId) {
        return planRepo.findById(planId).topic();
    }

    public record ShareResult(UUID planId, String shareToken) {}

    public ShareResult share(UUID planId, UUID teacherId) {
        planRepo.ensureOwner(planId, teacherId);
        String shareToken = planRepo.enableSharing(planId);
        return new ShareResult(planId, shareToken);
    }

    public LessonPlan getShared(String shareToken) {
        return planRepo.findByShareToken(shareToken);
    }

    public void ensureOwner(UUID planId, UUID teacherId) {
        planRepo.ensureOwner(planId, teacherId);
    }
}
