package com.shuhongyi.trading.account.dto;

public record DepositResponse(
        LedgerEntryResponse entry,
        AccountResponse account
) {
}
