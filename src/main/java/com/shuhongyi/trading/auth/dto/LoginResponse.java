package com.shuhongyi.trading.auth.dto;

public record LoginResponse(
        Long userId,
        String email,
        String fullName,
        String message
) {
}
