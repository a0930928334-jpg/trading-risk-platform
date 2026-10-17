package com.shuhongyi.trading.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary conventions: every amount is a {@link BigDecimal} with scale 4, and USD is the only currency.
 */
public final class Money {

    public static final int SCALE = 4;
    public static final String CURRENCY = "USD";
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /**
     * Normalises to scale 4. Inputs are validated to at most 4 decimal places before they reach the domain,
     * so this never rounds a real value; {@link RoundingMode#UNNECESSARY} turns any violation into an error.
     */
    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }

    /** Notional value of {@code quantity} shares at {@code price}. Exact, because price has scale 4. */
    public static BigDecimal notional(long quantity, BigDecimal price) {
        return of(price.multiply(BigDecimal.valueOf(quantity)));
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount.signum() > 0;
    }
}
