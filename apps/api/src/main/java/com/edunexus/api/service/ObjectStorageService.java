package com.edunexus.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class ObjectStorageService {
    private final S3Client s3;
    private final String bucket;

    public ObjectStorageService(S3Client s3, @Value("${app.s3.bucket:edunexus-kb}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
        ensureBucket();
    }

    public String upload(String fileName, String contentType, byte[] content) {
        String key = "documents/" + UUID.randomUUID() + "-" + safeName(fileName);
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content)
        );
        return "s3://" + bucket + "/" + key;
    }

    public byte[] download(String storagePath) {
        ParsedPath parsed = parse(storagePath);
        ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(parsed.bucket())
                        .key(parsed.key())
                        .build()
        );
        return response.asByteArray();
    }

    public void delete(String storagePath) {
        ParsedPath parsed = parse(storagePath);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(parsed.bucket()).key(parsed.key()).build());
    }

    private ParsedPath parse(String storagePath) {
        if (storagePath == null || !storagePath.startsWith("s3://")) {
            throw new IllegalArgumentException("storage_path 不是 s3 路径");
        }
        String raw = storagePath.substring("s3://".length());
        int idx = raw.indexOf('/');
        if (idx <= 0 || idx == raw.length() - 1) {
            throw new IllegalArgumentException("storage_path 格式非法");
        }
        return new ParsedPath(raw.substring(0, idx), raw.substring(idx + 1));
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (Exception ex) {
            if (!ex.getMessage().contains("Not Found")) {
                throw ex;
            }
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private String safeName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }

    private record ParsedPath(String bucket, String key) {}
}
