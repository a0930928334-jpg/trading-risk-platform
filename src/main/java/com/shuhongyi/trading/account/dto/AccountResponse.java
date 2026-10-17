package com.shuhongyi.trading.account.dto;

import com.shuhongyi.trading.account.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        AccountStatus status,
        String currency,
        BigDecimal balance,
        BigDecimal reserved,
        BigDecimal available
) {
}
