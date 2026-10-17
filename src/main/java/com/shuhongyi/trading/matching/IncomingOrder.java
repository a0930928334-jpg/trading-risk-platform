package com.shuhongyi.trading.matching;

import com.shuhongyi.trading.order.OrderSide;

import java.math.BigDecimal;

/**
 * A new limit order to be matched against the book. It has no ID yet: matching runs before the order is stored.
 */
public record IncomingOrder(long accountId, OrderSide side, BigDecimal price, long quantity) {

    public IncomingOrder {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
    }

    /** True if this order is willing to trade at {@code restingPrice}. */
    boolean crosses(BigDecimal restingPrice) {
        return side == OrderSide.BUY
                ? restingPrice.compareTo(price) <= 0
                : restingPrice.compareTo(price) >= 0;
    }
}
