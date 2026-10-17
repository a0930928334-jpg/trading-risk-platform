package com.shuhongyi.trading.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Price-time priority matching. Pure function of (book, incoming order) to {@link MatchResult}:
 * it reads the book but never changes it, so the caller can persist the result first and apply it
 * to the book only after the database transaction commits.
 *
 * <ul>
 *   <li>Best price first; within a price, earliest order first.</li>
 *   <li>Trades execute at the resting (maker) order's price.</li>
 *   <li>Partial fills are allowed on both sides.</li>
 *   <li>Self-trade prevention, cancel newest: on reaching a resting order from the same account,
 *       matching stops and the incoming order's unfilled quantity is cancelled.</li>
 * </ul>
 *
 * Cost is O(L + F) for L price levels visited and F orders filled.
 */
public final class MatchingEngine {

    public MatchResult match(OrderBook book, IncomingOrder incoming) {
        List<Fill> fills = new ArrayList<>();
        long unfilled = incoming.quantity();

        for (BookOrder resting : book.priorityOrderAgainst(incoming.side())) {
            if (unfilled == 0 || !incoming.crosses(resting.price())) {
                break;
            }
            if (resting.accountId() == incoming.accountId()) {
                return new MatchResult(fills, unfilled, true);
            }
            long quantity = Math.min(unfilled, resting.remainingQuantity());
            fills.add(new Fill(resting.orderId(), resting.accountId(), resting.price(), quantity));
            unfilled -= quantity;
        }
        return new MatchResult(fills, unfilled, false);
    }
}
