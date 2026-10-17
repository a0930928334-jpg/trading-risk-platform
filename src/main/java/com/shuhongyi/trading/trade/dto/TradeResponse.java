package com.shuhongyi.trading.trade.dto;

import com.shuhongyi.trading.order.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A trade from the caller's point of view.
 *
 * @param side      the caller's side of the trade
 * @param orderId   the caller's order that traded
 * @param liquidity {@code TAKER} if the caller's order was the incoming one, otherwise {@code MAKER}
 */
public record TradeResponse(
        Long id,
        String symbol,
        OrderSide side,
        BigDecimal price,
        long quantity,
        BigDecimal value,
        Long orderId,
        Liquidity liquidity,
        LocalDateTime executedAt
) {

    public enum Liquidity {
        MAKER,
        TAKER
    }
}
