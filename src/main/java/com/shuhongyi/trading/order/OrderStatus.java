package com.shuhongyi.trading.order;

public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED;

    /** Open orders rest on the order book and hold a reservation. */
    public boolean isOpen() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
}
