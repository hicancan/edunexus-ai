package com.edunexus.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class GovernanceService {
    private final DbService db;
    private final ObjectMapper objectMapper;

    public GovernanceService(DbService db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getIdempotentReplay(String scope, String idemKey, String requestHash) {
        if (idemKey == null || idemKey.isBlank()) {
            return null;
        }
        if (!db.exists("select 1 from idempotency_keys where scope=? and idem_key=? and expires_at > now()", scope, idemKey)) {
            return null;
        }
        Map<String, Object> row = db.one(
                "select request_hash as \"requestHash\", response_snapshot::text as \"responseText\" from idempotency_keys where scope=? and idem_key=? and expires_at > now()",
                scope,
                idemKey
        );
        String savedHash = String.valueOf(row.get("requestHash"));
        if (!savedHash.equals(requestHash)) {
            throw new IllegalArgumentException("Idempotency-Key 已用于不同请求");
        }
        String responseText = String.valueOf(row.get("responseText"));
        try {
            return objectMapper.readValue(responseText, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("幂等回放数据损坏");
        }
    }

    public void storeIdempotency(String scope, String idemKey, String requestHash, Map<String, Object> responseSnapshot, Duration ttl) {
        if (idemKey == null || idemKey.isBlank()) {
            return;
        }
        String json = toJson(responseSnapshot);
        long ttlSeconds = Math.max(300, ttl.getSeconds());
        db.update(
                """
                insert into idempotency_keys(id,scope,idem_key,request_hash,response_snapshot,expires_at)
                values (?,?,?,?,?::jsonb, now() + (?::text || ' seconds')::interval)
                on conflict (scope, idem_key)
                do nothing
                """,
                db.newId(),
                scope,
                idemKey,
                requestHash,
                json,
                String.valueOf(ttlSeconds)
        );
    }

    public UUID createJobRun(String jobType, UUID businessId, Map<String, Object> payload) {
        UUID jobId = db.newId();
        db.update(
                "insert into job_runs(id,job_type,business_id,status,attempt,payload) values (?,?,?,'PENDING',1,?::jsonb)",
                jobId,
                jobType,
                businessId,
                toJson(payload)
        );
        return jobId;
    }

    public void markJobRunning(UUID jobId) {
        db.update("update job_runs set status='RUNNING',started_at=coalesce(started_at,now()),updated_at=now() where id=?", jobId);
    }

    public void markJobSucceeded(UUID jobId, Map<String, Object> result) {
        db.update(
                "update job_runs set status='SUCCEEDED',result=?::jsonb,error_message=null,finished_at=now(),updated_at=now() where id=?",
                toJson(result),
                jobId
        );
    }

    public void markJobFailed(UUID jobId, String errorMessage) {
        db.update(
                "update job_runs set status='FAILED',error_message=?,finished_at=now(),updated_at=now() where id=?",
                errorMessage,
                jobId
        );
    }

    public void markJobDeadLetter(UUID jobId, String errorMessage) {
        db.update(
                "update job_runs set status='DEAD_LETTER',error_message=?,finished_at=now(),updated_at=now() where id=?",
                errorMessage,
                jobId
        );
    }

    public void audit(UUID actorId, String actorRole, String action, String resourceType, String resourceId, String traceId) {
        db.update(
                "insert into audit_logs(id,actor_id,actor_role,action,resource_type,resource_id,detail) values (?,?,?,?,?,?,?::jsonb)",
                db.newId(),
                actorId,
                actorRole,
                action,
                resourceType,
                resourceId,
                toJson(Map.of("traceId", traceId == null ? "" : traceId))
        );
    }

    public String requestHash(Object requestPayload) {
        return sha256(toJson(requestPayload));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
