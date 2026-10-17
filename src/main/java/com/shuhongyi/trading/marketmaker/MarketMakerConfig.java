package com.shuhongyi.trading.marketmaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the simulated market maker. The startup runner exists only under the {@code dev} profile,
 * so no test depends on market maker liquidity.
 */
@Configuration
@EnableConfigurationProperties(MarketMakerProperties.class)
public class MarketMakerConfig {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerConfig.class);

    @Bean
    @Profile("dev")
    public ApplicationRunner marketMakerRunner(MarketMakerAccountSeeder seeder, MarketMakerQuoter quoter) {
        return args -> {
            var account = seeder.seed();
            int placed = quoter.quote(account);
            log.info("Dev market maker ready: account {}, {} new quotes placed", account.getAccountNumber(), placed);
        };
    }
}
