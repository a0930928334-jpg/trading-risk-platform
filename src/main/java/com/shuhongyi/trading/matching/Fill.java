package com.shuhongyi.trading.matching;

import java.math.BigDecimal;

/**
 * One execution against a resting (maker) order. The price is always the maker's price.
 */
public record Fill(long makerOrderId, long makerAccountId, BigDecimal price, long quantity) {
}
