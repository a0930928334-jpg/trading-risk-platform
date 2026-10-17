package com.shuhongyi.trading.position;

import com.shuhongyi.trading.position.exception.InsufficientPositionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    @Test
    void reserveMovesSharesFromAvailableToReserved() {
        Position position = position(100);
        position.reserve(30);

        assertThat(position.getQuantity()).isEqualTo(100);
        assertThat(position.getReservedQuantity()).isEqualTo(30);
        assertThat(position.getAvailableQuantity()).isEqualTo(70);
    }

    @Test
    void cannotReserveMoreThanAvailableSoShortSellingIsImpossible() {
        Position position = position(10);
        position.reserve(6);

        assertThatThrownBy(() -> position.reserve(5)).isInstanceOf(InsufficientPositionException.class);
        assertThat(position.getReservedQuantity()).isEqualTo(6);
    }

    @Test
    void emptyPositionCannotBeSold() {
        assertThatThrownBy(() -> position(0).reserve(1)).isInstanceOf(InsufficientPositionException.class);
    }

    @Test
    void releaseReturnsReservedShares() {
        Position position = position(50);
        position.reserve(20);
        position.release(15);

        assertThat(position.getReservedQuantity()).isEqualTo(5);
        assertThat(position.getAvailableQuantity()).isEqualTo(45);
        assertThatThrownBy(() -> position.release(6)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debitReservedDeliversSharesForASellFill() {
        Position position = position(50);
        position.reserve(20);
        position.debitReserved(12);

        assertThat(position.getQuantity()).isEqualTo(38);
        assertThat(position.getReservedQuantity()).isEqualTo(8);
        assertThatThrownBy(() -> position.debitReserved(9)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void creditAddsBoughtShares() {
        Position position = position(0);
        position.credit(25);

        assertThat(position.getQuantity()).isEqualTo(25);
        assertThat(position.getAvailableQuantity()).isEqualTo(25);
    }

    @Test
    void nonPositiveShareCountsAreRejected() {
        Position position = position(10);

        assertThatThrownBy(() -> position.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.credit(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Position position(long quantity) {
        return Position.builder().accountId(1L).instrumentId(1L).quantity(quantity).build();
    }
}
