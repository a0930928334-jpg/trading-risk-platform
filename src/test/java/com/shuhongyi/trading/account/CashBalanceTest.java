package com.shuhongyi.trading.account;

import com.shuhongyi.trading.account.exception.InsufficientFundsException;
import com.shuhongyi.trading.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashBalanceTest {

    @Test
    void availableIsBalanceMinusReserved() {
        CashBalance cash = cash("1000.0000");
        cash.reserve(Money.of("250.5000"));

        assertThat(cash.getReserved()).isEqualByComparingTo("250.5000");
        assertThat(cash.getAvailable()).isEqualByComparingTo("749.5000");
        assertThat(cash.getBalance()).isEqualByComparingTo("1000.0000");
    }

    @Test
    void reservingMoreThanAvailableFails() {
        CashBalance cash = cash("100.0000");
        cash.reserve(Money.of("60.0000"));

        assertThatThrownBy(() -> cash.reserve(Money.of("40.0001")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(cash.getReserved()).isEqualByComparingTo("60.0000");
    }

    @Test
    void releaseReturnsReservedFundsToAvailable() {
        CashBalance cash = cash("100.0000");
        cash.reserve(Money.of("80.0000"));
        cash.release(Money.of("30.0000"));

        assertThat(cash.getReserved()).isEqualByComparingTo("50.0000");
        assertThat(cash.getAvailable()).isEqualByComparingTo("50.0000");
    }

    @Test
    void releasingMoreThanReservedFails() {
        CashBalance cash = cash("100.0000");
        cash.reserve(Money.of("10.0000"));

        assertThatThrownBy(() -> cash.release(Money.of("10.0001")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debitReservedReducesBothBalanceAndReserved() {
        CashBalance cash = cash("100.0000");
        cash.reserve(Money.of("80.0000"));
        cash.debitReserved(Money.of("75.0000"));

        assertThat(cash.getBalance()).isEqualByComparingTo("25.0000");
        assertThat(cash.getReserved()).isEqualByComparingTo("5.0000");
        assertThat(cash.getAvailable()).isEqualByComparingTo("20.0000");
    }

    @Test
    void debitCannotExceedReservedFunds() {
        CashBalance cash = cash("100.0000");
        cash.reserve(Money.of("10.0000"));

        assertThatThrownBy(() -> cash.debitReserved(Money.of("20.0000")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cash.getBalance()).isEqualByComparingTo("100.0000");
    }

    @Test
    void depositAndCreditIncreaseBalance() {
        CashBalance cash = cash("0.0000");
        cash.deposit(Money.of("10.0000"));
        cash.credit(Money.of("2.5000"));

        assertThat(cash.getBalance()).isEqualByComparingTo("12.5000");
        assertThat(cash.getAvailable()).isEqualByComparingTo("12.5000");
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        CashBalance cash = cash("100.0000");

        assertThatThrownBy(() -> cash.deposit(Money.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cash.reserve(Money.of("-1.0000"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static CashBalance cash(String balance) {
        return CashBalance.builder()
                .currency("USD")
                .balance(new BigDecimal(balance))
                .build();
    }
}
