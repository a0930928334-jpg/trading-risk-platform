package com.shuhongyi.trading.matching;

import java.math.BigDecimal;

/** Aggregated depth at one price: total resting quantity and number of orders. */
public record PriceLevelView(BigDecimal price, long quantity, int orderCount) {
}
