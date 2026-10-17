package com.shuhongyi.trading.matching;

import com.shuhongyi.trading.order.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.shuhongyi.trading.order.OrderSide.BUY;
import static com.shuhongyi.trading.order.OrderSide.SELL;
import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long CAROL = 3;
    private static final long TAKER = 9;

    private final MatchingEngine engine = new MatchingEngine();
    private final OrderBook book = new OrderBook();

    @Test
    void emptyBookProducesNoFillsAndTheWholeOrderRests() {
        MatchResult result = engine.match(book, incoming(BUY, "100", 10));

        assertThat(result.fills()).isEmpty();
        assertThat(result.restingQuantity()).isEqualTo(10);
        assertThat(result.selfTradePrevented()).isFalse();
    }

    @Test
    void nonCrossingPricesDoNotTrade() {
        book.add(resting(1, ALICE, SELL, "101", 10));
        book.add(resting(2, BOB, BUY, "99", 10));

        assertThat(engine.match(book, incoming(BUY, "100.9999", 5)).fills()).isEmpty();
        assertThat(engine.match(book, incoming(SELL, "99.0001", 5)).fills()).isEmpty();
    }

    @Test
    void fullFillExecutesAtTheMakerPrice() {
        book.add(resting(1, ALICE, SELL, "100", 10));

        MatchResult result = engine.match(book, incoming(BUY, "105", 10));

        assertThat(result.fills()).containsExactly(new Fill(1, ALICE, price("100"), 10));
        assertThat(result.unfilledQuantity()).isZero();
        assertThat(result.restingQuantity()).isZero();
    }

    @Test
    void partialFillLeavesRemainderToRest() {
        book.add(resting(1, ALICE, SELL, "100", 4));

        MatchResult result = engine.match(book, incoming(BUY, "100", 10));

        assertThat(result.filledQuantity()).isEqualTo(4);
        assertThat(result.restingQuantity()).isEqualTo(6);
    }

    @Test
    void incomingOrderCanPartiallyFillAMaker() {
        book.add(resting(1, ALICE, SELL, "100", 10));

        MatchResult result = engine.match(book, incoming(BUY, "100", 3));

        assertThat(result.fills()).containsExactly(new Fill(1, ALICE, price("100"), 3));
        assertThat(result.restingQuantity()).isZero();
    }

    @Test
    void sweepsLevelsBestPriceFirstAndStopsAtTheLimit() {
        book.add(resting(1, ALICE, SELL, "102", 5));
        book.add(resting(2, BOB, SELL, "100", 5));
        book.add(resting(3, CAROL, SELL, "101", 5));
        book.add(resting(4, ALICE, SELL, "103", 5));

        MatchResult result = engine.match(book, incoming(BUY, "102", 20));

        assertThat(result.fills()).extracting(Fill::makerOrderId).containsExactly(2L, 3L, 1L);
        assertThat(result.fills()).extracting(Fill::price).containsExactly(price("100"), price("101"), price("102"));
        assertThat(result.restingQuantity()).isEqualTo(5);
    }

    @Test
    void earlierOrderAtTheSamePriceFillsFirst() {
        book.add(resting(5, ALICE, SELL, "100", 3));
        book.add(resting(7, BOB, SELL, "100", 3));
        book.add(resting(9, CAROL, SELL, "100", 3));

        MatchResult result = engine.match(book, incoming(BUY, "100", 5));

        assertThat(result.fills()).containsExactly(
                new Fill(5, ALICE, price("100"), 3),
                new Fill(7, BOB, price("100"), 2));
    }

    @Test
    void sellOrdersMatchHighestBidFirst() {
        book.add(resting(1, ALICE, BUY, "99", 5));
        book.add(resting(2, BOB, BUY, "101", 5));
        book.add(resting(3, CAROL, BUY, "100", 5));

        MatchResult result = engine.match(book, incoming(SELL, "100", 8));

        assertThat(result.fills()).containsExactly(
                new Fill(2, BOB, price("101"), 5),
                new Fill(3, CAROL, price("100"), 3));
        assertThat(result.restingQuantity()).isZero();
    }

    @Test
    void selfTradePreventionCancelsTheIncomingRemainder() {
        book.add(resting(1, ALICE, SELL, "100", 5));
        book.add(resting(2, TAKER, SELL, "101", 5));
        book.add(resting(3, BOB, SELL, "102", 5));

        MatchResult result = engine.match(book, incoming(BUY, "102", 12));

        assertThat(result.fills()).containsExactly(new Fill(1, ALICE, price("100"), 5));
        assertThat(result.selfTradePrevented()).isTrue();
        assertThat(result.cancelledQuantity()).isEqualTo(7);
        assertThat(result.restingQuantity()).isZero();
    }

    @Test
    void selfTradeAtTheTopOfTheBookCancelsEverything() {
        book.add(resting(1, TAKER, BUY, "100", 5));
        book.add(resting(2, ALICE, BUY, "99", 5));

        MatchResult result = engine.match(book, incoming(SELL, "99", 3));

        assertThat(result.fills()).isEmpty();
        assertThat(result.cancelledQuantity()).isEqualTo(3);
    }

    @Test
    void ownOrderThatDoesNotCrossDoesNotTriggerPrevention() {
        book.add(resting(1, ALICE, SELL, "100", 5));
        book.add(resting(2, TAKER, SELL, "105", 5));

        MatchResult result = engine.match(book, incoming(BUY, "100", 8));

        assertThat(result.selfTradePrevented()).isFalse();
        assertThat(result.restingQuantity()).isEqualTo(3);
    }

    @Test
    void ownOrderOnTheSameSideIsIrrelevant() {
        book.add(resting(1, TAKER, BUY, "100", 5));
        book.add(resting(2, ALICE, SELL, "100", 5));

        MatchResult result = engine.match(book, incoming(BUY, "100", 5));

        assertThat(result.selfTradePrevented()).isFalse();
        assertThat(result.filledQuantity()).isEqualTo(5);
    }

    @Test
    void matchingNeverChangesTheBook() {
        book.add(resting(1, ALICE, SELL, "100", 5));
        book.add(resting(2, BOB, SELL, "101", 5));

        engine.match(book, incoming(BUY, "101", 10));

        assertThat(book.size()).isEqualTo(2);
        assertThat(book.find(1)).get().extracting(BookOrder::remainingQuantity).isEqualTo(5L);
        assertThat(book.depth(SELL, 10)).hasSize(2);
    }

    private static IncomingOrder incoming(OrderSide side, String price, long quantity) {
        return new IncomingOrder(TAKER, side, price(price), quantity);
    }

    private static BookOrder resting(long id, long account, OrderSide side, String price, long quantity) {
        return new BookOrder(id, account, side, price(price), quantity);
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
