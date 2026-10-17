package com.shuhongyi.trading.order.dto;

import com.shuhongyi.trading.matching.PriceLevelView;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated order book depth, best prices first.
 */
public record OrderBookResponse(
        String symbol,
        BigDecimal lastTradePrice,
        BigDecimal referencePrice,
        List<PriceLevelView> bids,
        List<PriceLevelView> asks
) {
}
