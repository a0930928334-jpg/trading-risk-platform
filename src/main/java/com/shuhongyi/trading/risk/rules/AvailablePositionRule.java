package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A sell order must be covered by available shares. This is what forbids short selling. */
@Component
@Order(60)
public class AvailablePositionRule implements RiskRule {

    public static final String CODE = "INSUFFICIENT_POSITION";

    @Override
    public RiskDecision evaluate(RiskContext context) {
        if (context.side() != OrderSide.SELL) {
            return RiskDecision.accept();
        }
        long available = context.position() == null ? 0 : context.position().getAvailableQuantity();
        if (available < context.quantity()) {
            return RiskDecision.reject(CODE, "Required %d shares, available %d".formatted(
                    context.quantity(), available));
        }
        return RiskDecision.accept();
    }
}
