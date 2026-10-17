package com.shuhongyi.trading.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Configurable pre-trade limits bound from {@code app.risk.*}.
 *
 * @param maxOrderNotional  largest allowed {@code quantity x price} for a single order (default 100,000)
 * @param priceBandPercent  maximum deviation of the limit price from the mark price, in percent (default 10)
 */
@ConfigurationProperties(prefix = "app.risk")
public record RiskProperties(BigDecimal maxOrderNotional, BigDecimal priceBandPercent) {

    public RiskProperties {
        if (maxOrderNotional == null) {
            maxOrderNotional = new BigDecimal("100000");
        }
        if (priceBandPercent == null) {
            priceBandPercent = new BigDecimal("10");
        }
        if (maxOrderNotional.signum() <= 0 || priceBandPercent.signum() <= 0) {
            throw new IllegalStateException("app.risk limits must be positive");
        }
    }
}
