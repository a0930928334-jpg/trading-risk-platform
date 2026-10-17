package com.shuhongyi.trading.matching;

import java.util.List;

/**
 * Outcome of matching one incoming order.
 *
 * @param fills                executions in the order they happened
 * @param unfilledQuantity     quantity left after the fills
 * @param selfTradePrevented   true if matching stopped at the taker's own resting order; the unfilled
 *                             quantity is then cancelled instead of resting (cancel-newest policy)
 */
public record MatchResult(List<Fill> fills, long unfilledQuantity, boolean selfTradePrevented) {

    public MatchResult {
        fills = List.copyOf(fills);
    }

    public long filledQuantity() {
        return fills.stream().mapToLong(Fill::quantity).sum();
    }

    /** Quantity that should be added to the book as a new resting order. */
    public long restingQuantity() {
        return selfTradePrevented ? 0 : unfilledQuantity;
    }

    /** Quantity cancelled by self-trade prevention. */
    public long cancelledQuantity() {
        return selfTradePrevented ? unfilledQuantity : 0;
    }
}
