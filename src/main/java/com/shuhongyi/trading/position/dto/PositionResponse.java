package com.shuhongyi.trading.position.dto;

public record PositionResponse(
        String symbol,
        long quantity,
        long reservedQuantity,
        long availableQuantity
) {
}
