package com.shuhongyi.trading.matching;

import com.shuhongyi.trading.order.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Limit order book for one instrument. Plain Java with no Spring or database dependency.
 *
 * <p>Each side is a {@link TreeMap} from price to a price level, sorted best price first
 * (bids descending, asks ascending). Each level keeps its orders in arrival order in a
 * {@link LinkedHashMap}, which gives FIFO iteration and O(1) removal by order ID.
 * An index from order ID to order makes cancellation O(log P) for P price levels.
 *
 * <p>Not thread-safe: the caller serialises access per instrument.
 */
public final class OrderBook {

    private final NavigableMap<BigDecimal, LinkedHashMap<Long, BookOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, LinkedHashMap<Long, BookOrder>> asks = new TreeMap<>();
    private final Map<Long, BookOrder> index = new HashMap<>();

    /** Adds a resting order at the back of its price level's queue. */
    public void add(BookOrder order) {
        if (index.containsKey(order.orderId())) {
            throw new IllegalArgumentException("Order already in book: " + order.orderId());
        }
        side(order.side()).computeIfAbsent(order.price(), price -> new LinkedHashMap<>()).put(order.orderId(), order);
        index.put(order.orderId(), order);
    }

    /** Removes an order; returns false if it was not in the book. */
    public boolean remove(long orderId) {
        BookOrder order = index.remove(orderId);
        if (order == null) {
            return false;
        }
        NavigableMap<BigDecimal, LinkedHashMap<Long, BookOrder>> side = side(order.side());
        LinkedHashMap<Long, BookOrder> level = side.get(order.price());
        level.remove(orderId);
        if (level.isEmpty()) {
            side.remove(order.price());
        }
        return true;
    }

    /**
     * Applies a committed match: reduces or removes the filled makers and, if a quantity rests,
     * adds the taker with its newly assigned order ID.
     */
    public void apply(MatchResult result, long takerOrderId, IncomingOrder taker) {
        for (Fill fill : result.fills()) {
            reduce(fill.makerOrderId(), fill.quantity());
        }
        if (result.restingQuantity() > 0) {
            add(new BookOrder(takerOrderId, taker.accountId(), taker.side(), taker.price(), result.restingQuantity()));
        }
    }

    public Optional<BookOrder> find(long orderId) {
        return Optional.ofNullable(index.get(orderId));
    }

    public Optional<BigDecimal> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    public Optional<BigDecimal> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    public int size() {
        return index.size();
    }

    /** Aggregated depth for the best {@code levels} prices on one side, best price first. */
    public List<PriceLevelView> depth(OrderSide side, int levels) {
        List<PriceLevelView> view = new ArrayList<>();
        for (Map.Entry<BigDecimal, LinkedHashMap<Long, BookOrder>> entry : side(side).entrySet()) {
            if (view.size() == levels) {
                break;
            }
            long quantity = entry.getValue().values().stream().mapToLong(BookOrder::remainingQuantity).sum();
            view.add(new PriceLevelView(entry.getKey(), quantity, entry.getValue().size()));
        }
        return view;
    }

    /** Resting orders on the side opposite to {@code takerSide}, in matching priority. Read-only. */
    Iterable<BookOrder> priorityOrderAgainst(OrderSide takerSide) {
        Collection<LinkedHashMap<Long, BookOrder>> levels = side(opposite(takerSide)).values();
        return () -> new Iterator<>() {
            private final Iterator<LinkedHashMap<Long, BookOrder>> levelIterator = levels.iterator();
            private Iterator<BookOrder> orderIterator = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!orderIterator.hasNext() && levelIterator.hasNext()) {
                    orderIterator = levelIterator.next().values().iterator();
                }
                return orderIterator.hasNext();
            }

            @Override
            public BookOrder next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return orderIterator.next();
            }
        };
    }

    private void reduce(long orderId, long quantity) {
        BookOrder order = index.get(orderId);
        if (order == null) {
            throw new IllegalStateException("Filled order not in book: " + orderId);
        }
        long remaining = order.remainingQuantity() - quantity;
        if (remaining < 0) {
            throw new IllegalStateException("Overfill of order %d by %d".formatted(orderId, -remaining));
        }
        if (remaining == 0) {
            remove(orderId);
            return;
        }
        BookOrder reduced = order.withRemaining(remaining);
        // Replacing the value of an existing key keeps its position in the LinkedHashMap: time priority is kept.
        side(order.side()).get(order.price()).put(orderId, reduced);
        index.put(orderId, reduced);
    }

    private NavigableMap<BigDecimal, LinkedHashMap<Long, BookOrder>> side(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    private static OrderSide opposite(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
