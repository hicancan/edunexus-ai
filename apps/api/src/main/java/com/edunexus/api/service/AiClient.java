package com.edunexus.api.service;

import com.edunexus.api.common.DependencyException;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.grpc.ai.v1.AiQuestionGenerateRequest;
import com.edunexus.api.grpc.ai.v1.AiQuestionGenerateResponse;
import com.edunexus.api.grpc.ai.v1.AiQuestionServiceGrpc;
import com.edunexus.api.grpc.ai.v1.ChatRequest;
import com.edunexus.api.grpc.ai.v1.ChatResponse;
import com.edunexus.api.grpc.ai.v1.ChatStreamResponse;
import com.edunexus.api.grpc.ai.v1.Citation;
import com.edunexus.api.grpc.ai.v1.ExerciseAnalysisRequest;
import com.edunexus.api.grpc.ai.v1.ExerciseAnalysisResponse;
import com.edunexus.api.grpc.ai.v1.ExerciseAnalysisServiceGrpc;
import com.edunexus.api.grpc.ai.v1.GeneratedQuestion;
import com.edunexus.api.grpc.ai.v1.KbDeleteRequest;
import com.edunexus.api.grpc.ai.v1.KbDeleteResponse;
import com.edunexus.api.grpc.ai.v1.KbIngestRequest;
import com.edunexus.api.grpc.ai.v1.KbIngestResponse;
import com.edunexus.api.grpc.ai.v1.KnowledgeBaseServiceGrpc;
import com.edunexus.api.grpc.ai.v1.LessonPlanGenerateRequest;
import com.edunexus.api.grpc.ai.v1.LessonPlanGenerateResponse;
import com.edunexus.api.grpc.ai.v1.LessonPlanServiceGrpc;
import com.edunexus.api.grpc.ai.v1.RagChatServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiClient {
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final String serviceToken;
    private final long aiQuestionTimeoutSeconds;
    private final long lessonPlanTimeoutSeconds;
    private final long kbIngestTimeoutSeconds;
    private final long kbDeleteTimeoutSeconds;

    private final ManagedChannel grpcChannel;
    private final RagChatServiceGrpc.RagChatServiceBlockingStub chatStub;
    private final ExerciseAnalysisServiceGrpc.ExerciseAnalysisServiceBlockingStub analysisStub;
    private final AiQuestionServiceGrpc.AiQuestionServiceBlockingStub aiQuestionStub;
    private final LessonPlanServiceGrpc.LessonPlanServiceBlockingStub lessonPlanStub;
    private final KnowledgeBaseServiceGrpc.KnowledgeBaseServiceBlockingStub kbStub;

    public record ChatStreamChunk(String delta, List<Map<String, Object>> citations) {}

    public AiClient(
            @Value("${app.ai-service-grpc-host:127.0.0.1}") String grpcHost,
            @Value("${app.ai-service-grpc-port:50051}") int grpcPort,
            @Value("${app.ai-question-timeout-seconds:150}") long aiQuestionTimeoutSeconds,
            @Value("${app.lesson-plan-timeout-seconds:180}") long lessonPlanTimeoutSeconds,
            @Value("${app.kb-ingest-timeout-seconds:180}") long kbIngestTimeoutSeconds,
            @Value("${app.kb-delete-timeout-seconds:60}") long kbDeleteTimeoutSeconds,
            @Value("${app.ai-service-token}") String serviceToken) {
        this.serviceToken = serviceToken;
        this.aiQuestionTimeoutSeconds = aiQuestionTimeoutSeconds;
        this.lessonPlanTimeoutSeconds = lessonPlanTimeoutSeconds;
        this.kbIngestTimeoutSeconds = kbIngestTimeoutSeconds;
        this.kbDeleteTimeoutSeconds = kbDeleteTimeoutSeconds;

        this.grpcChannel =
                ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();

        this.chatStub = RagChatServiceGrpc.newBlockingStub(grpcChannel);
        this.analysisStub = ExerciseAnalysisServiceGrpc.newBlockingStub(grpcChannel);
        this.aiQuestionStub = AiQuestionServiceGrpc.newBlockingStub(grpcChannel);
        this.lessonPlanStub = LessonPlanServiceGrpc.newBlockingStub(grpcChannel);
        this.kbStub = KnowledgeBaseServiceGrpc.newBlockingStub(grpcChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (grpcChannel != null) {
            grpcChannel.shutdownNow();
        }
    }

    private <T extends io.grpc.stub.AbstractBlockingStub<T>> T authorize(
            T stub, String traceId, String idemKey) {
        Metadata metadata = new Metadata();
        metadata.put(
                Metadata.Key.of("X-Service-Token", Metadata.ASCII_STRING_MARSHALLER), serviceToken);
        if (traceId != null && !traceId.isBlank()) {
            metadata.put(Metadata.Key.of("X-Trace-Id", Metadata.ASCII_STRING_MARSHALLER), traceId);
        }
        if (idemKey != null && !idemKey.isBlank()) {
            metadata.put(
                    Metadata.Key.of("Idempotency-Key", Metadata.ASCII_STRING_MARSHALLER), idemKey);
        }
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        long startMs = System.currentTimeMillis();
        try {
            ChatRequest request = buildChatRequest(body, traceId);

            ChatResponse response =
                    authorize(chatStub, traceId, "")
                            .withDeadlineAfter(30, TimeUnit.SECONDS)
                            .chat(request);

            log.info(
                    "ai_call_grpc path=chat latency_ms={} trace_id={}",
                    (System.currentTimeMillis() - startMs),
                    traceId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("answer", response.getAnswer());
            result.put("provider", response.getProvider());
            result.put("model", response.getModel());
            result.put("latencyMs", response.getLatencyMs());

            List<Map<String, Object>> citations = new ArrayList<>();
            for (Citation citation : response.getCitationsList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("documentId", citation.getDocumentId());
                item.put("filename", citation.getFilename());
                item.put("chunkIndex", citation.getChunkIndex());
                item.put("content", citation.getContent());
                item.put("score", citation.getScore());
                citations.add(item);
            }
            result.put("citations", citations);

            Map<String, Object> tokenUsage = new LinkedHashMap<>();
            tokenUsage.put("prompt", response.getTokenUsage().getPrompt());
            tokenUsage.put("completion", response.getTokenUsage().getCompletion());
            result.put("tokenUsage", tokenUsage);

            return result;
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("chat", ex);
        }
    }

    public void chatStream(Map<String, Object> body, Consumer<ChatStreamChunk> onChunk) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        try {
            ChatRequest request =
                    buildChatRequest(body, traceId).toBuilder().setStream(true).build();
            var stream =
                    authorize(chatStub, traceId, "")
                            .withDeadlineAfter(70, TimeUnit.SECONDS)
                            .chatStream(request);

            while (stream.hasNext()) {
                ChatStreamResponse frame = stream.next();
                List<Map<String, Object>> citations = new ArrayList<>();
                for (Citation citation : frame.getCitationsList()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("documentId", citation.getDocumentId());
                    item.put("filename", citation.getFilename());
                    item.put("chunkIndex", citation.getChunkIndex());
                    item.put("content", citation.getContent());
                    item.put("score", citation.getScore());
                    citations.add(item);
                }
                onChunk.accept(new ChatStreamChunk(frame.getDelta(), citations));
            }
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("chatStream", ex);
        }
    }

    public Map<String, Object> analyzeWrong(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        try {
            ExerciseAnalysisRequest.Builder reqBuilder =
                    ExerciseAnalysisRequest.newBuilder()
                            .setTraceId(traceId)
                            .setIdempotencyKey(getString(body, "idempotencyKey", ""))
                            .setQuestion(getString(body, "question", ""))
                            .setUserAnswer(getString(body, "userAnswer", ""))
                            .setCorrectAnswer(getString(body, "correctAnswer", ""))
                            .setTeacherSuggestion(getString(body, "teacherSuggestion", ""));

            if (body.get("knowledgePoints") instanceof List points) {
                for (Object point : points) {
                    reqBuilder.addKnowledgePoints(String.valueOf(point));
                }
            }

            ExerciseAnalysisResponse response =
                    authorize(analysisStub, traceId, "")
                            .withDeadlineAfter(35, TimeUnit.SECONDS)
                            .analyze(reqBuilder.build());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("encourage", response.getEncourage());
            result.put("concept", response.getConcept());
            result.put("rootCause", response.getRootCause());
            result.put("nextPractice", response.getNextPractice());
            result.put("steps", new ArrayList<>(response.getStepsList()));
            return result;
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("analyze", ex);
        }
    }

    public Map<String, Object> generateQuestions(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        try {
            AiQuestionGenerateRequest.Builder reqBuilder =
                    AiQuestionGenerateRequest.newBuilder()
                            .setTraceId(traceId)
                            .setIdempotencyKey(getString(body, "idempotencyKey", ""))
                            .setStudentId(getString(body, "studentId", ""))
                            .setSubject(getString(body, "subject", ""))
                            .setDifficulty(getString(body, "difficulty", ""))
                            .setWeaknessProfile(getString(body, "weaknessProfile", ""))
                            .setTeacherSuggestions(getString(body, "teacherSuggestions", ""));

            if (body.get("count") instanceof Number count) {
                reqBuilder.setCount(count.intValue());
            }
            if (body.get("conceptTags") instanceof List tags) {
                for (Object tag : tags) {
                    reqBuilder.addConceptTags(String.valueOf(tag));
                }
            }

            AiQuestionGenerateResponse response =
                    authorize(aiQuestionStub, traceId, "")
                            .withDeadlineAfter(aiQuestionTimeoutSeconds, TimeUnit.SECONDS)
                            .generate(reqBuilder.build());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("routerDecision", response.getRouterDecision());

            List<Map<String, Object>> questions = new ArrayList<>();
            for (GeneratedQuestion question : response.getQuestionsList()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("question_type", question.getQuestionType());
                row.put("content", question.getContent());
                row.put("options", question.getOptionsMap());
                row.put("correct_answer", question.getCorrectAnswer());
                row.put("explanation", question.getExplanation());
                row.put("knowledge_points", new ArrayList<>(question.getKnowledgePointsList()));
                questions.add(row);
            }
            result.put("questions", questions);
            return result;
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("generateQuestions", ex);
        }
    }

    public Map<String, Object> generatePlan(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        try {
            LessonPlanGenerateRequest.Builder reqBuilder =
                    LessonPlanGenerateRequest.newBuilder()
                            .setTraceId(traceId)
                            .setIdempotencyKey(getString(body, "idempotencyKey", ""))
                            .setTopic(getString(body, "topic", ""))
                            .setGradeLevel(getString(body, "gradeLevel", ""))
                            .setTeacherId(getString(body, "teacherId", ""));
            if (body.get("durationMins") instanceof Number duration) {
                reqBuilder.setDurationMins(duration.intValue());
            }

            LessonPlanGenerateResponse response =
                    authorize(lessonPlanStub, traceId, "")
                            .withDeadlineAfter(lessonPlanTimeoutSeconds, TimeUnit.SECONDS)
                            .generate(reqBuilder.build());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contentMd", response.getContentMd());
            result.put("provider", response.getProvider());
            result.put("model", response.getModel());
            result.put("latencyMs", response.getLatencyMs());
            return result;
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("generatePlan", ex);
        }
    }

    public Map<String, Object> ingestKb(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        String idemKey = getString(body, "idempotencyKey", "");
        String classId = getString(body, "classId", "");
        if (classId.isBlank()) {
            throw new IllegalArgumentException("classId is required");
        }
        byte[] fileContent = toBytes(body.get("fileContent"));

        KbIngestRequest request =
                KbIngestRequest.newBuilder()
                        .setTraceId(traceId)
                        .setIdempotencyKey(idemKey)
                        .setJobId(getString(body, "jobId", ""))
                        .setDocumentId(getString(body, "documentId", ""))
                        .setTeacherId(getString(body, "teacherId", ""))
                        .setClassId(classId)
                        .setFilename(getString(body, "filename", "upload.bin"))
                        .setFileType(getString(body, "fileType", "application/octet-stream"))
                        .setFileContent(ByteString.copyFrom(fileContent))
                        .build();

        try {
            KbIngestResponse response =
                    authorize(kbStub, traceId, idemKey)
                            .withDeadlineAfter(kbIngestTimeoutSeconds, TimeUnit.SECONDS)
                            .ingest(request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.getStatus());
            result.put("jobId", response.getJobId());
            result.put("background", response.getBackground());
            result.put("chunks", response.getChunks());
            return result;
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("kb.ingest", ex);
        }
    }

    public Map<String, Object> deleteKb(Map<String, Object> body) {
        String traceId = getString(body, "traceId", UUID.randomUUID().toString());
        String idemKey = getString(body, "idempotencyKey", "");

        KbDeleteRequest request =
                KbDeleteRequest.newBuilder()
                        .setTraceId(traceId)
                        .setIdempotencyKey(idemKey)
                        .setDocumentId(getString(body, "documentId", ""))
                        .build();

        try {
            KbDeleteResponse response =
                    authorize(kbStub, traceId, idemKey)
                            .withDeadlineAfter(kbDeleteTimeoutSeconds, TimeUnit.SECONDS)
                            .delete(request);

            return Map.of("status", response.getStatus());
        } catch (StatusRuntimeException ex) {
            throw fromGrpcError("kb.delete", ex);
        }
    }

    private DependencyException fromGrpcError(String operation, StatusRuntimeException ex) {
        Status.Code grpcCode = ex.getStatus().getCode();
        ErrorCode errorCode =
                switch (grpcCode) {
                    case RESOURCE_EXHAUSTED -> ErrorCode.AI_RATE_LIMITED;
                    case DEADLINE_EXCEEDED -> ErrorCode.AI_TIMEOUT;
                    case UNAVAILABLE -> ErrorCode.AI_MODEL_UNAVAILABLE;
                    case INTERNAL -> ErrorCode.AI_OUTPUT_INVALID;
                    default -> ErrorCode.SYSTEM_DEPENDENCY;
                };
        String description = ex.getStatus().getDescription();
        String suffix = (description == null || description.isBlank()) ? "" : (": " + description);
        return new DependencyException(
                errorCode, "调用 AI 服务失败: " + operation + " [grpc=" + grpcCode + "]" + suffix, ex);
    }

    private ChatRequest buildChatRequest(Map<String, Object> body, String traceId) {
        ChatRequest.Builder reqBuilder =
                ChatRequest.newBuilder()
                        .setTraceId(traceId)
                        .setSessionId(getString(body, "sessionId", ""))
                        .setStudentId(getString(body, "studentId", ""))
                        .setMessage(getString(body, "message", ""));

        if (body.containsKey("stream") && body.get("stream") instanceof Boolean b && b) {
            reqBuilder.setStream(true);
        }

        if (body.get("context") instanceof Map ctxMap) {
            ChatRequest.Context.Builder ctxBuilder = ChatRequest.Context.newBuilder();
            if (ctxMap.get("history") instanceof List history) {
                for (Object item : history) {
                    if (item instanceof Map map) {
                        ctxBuilder.addHistory(
                                ChatRequest.Context.Message.newBuilder()
                                        .setRole(getString(map, "role", ""))
                                        .setContent(getString(map, "content", ""))
                                        .build());
                    }
                }
            }
            reqBuilder.setContext(ctxBuilder.build());
        }

        if (body.get("teacherScope") instanceof Map teacherScope) {
            reqBuilder.setTeacherScope(
                    ChatRequest.TeacherScope.newBuilder()
                            .setTeacherId(getString(teacherScope, "teacherId", ""))
                            .setClassId(getString(teacherScope, "classId", ""))
                            .build());
        }

        return reqBuilder.build();
    }

    private byte[] toBytes(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("fileContent is required");
        }
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        if (raw instanceof ByteString byteString) {
            return byteString.toByteArray();
        }
        if (raw instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("fileContent must be byte[] or string");
    }

    private String getString(Map<?, ?> map, String key, String defaultVal) {
        if (map == null) {
            return defaultVal;
        }
        Object val = map.get(key);
        if (val == null) {
            return defaultVal;
        }
        return String.valueOf(val);
    }
}
