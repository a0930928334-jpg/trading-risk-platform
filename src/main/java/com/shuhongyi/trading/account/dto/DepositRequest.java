package com.shuhongyi.trading.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @Schema(example = "50000.00", description = "USD, at most 4 decimal places")
        @NotNull
        @DecimalMin(value = "0.0001", message = "must be positive")
        @DecimalMax(value = "1000000.0000", message = "must not exceed 1,000,000 per deposit")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount
) {
}
