package com.shuhongyi.trading.risk.rules;

import com.shuhongyi.trading.order.OrderSide;
import com.shuhongyi.trading.risk.RiskDecision;
import org.junit.jupiter.api.Test;

import static com.shuhongyi.trading.risk.RiskContexts.order;
import static org.assertj.core.api.Assertions.assertThat;

class AvailablePositionRuleTest {

    private final AvailablePositionRule rule = new AvailablePositionRule();

    @Test
    void acceptsSellCoveredByAvailableShares() {
        assertThat(rule.evaluate(order().side(OrderSide.SELL).position(100, 40).quantity(60).build())
                .accepted()).isTrue();
    }

    @Test
    void reservedSharesAreNotAvailable() {
        RiskDecision decision = rule.evaluate(order().side(OrderSide.SELL).position(100, 40).quantity(61).build());

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.code()).isEqualTo(AvailablePositionRule.CODE);
        assertThat(decision.reason()).isEqualTo("Required 61 shares, available 60");
    }

    @Test
    void sellingWithoutAnyPositionIsRejectedSoNoShortSelling() {
        assertThat(rule.evaluate(order().side(OrderSide.SELL).noPosition().quantity(1).build()).rejected())
                .isTrue();
    }

    @Test
    void ignoresBuyOrders() {
        assertThat(rule.evaluate(order().side(OrderSide.BUY).noPosition().build()).accepted()).isTrue();
    }
}
