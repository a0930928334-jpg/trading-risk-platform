package com.shuhongyi.trading.order;

import com.shuhongyi.trading.matching.BookOrder;
import com.shuhongyi.trading.matching.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds one in-memory {@link OrderBook} per instrument. The books are a cache of the open orders in the
 * database, which stays the source of truth: any book can be rebuilt from the orders table at any time.
 *
 * <p>All books with open orders are rebuilt during startup, before the web server accepts requests.
 * Callers must hold the instrument's lock while reading or changing its book.
 */
@Component
public class OrderBookRegistry implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(OrderBookRegistry.class);
    private static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);

    private final ConcurrentMap<Long, OrderBook> books = new ConcurrentHashMap<>();
    private final OrderRepository orderRepository;

    public OrderBookRegistry(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int orders = 0;
        for (Long instrumentId : orderRepository.findInstrumentIdsWithStatusIn(OPEN)) {
            orders += rebuild(instrumentId).size();
        }
        log.info("Rebuilt {} order books with {} open orders from the database", books.size(), orders);
    }

    /** The instrument's book, loaded from the database on first use. */
    public OrderBook book(Long instrumentId) {
        return books.computeIfAbsent(instrumentId, this::load);
    }

    /** Replaces the instrument's book with a fresh copy of its open orders from the database. */
    public OrderBook rebuild(Long instrumentId) {
        OrderBook book = load(instrumentId);
        books.put(instrumentId, book);
        return book;
    }

    private OrderBook load(Long instrumentId) {
        OrderBook book = new OrderBook();
        // Ascending ID order recreates the original time priority within each price level.
        for (Order order : orderRepository.findByInstrumentIdAndStatusInOrderByIdAsc(instrumentId, OPEN)) {
            book.add(new BookOrder(
                    order.getId(),
                    order.getAccountId(),
                    order.getSide(),
                    order.getPrice(),
                    order.getRemainingQuantity()));
        }
        return book;
    }
}
