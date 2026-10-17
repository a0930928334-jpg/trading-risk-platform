package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskProperties;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Caps the value of a single order (fat-finger protection). Applies to buys and sells. */
@Component
@Order(30)
public class MaxOrderNotionalRule implements RiskRule {

    public static final String CODE = "MAX_NOTIONAL_EXCEEDED";

    private final RiskProperties properties;

    public MaxOrderNotionalRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public RiskDecision evaluate(RiskContext context) {
        BigDecimal notional = context.notional();
        if (notional.compareTo(properties.maxOrderNotional()) > 0) {
            return RiskDecision.reject(CODE, "Order value %s exceeds the limit of %s".formatted(
                    notional.toPlainString(), properties.maxOrderNotional().toPlainString()));
        }
        return RiskDecision.accept();
    }
}
