package com.shuhongyi.trading.auth.dto;

public record RegisterResponse(
        Long userId,
        String email,
        String fullName,
        Long accountId,
        String accountNumber,
        String currency
) {
}
