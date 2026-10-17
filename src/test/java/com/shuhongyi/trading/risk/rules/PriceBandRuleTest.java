package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.risk.RiskDecision;
import com.shuhongyi.trading.risk.RiskProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class PriceBandRuleTest {

    private final PriceBandRule rule = new PriceBandRule(new RiskProperties(null, null));

    @ParameterizedTest
    @ValueSource(strings = {"90.0000", "95.5000", "100.0000", "110.0000"})
    void acceptsPricesWithinTenPercentOfReference(String price) {
        assertThat(rule.evaluate(order().referencePrice("100.0000").price(price).build()).accepted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"89.9999", "110.0001", "150.0000", "0.0100"})
    void rejectsPricesOutsideTheBand(String price) {
        RiskDecision decision = rule.evaluate(order().referencePrice("100.0000").price(price).build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(PriceBandRule.CODE);
    }

    @Test
    void usesLastTradePriceInsteadOfReferenceOnceTheInstrumentHasTraded() {
        // 120 is 20% above the reference but within 10% of the last trade at 115.
        RiskDecision decision = rule.evaluate(order()
                .referencePrice("100.0000")
                .lastTradePrice("115.0000")
                .price("120.0000")
                .build());

        assertThat(decision.accepted()).isTrue();
    }

    @Test
    void rejectionReasonNamesTheMarkPrice() {
        RiskDecision decision = rule.evaluate(order().lastTradePrice("50.0000").price("60.0000").build());

        assertThat(decision.reason()).contains("50.0000").contains("10%");
    }

    @Test
    void bandIsConfigurable() {
        PriceBandRule wide = new PriceBandRule(new RiskProperties(null, new BigDecimal("25")));

        assertThat(wide.evaluate(order().referencePrice("100.0000").price("124.0000").build()).accepted()).isTrue();
        assertThat(wide.evaluate(order().referencePrice("100.0000").price("126.0000").build()).rejected()).isTrue();
    }
}
