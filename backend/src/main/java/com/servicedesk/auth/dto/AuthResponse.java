package com.servicedesk.auth.dto;

import com.servicedesk.user.Role;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String fullName,
        Role role,
        String accessToken,
        String refreshToken,
        long expiresInMs
) {}
