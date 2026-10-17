package com.shuhongyi.trading.order.dto;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.order.OrderType;
import com.shuhongyi.trading.order.TimeInForce;
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
        @NotBlank String symbol,
        @NotNull OrderSide side,
        OrderType type,
        TimeInForce timeInForce,
        @NotNull
        @DecimalMin(value = "0.0001", message = "must be positive")
        @Digits(integer = 15, fraction = 4)
        BigDecimal price,
        @Positive @Max(1_000_000) long quantity
) {

    public OrderType typeOrDefault() {
        return type == null ? OrderType.LIMIT : type;
    }

    public TimeInForce timeInForceOrDefault() {
        return timeInForce == null ? TimeInForce.GTC : timeInForce;
    }
}
