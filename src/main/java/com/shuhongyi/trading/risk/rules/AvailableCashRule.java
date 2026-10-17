package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** A buy order must be fully covered by available cash: quantity x limit price is reserved up front. */
@Component
@Order(50)
public class AvailableCashRule implements RiskRule {

    public static final String CODE = "INSUFFICIENT_FUNDS";

    @Override
    public RiskDecision evaluate(RiskContext context) {
        if (context.side() != OrderSide.BUY) {
            return RiskDecision.accept();
        }
        BigDecimal required = context.notional();
        BigDecimal available = context.cash().getAvailable();
        if (available.compareTo(required) < 0) {
            return RiskDecision.reject(CODE, "Required %s, available %s".formatted(
                    required.toPlainString(), available.toPlainString()));
        }
        return RiskDecision.accept();
    }
}
