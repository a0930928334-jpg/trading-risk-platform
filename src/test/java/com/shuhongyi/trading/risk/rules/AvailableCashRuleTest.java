package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.risk.RiskDecision;
import org.junit.jupiter.api.Test;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class AvailableCashRuleTest {

    private final AvailableCashRule rule = new AvailableCashRule();

    @Test
    void acceptsBuyFullyCoveredByAvailableCash() {
        assertThat(rule.evaluate(order().cash("1000.0000", "0").price("100.0000").quantity(10).build())
                .accepted()).isTrue();
    }

    @Test
    void reservedCashIsNotAvailable() {
        RiskDecision decision = rule.evaluate(order()
                .cash("1000.0000", "500.0000")
                .price("100.0000")
                .quantity(6)
                .build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(AvailableCashRule.CODE);
        assertThat(decision.reason()).isEqualTo("Required 600.0000, available 500.0000");
    }

    @Test
    void ignoresSellOrders() {
        assertThat(rule.evaluate(order().side(OrderSide.SELL).cash("0", "0").build()).accepted()).isTrue();
    }
}
