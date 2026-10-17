package com.shuhongyi.trading.account.dto;

import com.shuhongyi.trading.account.CashLedgerEntry;
import com.shuhongyi.trading.account.CashLedgerEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerEntryResponse(
        Long id,
        CashLedgerEntryType type,
        String currency,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal reservedAfter,
        Long orderId,
        Long tradeId,
        LocalDateTime createdAt
) {

    public static LedgerEntryResponse from(CashLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getCurrency(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getReservedAfter(),
                entry.getOrderId(),
                entry.getTradeId(),
                entry.getCreatedAt());
    }
}
