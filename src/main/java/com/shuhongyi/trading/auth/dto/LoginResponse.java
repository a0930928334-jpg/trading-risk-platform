package com.shuhongyi.trading.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        Long userId,
        String email,
        String fullName
) {
}
