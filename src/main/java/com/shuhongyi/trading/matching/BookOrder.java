package com.shuhongyi.trading.matching;

import com.shuhongyi.trading.order.OrderSide;

import java.math.BigDecimal;

/**
 * An open order as the order book sees it. Immutable; a partial fill replaces it with a smaller copy.
 * The order ID is the time-priority sequence: a lower ID arrived earlier.
 */
public record BookOrder(long orderId, long accountId, OrderSide side, BigDecimal price, long remainingQuantity) {

    public BookOrder {
        if (remainingQuantity <= 0) {
            throw new IllegalArgumentException("Resting quantity must be positive: " + remainingQuantity);
        }
    }

    BookOrder withRemaining(long quantity) {
        return new BookOrder(orderId, accountId, side, price, quantity);
    }
}
