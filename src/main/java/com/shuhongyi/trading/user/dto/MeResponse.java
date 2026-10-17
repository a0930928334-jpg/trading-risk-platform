package com.shuhongyi.trading.user.dto;

import com.shuhongyi.trading.user.UserStatus;

public record MeResponse(
        Long userId,
        String email,
        String fullName,
        UserStatus status,
        Long accountId,
        String accountNumber,
        String currency
) {
}
