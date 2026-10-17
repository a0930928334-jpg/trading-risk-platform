package com.shuhongyi.trading.order.dto;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.order.OrderType;
import com.shuhongyi.trading.order.TimeInForce;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param type        optional; only {@code LIMIT} exists in v0.1
 * @param timeInForce optional; only {@code GTC} exists in v0.1
 */
public record PlaceOrderRequest(
        @Schema(example = "AAPL") @NotBlank String symbol,
        @Schema(example = "BUY") @NotNull OrderSide side,
        @Schema(description = "Optional, defaults to LIMIT", example = "LIMIT") OrderType type,
        @Schema(description = "Optional, defaults to GTC", example = "GTC") TimeInForce timeInForce,
        @Schema(example = "190.50", description = "Limit price, at most 4 decimal places")
        @NotNull
        @DecimalMin(value = "0.0001", message = "must be positive")
        @Digits(integer = 15, fraction = 4)
        BigDecimal price,
        @Schema(example = "10", description = "Whole shares") @Positive @Max(1_000_000) long quantity
) {

    public OrderType typeOrDefault() {
        return type == null ? OrderType.LIMIT : type;
    }

    public TimeInForce timeInForceOrDefault() {
        return timeInForce == null ? TimeInForce.GTC : timeInForce;
    }
}
