package com.edunexus.api.auth;

import java.util.UUID;

public record AuthUser(UUID userId, String username, String role, String status) {}
