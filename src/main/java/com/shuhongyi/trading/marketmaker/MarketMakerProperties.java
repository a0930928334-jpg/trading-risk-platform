package com.shuhongyi.trading.marketmaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Settings for the simulated liquidity provider that only runs under the {@code dev} profile.
 *
 * @param email               login identity of the system user (it cannot log in)
 * @param initialCash         cash deposited when the account is first created
 * @param sharesPerInstrument shares of each instrument granted when the account is first created
 * @param quoteLevels         price levels quoted on each side of each instrument
 * @param quoteStepPercent    distance between levels, in percent of the mark price
 * @param quoteQuantity       shares quoted at each level
 */
@ConfigurationProperties(prefix = "app.market-maker")
public record MarketMakerProperties(
        String email,
        BigDecimal initialCash,
        long sharesPerInstrument,
        int quoteLevels,
        BigDecimal quoteStepPercent,
        long quoteQuantity
) {

    public MarketMakerProperties {
        if (email == null || email.isBlank()) {
            email = "market-maker@system.local";
        }
        if (initialCash == null) {
            initialCash = new BigDecimal("10000000.0000");
        }
        if (sharesPerInstrument <= 0) {
            sharesPerInstrument = 100_000;
        }
        if (quoteLevels <= 0) {
            quoteLevels = 5;
        }
        if (quoteStepPercent == null) {
            quoteStepPercent = new BigDecimal("0.5");
        }
        if (quoteQuantity <= 0) {
            quoteQuantity = 100;
        }
    }
}
