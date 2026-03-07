package com.edunexus.api.service;

import com.edunexus.api.grpc.worker.v1.JobStatusReportRequest;
import com.edunexus.api.grpc.worker.v1.JobStatusReportResponse;
import com.edunexus.api.grpc.worker.v1.JobStatusServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobStatusServiceImpl extends JobStatusServiceGrpc.JobStatusServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(JobStatusServiceImpl.class);

    private final GovernanceService governanceService;
    private final KnowledgeService knowledgeService;

    public JobStatusServiceImpl(
            GovernanceService governanceService, KnowledgeService knowledgeService) {
        this.governanceService = governanceService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void reportStatus(
            JobStatusReportRequest request,
            StreamObserver<JobStatusReportResponse> responseObserver) {
        log.info(
                "Received job status report: jobId={}, status={}, chunks={}, traceId={}",
                request.getJobId(),
                request.getStatus(),
                request.getChunks(),
                request.getTraceId());
        boolean success = true;

        try {
            UUID jobId = UUID.fromString(request.getJobId());

            switch (request.getStatus()) {
                case JOB_STATUS_RUNNING -> governanceService.markJobRunning(jobId);
                case JOB_STATUS_SUCCEEDED -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("message", "Job completed successfully");
                    if (request.getChunks() > 0) {
                        result.put("chunks", request.getChunks());
                    }
                    governanceService.markJobSucceeded(jobId, result);
                    knowledgeService.cleanupSupersededReadyDocumentsForJob(
                            jobId, request.getTraceId());
                }
                case JOB_STATUS_FAILED ->
                        governanceService.markJobFailed(jobId, request.getErrorMessage());
                case JOB_STATUS_DEAD_LETTER ->
                        governanceService.markJobDeadLetter(jobId, request.getErrorMessage());
                default -> log.warn("Unhandled job status reported: {}", request.getStatus());
            }

        } catch (IllegalArgumentException e) {
            log.warn("Invalid jobId UUID format: {}", request.getJobId());
            success = false;
        } catch (Exception e) {
            log.error("Failed to update job status", e);
            success = false;
        }

        JobStatusReportResponse response =
                JobStatusReportResponse.newBuilder().setSuccess(success).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
