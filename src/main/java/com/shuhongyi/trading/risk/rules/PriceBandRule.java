package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.risk.RiskContext;
import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskProperties;
import com.shuhongyi.trading.risk.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Rejects limit prices more than the configured percentage away from the mark price
 * (the last trade price, or the reference price if the instrument has not traded yet).
 */
@Component
@Order(40)
public class PriceBandRule implements RiskRule {

    public static final String CODE = "PRICE_OUT_OF_BAND";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RiskProperties properties;

    public PriceBandRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public RiskDecision evaluate(RiskContext context) {
        BigDecimal mark = context.instrument().getMarkPrice();
        BigDecimal deviation = context.price().subtract(mark).abs();
        // deviation / mark > band% / 100  <=>  deviation * 100 > mark * band%  (no division, no rounding)
        if (deviation.multiply(HUNDRED).compareTo(mark.multiply(properties.priceBandPercent())) > 0) {
            return RiskDecision.reject(CODE, "Limit price %s is more than %s%% away from the mark price %s".formatted(
                    context.price().toPlainString(),
                    properties.priceBandPercent().stripTrailingZeros().toPlainString(),
                    mark.toPlainString()));
        }
        return RiskDecision.accept();
    }
}
