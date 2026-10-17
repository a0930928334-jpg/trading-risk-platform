package com.shuhongyi.trading.marketmaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Settings for the simulated liquidity provider that only runs under the {@code dev} profile.
 *
 * @param email               login identity of the system user (it cannot log in)
 * @param initialCash         cash deposited when the account is first created
 * @param sharesPerInstrument shares of each instrument granted when the account is first created
 */
@ConfigurationProperties(prefix = "app.market-maker")
public record MarketMakerProperties(
        String email,
        BigDecimal initialCash,
        long sharesPerInstrument
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
    }
}
