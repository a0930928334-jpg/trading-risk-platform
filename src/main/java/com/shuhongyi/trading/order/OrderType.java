package com.shuhongyi.trading.order;

/**
 * Only limit orders exist in v0.1. A market order has no price, so the amount of cash to reserve
 * cannot be known when it is placed; see ADR 0005.
 */
public enum OrderType {
    LIMIT
}
