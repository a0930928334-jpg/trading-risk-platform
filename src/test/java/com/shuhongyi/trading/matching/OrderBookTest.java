package com.shuhongyi.trading.matching;

import com.shuhongyi.trading.order.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.shuhongyi.trading.order.OrderSide.BUY;
import static com.shuhongyi.trading.order.OrderSide.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBookTest {

    private final OrderBook book = new OrderBook();

    @Test
    void bestBidIsHighestAndBestAskIsLowest() {
        book.add(order(1, 1, BUY, "99", 1));
        book.add(order(2, 1, BUY, "100", 1));
        book.add(order(3, 2, SELL, "102", 1));
        book.add(order(4, 2, SELL, "101", 1));

        assertThat(book.bestBid()).contains(price("100"));
        assertThat(book.bestAsk()).contains(price("101"));
    }

    @Test
    void depthAggregatesEachLevelBestFirstAndHonoursTheLimit() {
        book.add(order(1, 1, SELL, "101", 5));
        book.add(order(2, 2, SELL, "101", 7));
        book.add(order(3, 1, SELL, "103", 1));
        book.add(order(4, 1, SELL, "102", 2));

        assertThat(book.depth(SELL, 2)).containsExactly(
                new PriceLevelView(price("101"), 12, 2),
                new PriceLevelView(price("102"), 2, 1));
        assertThat(book.depth(BUY, 5)).isEmpty();
    }

    @Test
    void removeDeletesOrderAndEmptyLevel() {
        book.add(order(1, 1, BUY, "100", 5));
        book.add(order(2, 2, BUY, "100", 5));

        assertThat(book.remove(1)).isTrue();
        assertThat(book.depth(BUY, 5)).containsExactly(new PriceLevelView(price("100"), 5, 1));
        assertThat(book.remove(2)).isTrue();
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.remove(2)).isFalse();
    }

    @Test
    void duplicateOrderIdIsRejected() {
        book.add(order(1, 1, BUY, "100", 5));

        assertThatThrownBy(() -> book.add(order(1, 1, BUY, "101", 5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyReducesPartiallyFilledMakerButKeepsItsTimePriority() {
        book.add(order(1, 1, SELL, "100", 10));
        book.add(order(2, 2, SELL, "100", 10));
        IncomingOrder taker = new IncomingOrder(3, BUY, price("100"), 4);
        MatchResult result = new MatchingEngine().match(book, taker);

        book.apply(result, 50, taker);

        assertThat(book.find(1)).get().extracting(BookOrder::remainingQuantity).isEqualTo(6L);
        MatchResult next = new MatchingEngine().match(book, new IncomingOrder(3, BUY, price("100"), 1));
        assertThat(next.fills()).extracting(Fill::makerOrderId).containsExactly(1L);
    }

    @Test
    void applyRemovesFilledMakersAndRestsTakerRemainderUnderItsNewId() {
        book.add(order(1, 1, SELL, "100", 3));
        IncomingOrder taker = new IncomingOrder(2, BUY, price("100"), 10);
        MatchResult result = new MatchingEngine().match(book, taker);

        book.apply(result, 42, taker);

        assertThat(book.find(1)).isEmpty();
        assertThat(book.find(42)).contains(new BookOrder(42, 2, BUY, price("100"), 7));
        assertThat(book.bestAsk()).isEmpty();
        assertThat(book.bestBid()).contains(price("100"));
    }

    @Test
    void selfTradeCancelledRemainderIsNotAddedToTheBook() {
        book.add(order(1, 7, SELL, "100", 5));
        IncomingOrder taker = new IncomingOrder(7, BUY, price("100"), 5);
        MatchResult result = new MatchingEngine().match(book, taker);

        book.apply(result, 2, taker);

        assertThat(book.find(2)).isEmpty();
        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void randomOrderFlowNeverLeavesTheBookCrossedAndConservesQuantity() {
        Random random = new Random(20261017L);
        MatchingEngine engine = new MatchingEngine();
        List<Long> restingIds = new ArrayList<>();
        long submitted = 0;
        long filled = 0;
        long cancelled = 0;

        for (long id = 1; id <= 5_000; id++) {
            if (!restingIds.isEmpty() && random.nextInt(10) == 0) {
                long victim = restingIds.remove(random.nextInt(restingIds.size()));
                cancelled += book.find(victim).map(BookOrder::remainingQuantity).orElse(0L);
                book.remove(victim);
                continue;
            }
            OrderSide side = random.nextBoolean() ? BUY : SELL;
            BigDecimal price = BigDecimal.valueOf(9_500 + random.nextInt(1_000), 2).setScale(4);
            long quantity = 1 + random.nextInt(100);
            IncomingOrder incoming = new IncomingOrder(random.nextInt(20), side, price, quantity);
            submitted += quantity;

            MatchResult result = engine.match(book, incoming);
            book.apply(result, id, incoming);
            filled += 2 * result.filledQuantity();
            cancelled += result.cancelledQuantity();
            if (result.restingQuantity() > 0) {
                restingIds.add(id);
            }
            restingIds.removeIf(restingId -> book.find(restingId).isEmpty());

            if (book.bestBid().isPresent() && book.bestAsk().isPresent()) {
                assertThat(book.bestBid().get()).isLessThan(book.bestAsk().get());
            }
        }

        long resting = book.depth(BUY, Integer.MAX_VALUE).stream().mapToLong(PriceLevelView::quantity).sum()
                + book.depth(SELL, Integer.MAX_VALUE).stream().mapToLong(PriceLevelView::quantity).sum();
        // Every submitted share is either filled (counted once on each side), cancelled, or still resting.
        assertThat(filled + cancelled + resting).isEqualTo(submitted);
    }

    private static BookOrder order(long id, long account, OrderSide side, String price, long quantity) {
        return new BookOrder(id, account, side, price(price), quantity);
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
