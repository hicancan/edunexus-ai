package com.edunexus.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class AppConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean(name = "documentIngestExecutor")
    public TaskExecutor documentIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-ingest-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatStreamExecutor")
    public TaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-stream-");
        executor.initialize();
        return executor;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value(
                            "${app.cors-allowed-origin-patterns:http://127.0.0.1:5173,http://localhost:5173,http://*:5173}")
                    String[] allowedOriginPatterns) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(allowedOriginPatterns)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("X-Request-Id");
            }
        };
    }

    @Bean
    public S3Client s3Client(
            @Value("${app.s3.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${app.s3.region:us-east-1}") String region,
            @Value("${app.s3.access-key:minioadmin}") String accessKey,
            @Value("${app.s3.secret-key:minioadmin}") String secretKey,
            @Value("${app.s3.force-path-style:true}") boolean forcePathStyle) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(forcePathStyle).build())
                .build();
    }
}
