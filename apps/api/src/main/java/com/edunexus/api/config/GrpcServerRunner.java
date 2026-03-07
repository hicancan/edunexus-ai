package com.edunexus.api.config;

import com.edunexus.api.service.JobStatusServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class GrpcServerRunner implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

    private Server server;
    private final JobStatusServiceImpl jobStatusService;
    private final int port;
    private boolean isRunning = false;

    public GrpcServerRunner(
            JobStatusServiceImpl jobStatusService,
            @Value("${app.grpc.server.port:9090}") int port) {
        this.jobStatusService = jobStatusService;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port).addService(jobStatusService).build().start();
            log.info("gRPC Server started, listening on " + port);

            // Add shutdown hook
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        log.info(
                                                "Shutting down gRPC server since JVM is shutting down");
                                        GrpcServerRunner.this.stop();
                                        log.info("gRPC server shut down");
                                    }));

            isRunning = true;
        } catch (IOException e) {
            log.error("Failed to start gRPC server", e);
            throw new RuntimeException("Failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}
