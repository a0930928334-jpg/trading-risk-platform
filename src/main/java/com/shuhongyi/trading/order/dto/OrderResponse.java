package com.shuhongyi.trading.order.dto;

import com.shuhongyi.trading.order.Order;
import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.order.OrderStatus;
import com.shuhongyi.trading.order.OrderType;
import com.shuhongyi.trading.order.TimeInForce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String symbol,
        OrderSide side,
        OrderType type,
        TimeInForce timeInForce,
        BigDecimal price,
        long quantity,
        long filledQuantity,
        long remainingQuantity,
        OrderStatus status,
        String rejectCode,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OrderResponse from(Order order, String symbol) {
        return new OrderResponse(
                order.getId(),
                symbol,
                order.getSide(),
                order.getType(),
                order.getTimeInForce(),
                order.getPrice(),
                order.getQuantity(),
                order.getFilledQuantity(),
                order.isOpen() ? order.getRemainingQuantity() : 0,
                order.getStatus(),
                order.getRejectCode(),
                order.getRejectReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
