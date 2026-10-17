package com.shuhongyi.trading.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void partialThenFullFillMovesStatusThroughPartiallyFilledToFilled() {
        Order order = newOrder(10);

        order.fill(4);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getRemainingQuantity()).isEqualTo(6);

        order.fill(6);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void cannotOverfill() {
        Order order = newOrder(10);
        order.fill(7);

        assertThatThrownBy(() -> order.fill(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remainingNotionalUsesRemainingQuantityAtLimitPrice() {
        Order order = newOrder(10);
        order.fill(3);

        assertThat(order.getRemainingNotional()).isEqualByComparingTo("87.5000");
    }

    @Test
    void cancelledOrderCanNeitherFillNorCancelAgain() {
        Order order = newOrder(10);
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> order.fill(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyNewAndPartiallyFilledAreOpen() {
        assertThat(OrderStatus.NEW.isOpen()).isTrue();
        assertThat(OrderStatus.PARTIALLY_FILLED.isOpen()).isTrue();
        assertThat(OrderStatus.FILLED.isOpen()).isFalse();
        assertThat(OrderStatus.CANCELLED.isOpen()).isFalse();
        assertThat(OrderStatus.REJECTED.isOpen()).isFalse();
    }

    private static Order newOrder(long quantity) {
        return Order.builder()
                .id(1L)
                .side(OrderSide.BUY)
                .price(new BigDecimal("12.5000"))
                .quantity(quantity)
                .status(OrderStatus.NEW)
                .build();
    }
}
